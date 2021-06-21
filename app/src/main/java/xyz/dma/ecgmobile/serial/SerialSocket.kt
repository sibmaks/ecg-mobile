package xyz.dma.ecgmobile.serial

import android.app.PendingIntent
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java8.util.concurrent.CompletableFuture
import xyz.dma.ecgmobile.service.BoardResponseType
import xyz.dma.ecgmobile.service.START_CHAR
import xyz.dma.ecgmobile.service.StatisticService
import xyz.dma.ecgmobile.utils.SerialInputOutputManager
import java.nio.charset.StandardCharsets
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val TAG = "EM-SerialSocket"
private const val DRIVER_WRITE_TIMEOUT = 50
private val STUB_ARRAY = ByteArray(0)

class SerialSocket(
    private val usbManager: UsbManager,
    private val permissionIntent: PendingIntent,
    private val listener: Listener) : SerialInputOutputManager.Listener {

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

    interface Listener {
        fun onDataChanged()

        fun getDataBuffer() : ByteArray

        fun onDisconnect()
    }

    @Synchronized
    fun tryConnect() : Boolean {
        try {
            if (isConnected()) {
                Log.d(TAG, "Already connected")
                return false
            }
            val availableDrivers = usbSerialProber.findAllDrivers(usbManager)
            if (availableDrivers.isEmpty()) {
                Log.d(TAG, "There are no available driver")
                return false
            }

            val driver = availableDrivers[0]
            val connection = if (usbManager.hasPermission(driver.device)) {
                usbManager.openDevice(driver.device)
            } else {
                usbManager.requestPermission(driver.device, permissionIntent)
                Log.d(TAG, "Permission required")
                return false
            }

            usbSerialPort = driver.ports[0] // Most devices have just one port (port 0)

            reset()

            Log.d(TAG, "Open connection")
            usbSerialPort.open(connection)
            if(usbSerialPort.isOpen) {
                usbSerialPort.setParameters(
                    // https://stackoverflow.com/questions/44912175/is-it-possible-to-achieve-usb-baud-rate-bigger-than-115200-baud-on-android
                    1228800,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
                usbSerialPort.dtr = true
                usbSerialPort.rts = true
                Log.d(TAG, "Connection opened")

                serialInputOutputManager = SerialInputOutputManager(usbSerialPort, this)
                executionService.submit(serialInputOutputManager)
                Log.d(TAG, "Device connected")
                Log.d(TAG, "Device connected")
                return true
            } else {
                Log.e(TAG, "USB serial port not opened")
                return false
            }
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

        while (!Thread.interrupted()) {
            try {
                var byte = byteQueue.take()
                var char = byte.toInt().toChar()

                for (response in possibleResponses) {
                    if (response.index >= response.type.code.length || response.type.code[response.index++] != char) {
                        removingResponse.add(response)
                    } else if (response.index == response.type.code.length) {
                        if (currentType != null) {
                            onBoardResponse(currentType, buffer, response)
                        }
                        buffer.clear()
                        possibleResponses.clear()
                        removingResponse.clear()
                        currentType = response.type
                        if (currentType == BoardResponseType.DATA) {
                            val dataContent = listener.getDataBuffer()
                            for(i in dataContent.indices) {
                                dataContent[i] = byteQueue.take()
                            }
                            //Log.d(TAG, "New response: $currentType, ${String(responseContent, StandardCharsets.US_ASCII)}")
                            listener.onDataChanged()
                            currentType = null
                        } else if(!response.type.content) {
                            onBoardResponse(currentType, buffer, response)
                            currentType = null
                        }
                        byte = byteQueue.take()
                        char = byte.toInt().toChar()
                        break
                    }
                }

                possibleResponses.removeAll(removingResponse)
                removingResponse.clear()

                if (char == START_CHAR) {
                    val nextChar = byteQueue.peek()?.toInt()?.toChar()
                    for (response in BoardResponseType.values()) {
                        if (nextChar == null || response.code[1] == nextChar) {
                            possibleResponses.add(CandidateResponse(response, 1))
                        }
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

    private fun onBoardResponse(currentType: BoardResponseType, buffer: List<Byte>, candidateResponse: CandidateResponse) {
        val boardResponse: BoardResponse = if (buffer.size >= candidateResponse.index) {
            val responseContent = buffer.subList(0, buffer.size - candidateResponse.index + 1).toByteArray()
            Log.d(TAG, "New response: $currentType, ${String(responseContent, StandardCharsets.US_ASCII)}")
            BoardResponse(responseContent)
        } else {
            Log.d(TAG, "New response: $currentType")
            BoardResponse(null)
        }
        val futures = expectedResponses[currentType]
        if(futures != null) {
            val iFutures = ArrayList(futures)
            iFutures.forEach { future -> future.complete(boardResponse) }
            futures.removeAll(iFutures)
        }
    }

    override fun onNewData(data: ByteArray, len: Int) {
        StatisticService.tick("BAUD", len)
        for(i in 0 until len) {
            byteQueue.add(data[i])
        }
    }

    private fun reset() {
        Log.d(TAG, "Start settings reset")
        byteQueue.clear()
        expectedResponses.clear()
        Log.d(TAG, "Finished settings reset")
    }

    private fun send(text: String) {
        Log.d(TAG, "Send data: $text")
        usbSerialPort.write(text.toByteArray(), DRIVER_WRITE_TIMEOUT)
    }

    override fun onStop() {
        listener.onDisconnect()
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