package com.craxiom.networksurvey.ui.privacy

/**
 * Counts the community upload queue's records that a zone would remove, for the editor's live line
 * "N records waiting to upload from inside this zone won't be uploaded".
 */
fun interface WaitingUploadCounter {
    /**
     * @return How many records still waiting to upload to the community databases fall inside a
     * circle of [radiusMeters] around [latitude], [longitude] by the same rule the queue scrub uses.
     * Called off the main thread; may read the database.
     */
    suspend fun countInside(latitude: Double, longitude: Double, radiusMeters: Int): Int

    companion object {
        /** Counts nothing, so the line stays hidden (for tests and previews). */
        val NONE = WaitingUploadCounter { _, _, _ -> 0 }
    }
}
