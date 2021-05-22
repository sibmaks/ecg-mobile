package xyz.dma.ecgmobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import xyz.dma.ecgmobile.entity.BoardInfo
import xyz.dma.ecgmobile.entity.ChannelData
import xyz.dma.ecgmobile.event.ChannelChangedEvent
import xyz.dma.ecgmobile.event.board.BoardConnectedEvent
import xyz.dma.ecgmobile.event.board.BoardDisconnectedEvent
import xyz.dma.ecgmobile.event.command.ChangeChannelCommand
import xyz.dma.ecgmobile.event.command.ShareDataCommand
import xyz.dma.ecgmobile.queue.QueueService
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    private lateinit var dataCollector: DataCollector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dataCollector = DataCollector(filesDir)
    }

    override fun onResume() {
        super.onResume()
        EventBus.getDefault().register(this)
        dataCollector.onResume()
    }

    override fun onPause() {
        super.onPause()
        EventBus.getDefault().unregister(this)
        dataCollector.onPause()
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onShareCommand(command: ShareDataCommand) {
        if(!command.dataReady || command.data == null) {
            if(command.dataReady) {
                runOnUiThread {
                    Toast.makeText(applicationContext, resources.getText(R.string.nothing_to_share), Toast.LENGTH_LONG)
                        .show()
                }
            }
            return
        }

        val intentShareFile = Intent(Intent.ACTION_SEND)
        intentShareFile.type = "application/zip"
        intentShareFile.putExtra(
            Intent.EXTRA_STREAM,
            FileProvider.getUriForFile(this@MainActivity, "xyz.dma.ecgmobile.FILE_PROVIDER", command.data)
        )
        intentShareFile.putExtra(Intent.EXTRA_TEXT, getString(R.string.title_share))

        startActivity(Intent.createChooser(intentShareFile, getString(R.string.caption_save_data)))
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onChangeChannelCommand(changeChannelCommand: ChangeChannelCommand) {
        if(changeChannelCommand.up) {
            EventBus.getDefault().post(ChannelChangedEvent("II", ArrayList()))
            EventBus.getDefault().post(BoardConnectedEvent(BoardInfo("ADS1293", "0.0.0", 3, Pair(-2000000f, 2000000f))))
        } else {
            QueueService.dispatch("data-collector", ChannelData(listOf(1f, 2f, 3f)))
            QueueService.dispatch("data-collector", ChannelData(listOf(21f, 22f, 23f)))
            QueueService.dispatch("data-collector", ChannelData(listOf(31f, 32f, 33f)))
            TimeUnit.SECONDS.sleep(1)
            EventBus.getDefault().post(ChannelChangedEvent("I", ArrayList()))
            EventBus.getDefault().post(BoardDisconnectedEvent())
        }
    }
}