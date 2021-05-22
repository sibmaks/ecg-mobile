package xyz.dma.ecgmobile.event

import com.github.mikephil.charting.data.Entry

class ChannelChangedEvent(val channel: String, val data: List<Entry>)