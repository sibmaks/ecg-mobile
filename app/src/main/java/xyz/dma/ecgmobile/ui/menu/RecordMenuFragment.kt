package xyz.dma.ecgmobile.ui.menu

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import xyz.dma.ecgmobile.R
import xyz.dma.ecgmobile.event.board.BoardConnectedEvent
import xyz.dma.ecgmobile.event.board.BoardDisconnectedEvent
import xyz.dma.ecgmobile.event.command.ClearDataCommand
import xyz.dma.ecgmobile.event.command.RecordCommand
import xyz.dma.ecgmobile.event.command.ShareDataCommand


class RecordMenuFragment : Fragment() {

    private lateinit var recordMenuViewModel: RecordMenuViewModel
    private lateinit var recordButton: BottomNavigationItemView
    private var recording = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        recordMenuViewModel = ViewModelProvider(this).get(RecordMenuViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_record_nav_menu, container, false)
        recordButton = root.findViewById(R.id.record)

        root.findViewById<BottomNavigationItemView>(R.id.clear).setOnClickListener { onClear() }
        recordButton.setOnClickListener { onRecord() }
        root.findViewById<BottomNavigationItemView>(R.id.share).setOnClickListener { onShare() }
        return root
    }

    private fun onClear() {
        EventBus.getDefault().post(ClearDataCommand())
    }

    private fun onRecord() {
        recording = !recording
        updateRecordIcon()
        EventBus.getDefault().post(RecordCommand(recording))
    }

    @SuppressLint("RestrictedApi")
    private fun updateRecordIcon() {
        val iconId = if(recording) R.drawable.baseline_radio_button_checked_24 else R.drawable.baseline_radio_button_unchecked_24
        val icon = resources.getDrawable(iconId, context?.theme)
        recordButton.setIcon(icon)
    }

    private fun onShare() {
        EventBus.getDefault().post(ShareDataCommand())
    }

    override fun onResume() {
        super.onResume()
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        super.onPause()
        EventBus.getDefault().unregister(this)
    }

    @SuppressLint("RestrictedApi")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBoardConnected(event: BoardConnectedEvent) {
        recordButton.isEnabled = true
    }

    @SuppressLint("RestrictedApi")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBoardDisconnected(event: BoardDisconnectedEvent) {
        recording = false
        updateRecordIcon()
        recordButton.isEnabled = false
    }
}