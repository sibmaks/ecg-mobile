package xyz.dma.ecgmobile.entity

class BoardInfo(
    val name: String,
    val version: String,
    val channels: UInt,
    val maxDataToSend: UInt,
    val valuesRange: Pair<Float, Float>
)