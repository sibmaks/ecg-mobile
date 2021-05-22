package xyz.dma.ecgmobile.collector

import com.github.mikephil.charting.data.Entry
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import xyz.dma.ecgmobile.entity.ChannelData
import xyz.dma.ecgmobile.event.ChannelChangedEvent
import xyz.dma.ecgmobile.event.board.BoardConnectedEvent
import xyz.dma.ecgmobile.event.command.ChangeChannelCommand
import xyz.dma.ecgmobile.event.command.PlayCommand
import xyz.dma.ecgmobile.queue.QueueService
import xyz.dma.ecgmobile.utils.NumberConverter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sin
import kotlin.random.Random

class ChannelDataCollector(private val maxValue: Int) {
    private val collectedData = ConcurrentHashMap<Int, List<Entry>>()
    private var index = 0
    private var channels = 0
    private var activeChannel = 0
    var collectData = false

    init {
        QueueService.subscribe("data-collector") { onData(it) }
    }

    private fun onData(data: Any) {
        if (data is ChannelData) {
            if(data.data.size == collectedData.size) {
                for (channel in data.data.indices) {
                    val list = collectedData[channel]
                    if(list != null) {
                        list[index].y = data.data[channel]
                    }
                }
                if(index + 1 >= maxValue) {
                    index = 0
                } else {
                    index++
                }
            }
        }
    }

    fun onResume() {
        EventBus.getDefault().register(this)
    }

    fun onPause() {
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onPlayCommand(command: PlayCommand) {
        collectData = command.play
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onBoardConnected(event: BoardConnectedEvent) {
        val random = Random(System.currentTimeMillis())
        if(event.boardInfo.channels > collectedData.size) {
            for(i in (collectedData.size + 1)..event.boardInfo.channels) {
                val list = ArrayList<Entry>()
                for(j in 0 until maxValue) {
                    //list.add(Entry(j.toFloat(), 0f))
                    //list.add(Entry(j.toFloat(), random.nextFloat() * 1000000))
                    list.add(Entry(j.toFloat(), sin(Math.toRadians(j.toDouble() * i)).toFloat() * 100000))
                }
                collectedData[i] = list
            }
        }
        channels = event.boardInfo.channels
        activeChannel = 1
        val list = collectedData[activeChannel]
        if(list != null) {
            val channelName = NumberConverter.arabicToRoman(activeChannel)
            EventBus.getDefault().post(ChannelChangedEvent(channelName, list))
        }
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onChangeChannelCommand(command: ChangeChannelCommand) {
        var channel = activeChannel + (if (command.up) 1 else -1)
        if (channel > channels) {
            channel = 1
        } else if (channel < 1) {
            channel = channels
        }
        val list = collectedData[channel]
        if(list != null) {
            activeChannel = channel
            val channelName = NumberConverter.arabicToRoman(activeChannel)
            EventBus.getDefault().post(ChannelChangedEvent(channelName, list))
        }
    }
}