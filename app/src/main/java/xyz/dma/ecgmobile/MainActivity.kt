package xyz.dma.ecgmobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import xyz.dma.ecgmobile.entity.BoardInfo
import xyz.dma.ecgmobile.entity.DataType
import xyz.dma.ecgmobile.event.ChannelChangedEvent
import xyz.dma.ecgmobile.event.board.BoardConnectedEvent
import xyz.dma.ecgmobile.event.command.ChangeChannelCommand
import xyz.dma.ecgmobile.event.command.ClearDataCommand
import xyz.dma.ecgmobile.event.command.ShareDataCommand


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        super.onPause()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onShareCommand(shareDataCommand: ShareDataCommand) {
        println("Here we must share data")
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onChangeChannelCommand(changeChannelCommand: ChangeChannelCommand) {
        EventBus.getDefault().post(ChannelChangedEvent("I", ArrayList()))
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun onClearCommand(clearDataCommand: ClearDataCommand) {
        println("On clear data command")
        EventBus.getDefault().post(BoardConnectedEvent(BoardInfo("ADS1293", "0.0.0", 3, DataType.INT32, Pair(-2000000f, 2000000f))))
    }
}