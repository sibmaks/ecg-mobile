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


class BoardService(context: Context) {

    private val serialSocket: SerialSocket
    private var channels = 0u
    private val executorService = Executors.newFixedThreadPool(2)

    init {
        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0)
        serialSocket = SerialSocket(context.getSystemService(Context.USB_SERVICE) as UsbManager, permissionIntent, this::onDate)

        val filter = IntentFilter(ACTION_USB_PERMISSION)

        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
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

    private fun onDate(bData: ByteArray) {
        val channels = channels
        val iChannels = channels.toInt()
        val channelsData = Array(iChannels) { 0f }
        if (channels > 0u) {
            val data = bData.asUByteArray()
            if (data.size >= 8 && data.size % 4 == 0) {
                var hash: UInt = channels
                for (j in 0 until iChannels) {
                    val channelData = getInt(4 * j, data)
                    hash = hash xor getUInt(4 * j, data)
                    channelsData[j] = channelData.toFloat()
                }
                if (hash == getUInt(data.size - 4, data)) {
                    StatisticService.tick("SPS")
                    QueueService.dispatch("data-collector", ChannelData(channelsData))
                } else {
                    Log.w(
                        TAG, "Invalid hash $hash vs ${getUInt(data.size - 4, data)}, data: '${
                            String(bData, StandardCharsets.US_ASCII)
                        }'"
                    )
                }
            } else {
                Log.w(TAG, "Invalid data:$bData")
            }
        }
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
            if (!serialSocket.isConnected() && serialSocket.tryConnect()) {
                onSocketConnected()
            }
        }
    }

    fun onResume() {
        EventBus.getDefault().register(this)
        tryConnect()
    }

    fun onPause() {
        EventBus.getDefault().unregister(this)
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
}