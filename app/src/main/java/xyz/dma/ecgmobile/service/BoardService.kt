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
import xyz.dma.ecgmobile.R
import xyz.dma.ecgmobile.entity.BoardInfo
import xyz.dma.ecgmobile.entity.ChannelData
import xyz.dma.ecgmobile.event.AlertTriggeredEvent
import xyz.dma.ecgmobile.event.SpsCalculatedEvent
import xyz.dma.ecgmobile.event.board.BoardConnectedEvent
import xyz.dma.ecgmobile.event.board.BoardDisconnectedEvent
import xyz.dma.ecgmobile.event.command.PlayCommand
import xyz.dma.ecgmobile.queue.QueueService
import xyz.dma.ecgmobile.serial.SerialSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.roundToLong

private const val ACTION_USB_PERMISSION = "xyz.dma.ecgmobile.USB_PERMISSION"
private const val TAG = "EM-BoardService"


class BoardService(context: Context) {

    private val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0)
    private val serialSocket = SerialSocket(context.getSystemService(Context.USB_SERVICE) as UsbManager, permissionIntent)
    private var connectedBoard: BoardInfo? = null
    private var connectionLock = ReentrantLock()
    private var dataLock = ReentrantLock()
    private var values = AtomicInteger(0)
    private var paused = true
    private val executorService = Executors.newFixedThreadPool(2)

    init {
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

        executorService.submit {
            var time = System.currentTimeMillis()
            val interval = 1000f
            val halfInterval = (interval / 2f).roundToLong()
            while (!Thread.interrupted()) {
                val now = System.currentTimeMillis()
                if (now - time >= interval) {
                    val sps = values.get()
                    values.addAndGet(-sps)
                    if(connectedBoard != null) {
                        EventBus.getDefault().post(SpsCalculatedEvent(sps))
                    }
                    time = now
                } else if(now - time <= halfInterval) {
                    TimeUnit.MILLISECONDS.sleep(halfInterval)
                }
            }
        }

        serialSocket.addListener(BoardResponseType.TOO_SLOW_ERROR) {
            EventBus.getDefault().post(AlertTriggeredEvent(R.string.error_too_slow))
        }

        /*serialSocket.addListener(BoardResponseType.DATA) {
            val channels = connectedBoard?.channels
            val data = it.data?.asUByteArray()
            if (channels != null && data != null && data.size >= 8 && data.size % 4 == 0) {
                dataLock.withLock {
                    val channelsData = ArrayList<Float>()
                    for (j in 0 until channels.toInt()) {
                        val channelData = getInt(4 * j, data)
                        channelsData.add(channelData.toFloat())
                    }
                    values.incrementAndGet()
                    QueueService.dispatch("data-collector", ChannelData(channelsData))
                }
            } else {
                Log.w(TAG, "Invalid data, $channels, $data, ${data?.size}")
            }
        }*/

        serialSocket.addListener(BoardResponseType.DATA) {
            val channels = connectedBoard?.channels
            val data = it.data?.asUByteArray()
            if (channels != null && data != null && data.size >= 8 && data.size % 4 == 0) {
                dataLock.withLock {
                    var hash : UInt = channels
                    val channelsData = ArrayList<Float>()
                    for (j in 0 until channels.toInt()) {
                        val channelData = getInt(4 * j, data)
                        hash = hash xor getUInt(4 * j, data)
                        channelsData.add(channelData.toFloat())
                    }
                    if (hash == getUInt(data.size - 4, data)) {
                        values.incrementAndGet()
                        if(!paused) {
                            QueueService.dispatch("data-collector", ChannelData(channelsData))
                        } else {

                        }
                    } else {
                        Log.w(TAG, "Invalid hash $hash vs ${getUInt(data.size - 4, data)}, data: '${
                            String(data.asByteArray(), StandardCharsets.US_ASCII)
                        }'")
                    }
                }
            } else {
                Log.w(TAG, "Invalid data, $channels, $data, ${data?.size}")
            }
        }

        tryConnect()
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
            val channels = String(serialSocket.exchange("GET_PARAMETER\nCHANNELS_COUNT", BoardResponseType.CHANNELS_COUNT), StandardCharsets.US_ASCII).toUInt()
            Log.d(TAG, "Channels: $channels")
            val version = String(serialSocket.exchange("GET_PARAMETER\nVERSION", BoardResponseType.VERSION), StandardCharsets.US_ASCII)
            Log.d(TAG, "Version: $version")
            val minValue = String(serialSocket.exchange("GET_PARAMETER\nMIN_VALUE", BoardResponseType.MIN_VALUE), StandardCharsets.US_ASCII).toFloat()
            Log.d(TAG, "Min: $minValue")
            val maxValue = String(serialSocket.exchange("GET_PARAMETER\nMAX_VALUE", BoardResponseType.MAX_VALUE), StandardCharsets.US_ASCII).toFloat()
            Log.d(TAG, "Max: $maxValue")

            val boardInfo = BoardInfo(model, version, channels, Pair(minValue, maxValue))
            EventBus.getDefault().post(BoardConnectedEvent(boardInfo))
            connectedBoard = boardInfo
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
        }
    }

    private fun tryConnect() {
        executorService.submit {
            connectionLock.withLock {
                if (!serialSocket.isConnected() && serialSocket.tryConnect()) {
                    onSocketConnected()
                }
            }
        }
    }

    fun onStart() {
        EventBus.getDefault().register(this)
        tryConnect()
    }

    fun onStop() {
        EventBus.getDefault().unregister(this)
        closeConnection()
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onPlayCommand(command: PlayCommand) {
        paused = !command.play
        if(command.play) {
            serialSocket.exchange("ON_DF", BoardResponseType.DATA_FLOW_ON)
        } else {
            serialSocket.exchange("OFF_DF", BoardResponseType.DATA_FLOW_OFF)
        }
    }

    private fun closeConnection() {
        try {
            serialSocket.close()
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
        } finally {
            val connectedBoard = connectedBoard
            if(connectedBoard != null) {
                EventBus.getDefault().post(BoardDisconnectedEvent())
            }
            this.connectedBoard = null
        }
    }
}