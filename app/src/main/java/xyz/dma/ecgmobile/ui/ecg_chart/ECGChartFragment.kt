package xyz.dma.ecgmobile.ui.ecg_chart

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import xyz.dma.ecgmobile.R
import xyz.dma.ecgmobile.event.BaudRateCalculatedEvent
import xyz.dma.ecgmobile.event.ChannelChangedEvent
import xyz.dma.ecgmobile.event.SpsCalculatedEvent
import xyz.dma.ecgmobile.event.board.BoardConnectedEvent
import xyz.dma.ecgmobile.event.board.BoardDisconnectedEvent
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong


class ECGChartFragment : Fragment() {

    private lateinit var eCGChartViewModel: ECGChartViewModel
    private lateinit var lineChart: LineChart
    private lateinit var lineDataSet: LineDataSet
    private lateinit var channelNameView: TextView
    private lateinit var boardNameView: TextView
    private lateinit var spsView: TextView
    private lateinit var baudRateView: TextView
    private val executionService = Executors.newFixedThreadPool(2)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        eCGChartViewModel = ViewModelProvider(this).get(ECGChartViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_ecg_chart, container, false)
        val dotsCount = resources.getInteger(R.integer.dots_counts)

        channelNameView = root.findViewById(R.id.channel_name)
        boardNameView = root.findViewById(R.id.board_name)
        spsView = root.findViewById(R.id.sps_text)
        baudRateView = root.findViewById(R.id.baud_rate_text)
        lineChart = root.findViewById(R.id.ecg_graph)

        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setDrawGridBackground(true)
        lineChart.setPinchZoom(false)
        lineChart.setBackgroundColor(Color.WHITE)
        lineChart.legend.isEnabled = false
        lineChart.description.isEnabled = false

        lineChart.xAxis.setDrawAxisLine(true)
        lineChart.xAxis.setDrawGridLines(true)
        lineChart.xAxis.axisMinimum = 0f
        lineChart.xAxis.axisMaximum = dotsCount.toFloat()

        lineChart.axisLeft.setDrawAxisLine(true)
        lineChart.axisLeft.setDrawGridLines(true)
        lineChart.axisLeft.granularity = 10f
        lineChart.axisLeft.isGranularityEnabled = true
        lineChart.axisLeft.setLabelCount(10, true)
        lineChart.axisLeft.axisMinimum = -1000f
        lineChart.axisLeft.axisMaximum = 1000f

        lineChart.axisRight.isEnabled = false

        val stubPoints = ArrayList<Entry>()

        for(i in 0..dotsCount) {
            stubPoints.add(Entry(i.toFloat(), 0f))
        }

        lineDataSet = LineDataSet(stubPoints, "DataSet")
        lineDataSet.axisDependency = YAxis.AxisDependency.LEFT
        lineDataSet.color = ColorTemplate.getHoloBlue()
        lineDataSet.valueTextColor = ColorTemplate.getHoloBlue()
        lineDataSet.lineWidth = 1.5f
        lineDataSet.setDrawCircles(false)
        lineDataSet.setDrawValues(false)
        lineDataSet.fillAlpha = 65
        lineDataSet.fillColor = ContextCompat.getColor(root.context, R.color.ecg_line_color)
        lineDataSet.isHighlightEnabled = false
        lineDataSet.setDrawCircleHole(false)

        val lineData = LineData(lineDataSet)
        lineData.setValueTextColor(Color.WHITE)
        lineData.setValueTextSize(9f)

        lineChart.data = lineData

        executionService.submit { refreshView() }

        return root
    }

    private fun refreshView() {
        var time = System.currentTimeMillis()
        val interval = 1000f / 60f
        val halfInterval = (interval / 2f).roundToLong()
        while (!Thread.interrupted()) {
            val now = System.currentTimeMillis()
            if (now - time >= interval) {
                lineChart.postInvalidate()
                time = now
            } else if(now - time <= halfInterval) {
                TimeUnit.MILLISECONDS.sleep(halfInterval)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onChannelChanged(event: ChannelChangedEvent) {
        if(event.channel == "") {
            channelNameView.visibility = View.INVISIBLE
        } else {
            channelNameView.text = resources.getString(R.string.title_channel).format(event.channel)
            channelNameView.visibility = View.VISIBLE
        }
        lineDataSet.values = event.data
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onChannelChanged(event: SpsCalculatedEvent) {
        spsView.text = resources.getString(R.string.title_sps_text).format(event.sps)
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onChannelChanged(event: BaudRateCalculatedEvent) {
        baudRateView.text = resources.getString(R.string.title_baud_rate_text).format(event.baudRate)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBoardConnected(event: BoardConnectedEvent) {
        boardNameView.text = resources.getString(R.string.title_board_name).format(event.boardInfo.name)
        boardNameView.visibility = View.VISIBLE

        spsView.text = resources.getString(R.string.title_sps_text).format(0)
        spsView.visibility = View.VISIBLE

        baudRateView.text = resources.getString(R.string.title_baud_rate_text).format(0)
        baudRateView.visibility = View.VISIBLE

        lineChart.axisLeft.axisMinimum = event.boardInfo.valuesRange.first
        lineChart.axisLeft.axisMaximum = event.boardInfo.valuesRange.second
        lineChart.fitScreen()
        lineChart.resetZoom()

        // XXX: Crunch for big axis values
        executionService.submit {
            TimeUnit.MILLISECONDS.sleep(50)
            lineChart.fitScreen()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBoardDisconnected(event: BoardDisconnectedEvent) {
        channelNameView.visibility = View.INVISIBLE
        boardNameView.visibility = View.INVISIBLE
        spsView.visibility = View.INVISIBLE
        baudRateView.visibility = View.INVISIBLE
    }

    override fun onResume() {
        super.onResume()
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        super.onPause()
        EventBus.getDefault().unregister(this)
    }
}