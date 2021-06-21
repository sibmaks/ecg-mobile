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
import xyz.dma.ecgmobile.event.PlayChangedEvent
import xyz.dma.ecgmobile.event.board.BoardConnectedEvent
import xyz.dma.ecgmobile.event.board.BoardDisconnectedEvent
import xyz.dma.ecgmobile.event.command.ChangeChannelCommand
import xyz.dma.ecgmobile.event.command.PlayCommand


class ChannelMenuFragment : Fragment() {

    private lateinit var channelMenuViewModel: ChannelMenuViewModel
    private lateinit var playButton: BottomNavigationItemView
    private var plaing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        EventBus.getDefault().register(this)
        channelMenuViewModel = ViewModelProvider(this).get(ChannelMenuViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_channel_nav_menu, container, false)
        playButton = root.findViewById(R.id.play)

        root.findViewById<BottomNavigationItemView>(R.id.channel_down).setOnClickListener { onChangeChannel(false) }
        playButton.setOnClickListener { onPlay() }
        root.findViewById<BottomNavigationItemView>(R.id.channel_up).setOnClickListener { onChangeChannel(true) }
        return root
    }

    private fun onChangeChannel(up: Boolean) {
        EventBus.getDefault().post(ChangeChannelCommand(up))
    }

    private fun onPlay() {
        EventBus.getDefault().post(PlayCommand(!plaing))
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayEvent(event: PlayChangedEvent) {
        plaing = event.play
        updatePlayIcon()
    }

    @SuppressLint("RestrictedApi")
    private fun updatePlayIcon() {
        val iconId = if(plaing) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24
        val icon = resources.getDrawable(iconId, context?.theme)
        playButton.setIcon(icon)
    }

    @SuppressLint("RestrictedApi")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBoardConnected(event: BoardConnectedEvent) {
        playButton.isEnabled = true
    }

    @SuppressLint("RestrictedApi")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBoardDisconnected(event: BoardDisconnectedEvent) {
        plaing = false
        updatePlayIcon()
        playButton.isEnabled = false
    }
}