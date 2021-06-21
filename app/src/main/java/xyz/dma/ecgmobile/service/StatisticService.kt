package xyz.dma.ecgmobile.service

import org.greenrobot.eventbus.EventBus
import xyz.dma.ecgmobile.event.statistic.StatisticCalculatedEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.roundToLong

const val SECONDS = 1

object StatisticService {
    private val executorService = Executors.newSingleThreadExecutor()
    private val trackedMeasurements = ConcurrentHashMap<String, AtomicLong>()
    private val creationLock = ReentrantLock()

    init {
        executorService.submit {
            var lastTime = System.currentTimeMillis()
            val statisticInterval = SECONDS * 1000f
            val halfInterval = (statisticInterval / 2f).roundToLong()
            while (!Thread.interrupted()) {
                val currentTime = System.currentTimeMillis()
                val interval = currentTime - lastTime
                if (interval >= statisticInterval) {
                    val x = interval / statisticInterval / SECONDS
                    for (entry in trackedMeasurements.entries) {
                        val ticks = entry.value.get()
                        entry.value.addAndGet(-ticks)
                        EventBus.getDefault().post(StatisticCalculatedEvent(entry.key, (ticks * x).toInt()))
                    }
                    lastTime = currentTime
                } else if (interval <= halfInterval) {
                    TimeUnit.MILLISECONDS.sleep(halfInterval)
                }
            }
        }
    }

    fun tick(trackedName: String, tickCount: Int) {
        var ticks = trackedMeasurements[trackedName]
        if(ticks == null) {
            creationLock.withLock {
                ticks = trackedMeasurements[trackedName]
                if(ticks == null) {
                    trackedMeasurements[trackedName] = AtomicLong(0)
                    ticks = trackedMeasurements[trackedName]
                }
            }
        }
        ticks?.addAndGet(tickCount.toLong())
    }

    fun tick(trackedName: String) {
        tick(trackedName, 1)
    }

}