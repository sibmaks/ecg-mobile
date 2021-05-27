package xyz.dma.ecgmobile.service

enum class BoardResponseType(val code: String) {
    DATA_FLOW_ON("\nDF_ON"),
    DATA_FLOW_OFF("\nDF_OFF"),
    MODEL("\nMODEL "),
    CHANNELS_COUNT("\nCHS_CT "),
    VERSION("\nVERSION "),
    MIN_VALUE("\nMIN_VALUE "),
    MAX_VALUE("\nMAX_VALUE "),
    DATA("\nDATA "),
    TOO_SLOW_ERROR("\nTSE"),
    END("\nEND"),
}