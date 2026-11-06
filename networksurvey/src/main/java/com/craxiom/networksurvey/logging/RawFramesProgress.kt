package com.craxiom.networksurvey.logging

/**
 * What the raw frames (pcapng) file has saved this survey, for the Records panel's own raw line.
 *
 * @property framesSaved Frames written, over every file of the survey (rollovers included).
 * @property bytesSaved  Bytes written, over every file of the survey.
 * @property state       Whether frames are still being saved.
 * @property framesDropped Frames the Scout sent that never made it to a file: the writer fell behind,
 * or the Bluetooth link lost a notification (counted as one frame, the least it held). Frames the
 * board itself dropped are on the NS Scout page instead.
 */
data class RawFramesProgress(
    val framesSaved: Long = 0,
    val bytesSaved: Long = 0,
    val state: State = State.SAVING,
    val framesDropped: Long = 0,
) {
    /** Whether raw frames are being saved. */
    enum class State {
        /** Saving (or waiting for the first frame). */
        SAVING,

        /** The phone's storage is nearly full, so saving stopped; the file so far is kept and readable. */
        STORAGE_FULL,

        /** A file could not be created or written for another reason, so saving stopped. */
        FAILED,
    }
}
