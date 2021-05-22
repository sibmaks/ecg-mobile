package xyz.dma.ecgmobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import xyz.dma.ecgmobile.event.command.ClearDataCommand
import xyz.dma.ecgmobile.event.command.ShareDataCommand


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<BottomNavigationView>(R.id.bottom_nav_view).selectedItemId = R.id.play
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
    fun onClearCommand(clearDataCommand: ClearDataCommand) {
        println("On clear data command")
    }
}