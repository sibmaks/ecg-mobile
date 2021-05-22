package xyz.dma.ecgmobile.ui.menu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import org.greenrobot.eventbus.EventBus
import xyz.dma.ecgmobile.R
import xyz.dma.ecgmobile.event.command.ChangeChannelCommand


class ChannelMenuFragment : Fragment() {

    private lateinit var channelMenuViewModel: ChannelMenuViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        channelMenuViewModel = ViewModelProvider(this).get(ChannelMenuViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_channel_nav_menu, container, false)
        root.findViewById<BottomNavigationItemView>(R.id.channel_down).setOnClickListener { onChangeChannel(false) }
        root.findViewById<BottomNavigationItemView>(R.id.play).setOnClickListener { onPlay() }
        root.findViewById<BottomNavigationItemView>(R.id.channel_up).setOnClickListener { onChangeChannel(true) }
        return root
    }

    private fun onChangeChannel(up: Boolean) {
        EventBus.getDefault().post(ChangeChannelCommand(up))
    }

    private fun onPlay() {
        println("ON PLAY")
    }
}