package xyz.dma.ecgmobile.queue

import android.util.Log
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val TAG = "EM-QueueService"

object QueueService {
    private val creationLock: Lock = ReentrantLock()
    private val subscriptionLock: Lock = ReentrantLock()
    // queue name -> function
    private val subscribers = ConcurrentHashMap<String, MutableList<(Any) -> Unit>>()
    // queue name -> queue
    private val queues = ConcurrentHashMap<String, BlockingQueue<Any>>()
    private val executionService = Executors.newFixedThreadPool(4)

    fun dispatch(queue: String, event: Any) {
        var blockingQueue = queues[queue]
        if(blockingQueue == null) {
            creationLock.withLock {
                blockingQueue = queues[queue]
                if(blockingQueue == null) {
                    queues[queue] = LinkedBlockingQueue()
                    blockingQueue = queues[queue]
                }
            }
        }
        blockingQueue?.add(event)
    }

    private fun consumeEvent(queue: String) {
        val blockingQueue = queues[queue]
        try {
            if(blockingQueue != null) {
                try {
                    val subscribers = subscribers[queue]
                    if(subscribers != null && subscribers.isNotEmpty()) {
                        val event = blockingQueue.poll(100, TimeUnit.MILLISECONDS)
                        if(event != null) {
                            for(it in subscribers) {
                                try {
                                    it(event)
                                } catch (e: Exception) {
                                    Log.e(TAG, e.message, e)
                                }
                            }
                        }
                    }
                } catch (ignore: InterruptedException) {

                } catch (e: Exception) {
                    Log.e(TAG, e.message, e)
                }
            }
        } finally {
            executionService.execute{ consumeEvent(queue) }
        }
    }

    fun subscribe(queue: String, consumer: (Any) -> Unit) {
        var queueSubscribers = subscribers[queue]
        if(queueSubscribers == null) {
            subscriptionLock.withLock {
                queueSubscribers = subscribers[queue]
                if(queueSubscribers == null) {
                    subscribers[queue] = Collections.synchronizedList(ArrayList())
                    executionService.submit{ consumeEvent(queue) }
                }
            }
        }
        subscribers[queue]?.add(consumer)
    }
}