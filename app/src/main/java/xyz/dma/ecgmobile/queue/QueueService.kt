package xyz.dma.ecgmobile.queue

import android.util.Log
import java.util.concurrent.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object QueueService {
    private val creationLock: Lock = ReentrantLock()
    private val subscriptionLock: Lock = ReentrantLock()
    // queue name -> function
    private val subscribers = ConcurrentHashMap<String, (Any) -> Unit>()
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
                    val subscriber = subscribers[queue]
                    if(subscriber != null) {
                        val event = blockingQueue.poll(100, TimeUnit.MILLISECONDS)
                        if(event != null) {
                            subscriber(event)
                        }
                    }
                } catch (ignore: InterruptedException) {

                } catch (e: Exception) {
                    Log.e("EM-QueueService", e.message, e)
                }
            }
        } finally {
            executionService.execute{ consumeEvent(queue) }
        }
    }

    fun subscribe(queue: String, consumer: (Any) -> Unit) {
        var subscriber = subscribers[queue]
        if(subscriber == null) {
            subscriptionLock.withLock {
                subscriber = subscribers[queue]
                if(subscriber == null) {
                    subscribers[queue] = consumer
                    executionService.submit{ consumeEvent(queue) }
                }
            }
        }
    }
}