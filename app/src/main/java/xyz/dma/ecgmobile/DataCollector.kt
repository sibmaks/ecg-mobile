package xyz.dma.ecgmobile

import android.util.Log
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import xyz.dma.ecgmobile.entity.BoardInfo
import xyz.dma.ecgmobile.entity.ChannelData
import xyz.dma.ecgmobile.event.board.BoardConnectedEvent
import xyz.dma.ecgmobile.event.board.BoardDisconnectedEvent
import xyz.dma.ecgmobile.event.command.ClearDataCommand
import xyz.dma.ecgmobile.event.command.RecordCommand
import xyz.dma.ecgmobile.event.command.ShareDataCommand
import xyz.dma.ecgmobile.queue.QueueService
import xyz.dma.ecgmobile.utils.FileUtils
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DataCollector(private val parentFile: File) {
    private val recordsPath = File(parentFile, "records")
    // board name -> collected data
    private val activeFilesLock = ReentrantLock()
    private val collectedData = ConcurrentHashMap<BoardInfo, List<File>>()
    private val writers = ConcurrentHashMap<File, BufferedWriter>()
    private var activeBoard: BoardInfo? = null
    var collectData = false

    init {
        if(!recordsPath.exists()) {
            recordsPath.mkdirs()
        }
        QueueService.subscribe("data-collector") { onData(it) }
    }

    private fun onData(data: Any) {
        val activeBoard = this.activeBoard
        if(data is ChannelData && activeBoard != null) {
            activeFilesLock.withLock {
                val list = collectedData[activeBoard]
                if (list != null && collectData) {
                    for (channel in list.indices) {
                        try {
                            val bufferedWriter = writers[list[channel]]
                            if (bufferedWriter != null) {
                                bufferedWriter.write(data.data[channel].toString())
                                bufferedWriter.newLine()
                                bufferedWriter.flush()
                            }
                        } catch (e: Exception) {
                            Log.e("EM-DataCollector", e.message, e)
                        }
                    }
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
    fun onClearCommand(command: ClearDataCommand) {
        val wasCollectData = collectData
        collectData = false

        activeFilesLock.withLock {
            closeWriters()
            removeFiles()
            val activeBoard = this.activeBoard
            if (activeBoard != null) {
                createFiles(activeBoard)
            }
        }

        collectData = wasCollectData
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onShareCommand(command: ShareDataCommand) {
        if(command.dataReady) {
            return
        }
        val recordFile = File(parentFile, "data-${System.currentTimeMillis()}.zip")
        val files = ArrayList<File>()
        collectedData.values.forEach {
            it.forEach { file -> files.add(file) }
        }
        if(files.isEmpty()) {
            EventBus.getDefault().post(ShareDataCommand(true, null))
            return
        }
        FileUtils.zip(files, recordFile)
        EventBus.getDefault().post(ShareDataCommand(true, recordFile))
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onRecord(command: RecordCommand) {
        collectData = command.on
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onBoardConnected(event: BoardConnectedEvent) {
        createFiles(event.boardInfo)
        activeBoard = event.boardInfo
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onBoardDisconnected(event: BoardDisconnectedEvent) {
        closeWriters()
        activeBoard = null
    }

    private fun createFiles(boardInfo: BoardInfo) {
        val boardFiles = collectedData[boardInfo]
        if(boardFiles == null) {
            val list = ArrayList<File>()
            for (channel in 1..boardInfo.channels) {
                val file = File(
                    recordsPath,
                    "${boardInfo.name}-channel-${channel}-ecg-records-${System.currentTimeMillis()}.csv"
                )
                val bufferedWriter = BufferedWriter(OutputStreamWriter(file.outputStream()))
                bufferedWriter.write("Board: ${boardInfo.name}\n")
                bufferedWriter.write("Version: ${boardInfo.version}\n")
                bufferedWriter.write("Channels: ${boardInfo.channels}\n")
                bufferedWriter.write("Channel: $channel\n")
                bufferedWriter.flush()
                writers[file] = bufferedWriter
                list.add(file)
            }
            collectedData[boardInfo] = list
        }
    }

    private fun closeWriters() {
        writers.values.forEach{
            try {
                it.close()
            } catch (e: Exception) {
                Log.e("EM-DataCollector", e.message, e)
            }
        }
    }

    private fun removeFiles() {
        collectedData.forEach {
            it.value.forEach { file ->
                try {
                    file.delete()
                } catch (e: Exception) {
                    Log.e("EM-DataCollector", e.message, e)
                }
            }
        }
        collectedData.clear()
    }
}