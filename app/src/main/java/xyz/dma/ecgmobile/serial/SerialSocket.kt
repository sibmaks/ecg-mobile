package xyz.dma.ecgmobile.serial

import android.app.PendingIntent
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java8.util.concurrent.CompletableFuture
import xyz.dma.ecgmobile.service.BoardResponseType
import xyz.dma.ecgmobile.service.START_CHAR
import xyz.dma.ecgmobile.service.StatisticService
import java.nio.charset.StandardCharsets
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val TAG = "EM-SerialSocket"
private const val DRIVER_WRITE_TIMEOUT = 50
private val STUB_ARRAY = ByteArray(0)

class SerialSocket(private val usbManager: UsbManager, private val permissionIntent: PendingIntent,
    private val dataListener: (ByteArray) -> Unit) : SerialInputOutputManager.Listener {
    private val byteQueue = LinkedBlockingQueue<Byte>()
    private val listCreationLock = ReentrantLock()
    private val expectedResponses = ConcurrentHashMap<BoardResponseType, MutableList<CompletableFuture<BoardResponse>>>()
    private val usbSerialProber: UsbSerialProber
    private lateinit var usbSerialPort: UsbSerialPort
    private var serialInputOutputManager: SerialInputOutputManager? = null
    private val executionService: ExecutorService = Executors.newFixedThreadPool(4)

    init {
        val probeTable = UsbSerialProber.getDefaultProbeTable()
        probeTable.addProduct(1155, 22336, CdcAcmSerialDriver::class.java)
        usbSerialProber = UsbSerialProber(probeTable)
        executionService.submit {
            receive()
        }
    }

    @Synchronized
    fun tryConnect() : Boolean {
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
                // https://stackoverflow.com/questions/44912175/is-it-possible-to-achieve-usb-baud-rate-bigger-than-115200-baud-on-android
                1228800,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            usbSerialPort.dtr = true
            usbSerialPort.rts = true

            reset()
            serialInputOutputManager = SerialInputOutputManager(usbSerialPort, this)
            executionService.submit(serialInputOutputManager)
            Log.d(TAG, "Device connected")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Try connect exception: ${e.message}", e)
            return false
        }
    }

    class CandidateResponse(val type: BoardResponseType, var index: Int)
    class BoardResponse(var data: ByteArray?)

    private fun receive() {
        val possibleResponses = ArrayList<CandidateResponse>()
        val removingResponse = ArrayList<CandidateResponse>()
        var currentType: BoardResponseType? = null
        val buffer = ArrayList<Byte>(512)

        while (!Thread.currentThread().isInterrupted) {
            try {
                val byte = byteQueue.take()
                val char = byte.toInt().toChar()
                var skip = false

                for (response in possibleResponses) {
                    if (response.index >= response.type.code.length || response.type.code[response.index++] != char) {
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
                            val futures = expectedResponses[currentType]
                            if(futures != null) {
                                val iFutures = ArrayList(futures)
                                iFutures.forEach { future -> future.complete(boardResponse) }
                                futures.removeAll(iFutures)
                            }
                        }
                        buffer.clear()
                        possibleResponses.clear()
                        removingResponse.clear()
                        currentType = response.type
                        if (currentType == BoardResponseType.DATA) {
                            val size = byteQueue.take().toUByte().toInt()
                            for (j in 0 until size) {
                                buffer.add(byteQueue.take())
                            }
                            val responseContent = buffer.toByteArray()
                            //Log.d(TAG, "New response: $currentType, ${String(responseContent, StandardCharsets.US_ASCII)}")
                            dataListener(responseContent)
                            currentType = null
                            buffer.clear()
                        }
                        skip = true
                        break
                    }
                }

                if (skip) {
                    continue
                }
                possibleResponses.removeAll(removingResponse)
                removingResponse.clear()

                if (char == START_CHAR) {
                    for (response in BoardResponseType.values()) {
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
            StatisticService.tick("BAUD", data.size)
            for (a in data) {
                byteQueue.add(a)
            }
        }
    }

    private fun reset() {
        byteQueue.clear()
        expectedResponses.clear()
    }

    private fun send(text: String) {
        Log.d(TAG, "Send data: $text")
        usbSerialPort.write(text.toByteArray(), DRIVER_WRITE_TIMEOUT)
    }

    override fun onRunError(e: Exception?) {
        if(e != null) {
            Log.e(TAG, "On run error: ${e.message}", e)
        }
        if(!isConnected()) {
            tryConnect()
        }
        Log.d(TAG, "Device state: ${serialInputOutputManager?.state}")
    }

    fun close() {
        if (isConnected()) {
            usbSerialPort.close()
        }
    }

    fun exchange(request: String, responseType: BoardResponseType, contentRequired: Boolean = true): ByteArray {
        var futures = expectedResponses[responseType]
        if(futures == null) {
            listCreationLock.withLock {
                futures = expectedResponses[responseType]
                if(futures == null) {
                    expectedResponses[responseType] = CopyOnWriteArrayList()
                    futures = expectedResponses[responseType]
                }
            }
        }
        val completableFuture = CompletableFuture<BoardResponse>()
        futures?.add(completableFuture)
        do {
            try {
                send("${request}\n")
                try {
                    val response = completableFuture.get(10, TimeUnit.MILLISECONDS)
                    if(response != null) {
                        val data = response.data
                        if(data != null) {
                            return data
                        } else if(!contentRequired) {
                            return STUB_ARRAY
                        }
                    }
                } catch (ignore: TimeoutException) {

                }
            } catch (e: Exception) {
                Log.e(TAG, "Exchange exception: ${e.message}", e)
            }
        } while (isConnected())
        throw RuntimeException("Disconnected")
    }

    fun isConnected(): Boolean {
        return serialInputOutputManager?.state == SerialInputOutputManager.State.RUNNING
    }
}