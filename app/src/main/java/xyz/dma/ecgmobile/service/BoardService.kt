package xyz.dma.ecgmobile.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.util.Log
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import xyz.dma.ecgmobile.entity.BoardInfo
import xyz.dma.ecgmobile.entity.ChannelData
import xyz.dma.ecgmobile.event.PlayChangedEvent
import xyz.dma.ecgmobile.event.board.BoardConnectedEvent
import xyz.dma.ecgmobile.event.board.BoardDisconnectedEvent
import xyz.dma.ecgmobile.event.command.PlayCommand
import xyz.dma.ecgmobile.queue.QueueService
import xyz.dma.ecgmobile.serial.SerialSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

private const val ACTION_USB_PERMISSION = "xyz.dma.ecgmobile.USB_PERMISSION"
private const val TAG = "EM-BoardService"


class BoardService(context: Context) : SerialSocket.Listener {

    private val serialSocket: SerialSocket
    private var channels = 0u
    private var dataBuffer = ByteArray(0)
    private val executorService = Executors.newFixedThreadPool(2)

    init {
        EventBus.getDefault().register(this)

        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0)
        serialSocket = SerialSocket(context.getSystemService(Context.USB_SERVICE) as UsbManager, permissionIntent,
            this)

        val filter = IntentFilter(ACTION_USB_PERMISSION)

        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Received action: ${intent?.action}")
                when (intent?.action) {
                    ACTION_USB_PERMISSION -> {
                        tryConnect()
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        executorService.submit {
                            closeConnection()
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        tryConnect()
                    }
                }
            }
        }, filter)
    }

    override fun onDataChanged() {
        val channels = channels.toInt()
        if (this.channels > 0u) {
            val data = dataBuffer.asUByteArray()
            if (data.size >= 8 && data.size % 4 == 0) {
                var hash = getUInt(0, data)
                val channelsData = Array(channels) {
                    if(it != 0) {
                        hash = hash xor getUInt(4 * it, data)
                    }
                    getInt(4 * it, data).toFloat()
                }
                if (hash == getUInt(data.size - 4, data)) {
                    StatisticService.tick("SPS")
                    QueueService.dispatch("data-collector", ChannelData(channelsData))
                } else {
                    Log.w(TAG, "Invalid hash, data: '${String(dataBuffer, StandardCharsets.US_ASCII)}'")
                }
            } else {
                Log.w(TAG, "Invalid data (${dataBuffer.size}): ${String(dataBuffer, StandardCharsets.US_ASCII)}")
            }
        }
    }

    override fun getDataBuffer(): ByteArray {
        return dataBuffer
    }

    private fun getInt(offset: Int, bytes: UByteArray): Int {
        return (bytes[offset + 3].toInt() shl 24) or (bytes[offset + 2].toInt() shl 16) or (
                bytes[offset + 1].toInt() shl 8) or bytes[offset].toInt()
    }

    private fun getUInt(offset: Int, bytes: UByteArray): UInt {
        return (bytes[offset + 3].toUInt() shl 24) or (bytes[offset + 2].toUInt() shl 16) or (
                bytes[offset + 1].toUInt() shl 8) or bytes[offset].toUInt()
    }

    private fun onSocketConnected() {
        try {
            val model = String(serialSocket.exchange("GET_PARAMETER\nMODEL", BoardResponseType.MODEL), StandardCharsets.US_ASCII)
            Log.d(TAG, "Model read: $model")
            channels = String(serialSocket.exchange("GET_PARAMETER\nCHANNELS_COUNT", BoardResponseType.CHANNELS_COUNT), StandardCharsets.US_ASCII).toUInt()
            dataBuffer = ByteArray(channels.toInt() * 4 + 4)
            Log.d(TAG, "Channels: $channels")
            val version = String(serialSocket.exchange("GET_PARAMETER\nVERSION", BoardResponseType.VERSION), StandardCharsets.US_ASCII)
            Log.d(TAG, "Version: $version")
            val minValue = String(serialSocket.exchange("GET_PARAMETER\nMIN_VALUE", BoardResponseType.MIN_VALUE), StandardCharsets.US_ASCII).toFloat()
            Log.d(TAG, "Min: $minValue")
            val maxValue = String(serialSocket.exchange("GET_PARAMETER\nMAX_VALUE", BoardResponseType.MAX_VALUE), StandardCharsets.US_ASCII).toFloat()
            Log.d(TAG, "Max: $maxValue")

            val boardInfo = BoardInfo(model, version, channels, Pair(minValue, maxValue))
            EventBus.getDefault().post(BoardConnectedEvent(boardInfo))
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
        }
    }

    private fun tryConnect() {
        executorService.submit {
            Log.d(TAG, "tryConnect invocation")
            try {
                if (serialSocket.tryConnect()) {
                    Log.d(TAG, "Board connected")
                    onSocketConnected()
                }
            } catch (e: Exception) {
                Log.w(TAG, "On connect exception", e)
            }
            try {
                if (serialSocket.tryConnect()) {
                    Log.d(TAG, "Board connected")
                    try {
                        onSocketConnected()
                    } catch (e: Exception) {
                        Log.w(TAG, "On socket connected exception", e)
                        if(serialSocket.isConnected()) {
                            closeConnection()
                        }
                        tryConnect()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "On connect exception", e)
                tryConnect()
            }
        }
    }

    fun onStop() {
        closeConnection()
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onPlayCommand(command: PlayCommand) {
        if(command.play) {
            serialSocket.exchange("ON_DF", BoardResponseType.DATA_FLOW_ON, false)
        } else {
            serialSocket.exchange("OFF_DF", BoardResponseType.DATA_FLOW_OFF, false)
        }
        EventBus.getDefault().post(PlayChangedEvent(command.play))
    }

    private fun closeConnection() {
        try {
            serialSocket.close()
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
        } finally {
            EventBus.getDefault().post(BoardDisconnectedEvent())
        }
    }

    override fun onDisconnect() {
        channels = 0u
        EventBus.getDefault().post(BoardDisconnectedEvent())
        tryConnect()
    }
}