package xyz.dma.ecgmobile.serial

import android.app.PendingIntent
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import xyz.dma.ecgmobile.service.BoardResponseType
import java.nio.charset.StandardCharsets
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val TAG = "EM-SerialSocket"

class SerialSocket(private val usbManager: UsbManager, private val permissionIntent: PendingIntent) : SerialInputOutputManager.Listener {
    private val connectionLock = ReentrantLock()
    private val driverWriteTimeout = 2000
    private val byteQueue = LinkedBlockingQueue<Byte>()
    private val responseQueue = ConcurrentHashMap<BoardResponseType, LinkedBlockingQueue<BoardResponse>>()
    private val responseListeners = ConcurrentHashMap<BoardResponseType, MutableList<(BoardResponse) -> Unit>>()
    private val usbSerialProber: UsbSerialProber
    private lateinit var usbSerialPort: UsbSerialPort
    private var serialInputOutputManager: SerialInputOutputManager? = null
    private val executionService: ExecutorService = Executors.newFixedThreadPool(4)
    private val possibleResponses = ArrayList<CandidateResponse>()
    private val removingResponse = ArrayList<CandidateResponse>()

    init {
        val probeTable = UsbSerialProber.getDefaultProbeTable()
        probeTable.addProduct(1155, 22336, CdcAcmSerialDriver::class.java)
        usbSerialProber = UsbSerialProber(probeTable)
        executionService.submit {
            receive()
        }
    }

    fun addListener(boardResponseType: BoardResponseType, listener: (BoardResponse) -> Unit) {
        synchronized(responseListeners) {
            if(responseListeners[boardResponseType] == null) {
                responseListeners[boardResponseType] = ArrayList()
            }
            responseListeners[boardResponseType]?.add(listener)
        }
    }

    fun tryConnect() : Boolean {
        connectionLock.withLock {
            try {
                if (isConnected()) {
                    return true
                }
                val availableDrivers = usbSerialProber.findAllDrivers(usbManager)
                if (availableDrivers.isEmpty()) {
                    return false
                }

                val driver = availableDrivers[0]
                val connection = if (usbManager.hasPermission(driver.device)) {
                    usbManager.openDevice(driver.device)
                } else {
                    usbManager.requestPermission(driver.device, permissionIntent)
                    return false
                }

                usbSerialPort = driver.ports[0] // Most devices have just one port (port 0)

                usbSerialPort.open(connection)
                usbSerialPort.setParameters(
                    460800,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
                usbSerialPort.dtr = true
                usbSerialPort.rts = true

                serialInputOutputManager = SerialInputOutputManager(usbSerialPort, this)
                executionService.submit(serialInputOutputManager)
                Log.d(TAG, "Device connected")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Try connect exception: ${e.message}", e)
                return false
            }
        }
    }

    class CandidateResponse(val type: BoardResponseType, var index: Int)
    class BoardResponse(var data: ByteArray?)

    private fun receive() {
        var currentType: BoardResponseType? = null
        val buffer = ArrayList<Byte>()

        while (!Thread.currentThread().isInterrupted) {
            try {
                val byte = byteQueue.take()
                val char = byte.toInt().toChar()
                var skip = false

                for (response in possibleResponses) {
                    if (response.index >= response.type.code.length) {
                        removingResponse.add(response)
                    } else if (response.type.code[response.index++] != char) {
                        removingResponse.add(response)
                    } else if (response.index == response.type.code.length) {
                        if (currentType != null) {
                            val boardResponse: BoardResponse = if (buffer.size >= response.index) {
                                val responseContent =
                                    buffer.subList(0, buffer.size - response.index + 1).toByteArray()
                                //Log.d(TAG, "New response: $currentType, ${String(responseContent, StandardCharsets.US_ASCII)}")
                                BoardResponse(responseContent)
                            } else {
                                //Log.d(TAG, "New response: $currentType")
                                BoardResponse(null)
                            }
                            responseQueue[currentType]?.add(boardResponse)
                            synchronized(responseListeners) {
                                val listeners = responseListeners[currentType]
                                if(listeners != null) {
                                    for (it in listeners) {
                                        executionService.submit {
                                            it(boardResponse)
                                        }
                                    }
                                }
                            }
                        }
                        buffer.clear()
                        possibleResponses.clear()
                        removingResponse.clear()
                        currentType = response.type
                        skip = true
                        break
                    }
                }

                if (skip) {
                    continue
                }
                possibleResponses.removeAll(removingResponse)
                removingResponse.clear()

                for (response in BoardResponseType.values()) {
                    if (response.code[0] == char) {
                        possibleResponses.add(CandidateResponse(response, 1))
                    }
                }
                buffer.add(byte)
                if (buffer.size >= 512) {
                    Log.w(TAG, "Too big buffer, clear: ${String(buffer.toByteArray(), StandardCharsets.US_ASCII)}")
                    buffer.clear()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Receive exception: ${e.message}", e)
            }
        }
    }

    override fun onNewData(data: ByteArray?) {
        if (data != null) {
            for (a in data) {
                byteQueue.add(a)
            }
        }
    }

    fun reset() {
        byteQueue.clear()
        responseQueue.clear()
        possibleResponses.clear()
        removingResponse.clear()
    }

    fun send(text: String) {
        usbSerialPort.write(text.toByteArray(), driverWriteTimeout)
    }

    override fun onRunError(e: Exception?) {
        if(e != null) {
            Log.e(TAG, "On run error: ${e.message}", e)
        }
        if(!isConnected()) {
            reset()
        }
    }

    fun close() {
        reset()
        if (isConnected()) {
            usbSerialPort.close()
        }
    }

    fun exchange(request: String, responseType: BoardResponseType): ByteArray {
        val nQueue = LinkedBlockingQueue<BoardResponse>()
        val queue = responseQueue.putIfAbsent(responseType, nQueue) ?: nQueue
        var response: BoardResponse? = null
        do {
            try {
                send("${request}\n")
                try {
                    response = queue.poll(100, TimeUnit.MILLISECONDS)
                } catch (ignore: InterruptedException) {

                }
            } catch (e: Exception) {
                Log.e(TAG, "Exchange exception: ${e.message}", e)
            }
        } while (response?.data == null && isConnected())
        if (response?.data == null) {
            throw RuntimeException("Disconnected")
        }
        return response.data!!
    }

    fun isConnected(): Boolean {
        return serialInputOutputManager?.state == SerialInputOutputManager.State.RUNNING
    }
}