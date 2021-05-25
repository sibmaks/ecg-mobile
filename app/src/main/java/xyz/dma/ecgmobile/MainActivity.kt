package xyz.dma.ecgmobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import xyz.dma.ecgmobile.collector.ChannelDataCollector
import xyz.dma.ecgmobile.collector.FileDataCollector
import xyz.dma.ecgmobile.event.ShareDataEvent
import xyz.dma.ecgmobile.service.BoardService


class MainActivity : AppCompatActivity() {
    private lateinit var fileDataCollector: FileDataCollector
    private lateinit var channelDataCollector: ChannelDataCollector
    private lateinit var boardService: BoardService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fileDataCollector = FileDataCollector(filesDir)
        channelDataCollector = ChannelDataCollector(resources.getInteger(R.integer.dots_counts))
        boardService = BoardService(this)
    }

    override fun onResume() {
        super.onResume()
        EventBus.getDefault().register(this)
        fileDataCollector.onResume()
        channelDataCollector.onResume()
        boardService.onStart()
    }

    override fun onPause() {
        super.onPause()
        EventBus.getDefault().unregister(this)
        fileDataCollector.onPause()
        channelDataCollector.onPause()
        boardService.onStop()
    }

    override fun onStop() {
        super.onStop()
        boardService.onStop()
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onShareCommand(event: ShareDataEvent) {
        if(event.file == null) {
            runOnUiThread {
                Toast.makeText(applicationContext, resources.getText(R.string.nothing_to_share), Toast.LENGTH_LONG)
                    .show()
            }
            return
        }

        val intentShareFile = Intent(Intent.ACTION_SEND)
        intentShareFile.type = "application/zip"
        intentShareFile.putExtra(
            Intent.EXTRA_STREAM,
            FileProvider.getUriForFile(this@MainActivity, "xyz.dma.ecgmobile.FILE_PROVIDER", event.file)
        )
        intentShareFile.putExtra(Intent.EXTRA_TEXT, getString(R.string.title_share))

        startActivity(Intent.createChooser(intentShareFile, getString(R.string.caption_save_data)))
    }
}