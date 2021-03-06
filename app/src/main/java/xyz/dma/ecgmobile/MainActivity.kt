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
import xyz.dma.ecgmobile.event.AlertTriggeredEvent
import xyz.dma.ecgmobile.event.ShareDataEvent
import xyz.dma.ecgmobile.service.BoardService


private const val TAG = "EM-MainActivity"

class MainActivity : AppCompatActivity() {
    private lateinit var fileDataCollector: FileDataCollector
    private lateinit var channelDataCollector: ChannelDataCollector
    private lateinit var boardService: BoardService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        EventBus.getDefault().register(this)

        fileDataCollector = FileDataCollector(filesDir)
        channelDataCollector = ChannelDataCollector(resources.getInteger(R.integer.dots_counts))
        boardService = BoardService(this)
    }

    override fun onStop() {
        super.onStop()
        boardService.onStop()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAlertEvent(event: AlertTriggeredEvent) {
        Toast.makeText(this, resources.getText(event.alertId), Toast.LENGTH_LONG).show()
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