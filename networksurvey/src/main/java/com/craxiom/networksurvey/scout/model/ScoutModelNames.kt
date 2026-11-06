package com.craxiom.networksurvey.scout.model

import com.craxiom.networksurvey.board.v1.BoardModel

/**
 * The names people know Scout boards by, matching the iOS app's `BoardText.model`, shown on
 * screen.
 */
object ScoutModelNames {
    private const val ESP32_C5_DEVKITC_1 = "ESP32-C5-DevKitC-1"
    private const val XIAO_ESP32_C5 = "XIAO ESP32-C5"
    private const val ESP32_C6 = "ESP32-C6"
    private const val ESP32_S3 = "ESP32-S3"
    private const val ESP32_C3 = "ESP32-C3"

    /**
     * @return [modelName] when the board reports one (third-party boards must), otherwise the name
     * of [boardModel], or empty for an unknown model.
     */
    @JvmStatic
    fun displayName(boardModel: Int, modelName: String): String {
        if (modelName.isNotEmpty()) return modelName
        return when (BoardModel.forNumber(boardModel)) {
            BoardModel.BOARD_MODEL_ESP32_C5_DEVKITC_1 -> ESP32_C5_DEVKITC_1
            BoardModel.BOARD_MODEL_XIAO_ESP32_C5 -> XIAO_ESP32_C5
            BoardModel.BOARD_MODEL_ESP32_C6_GENERIC -> ESP32_C6
            BoardModel.BOARD_MODEL_ESP32_S3_GENERIC -> ESP32_S3
            BoardModel.BOARD_MODEL_ESP32_C3_GENERIC -> ESP32_C3
            else -> ""
        }
    }
}
