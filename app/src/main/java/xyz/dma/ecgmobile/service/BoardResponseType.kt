package xyz.dma.ecgmobile.service

const val START_CHAR = '\n'

enum class BoardResponseType(val code: String, val content: Boolean = true) {
    DATA_FLOW_ON(START_CHAR + "DF1", false),
    DATA_FLOW_OFF(START_CHAR + "DF0", false),

    MODEL(START_CHAR + "MDL"),
    CHANNELS_COUNT(START_CHAR + "CHC"),
    VERSION(START_CHAR + "VRS"),
    MIN_VALUE(START_CHAR + "MNV"),
    MAX_VALUE(START_CHAR + "MXV"),

    DATA(START_CHAR + "DAT"),
    END(START_CHAR + "END", false),
}