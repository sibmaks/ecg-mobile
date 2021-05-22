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
import xyz.dma.ecgmobile.event.command.ClearDataCommand
import xyz.dma.ecgmobile.event.command.ShareDataCommand


class RecordMenuFragment : Fragment() {

    private lateinit var recordMenuViewModel: RecordMenuViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        recordMenuViewModel = ViewModelProvider(this).get(RecordMenuViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_record_nav_menu, container, false)
        root.findViewById<BottomNavigationItemView>(R.id.clear).setOnClickListener { onClear() }
        root.findViewById<BottomNavigationItemView>(R.id.record).setOnClickListener { onRecord() }
        root.findViewById<BottomNavigationItemView>(R.id.share).setOnClickListener { onShare() }
        return root
    }

    private fun onClear() {
        EventBus.getDefault().post(ClearDataCommand())
    }

    private fun onRecord() {
        println("ON RECORD")
    }

    private fun onShare() {
        EventBus.getDefault().post(ShareDataCommand())
    }
}