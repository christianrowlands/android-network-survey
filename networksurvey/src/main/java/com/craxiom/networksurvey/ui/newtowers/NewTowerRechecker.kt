package com.craxiom.networksurvey.ui.newtowers

import com.craxiom.networksurvey.logging.db.NewTowerFindStore
import com.craxiom.networksurvey.model.TowerKey
import com.craxiom.networksurvey.ui.activesurvey.TowerLookup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Asks the tower service again about the finds that were not in the tower database when last
 * checked, so the list can say when one has been added since. The New towers page runs it once
 * each time it opens.
 *
 * It checks at most [MAX_PER_OPEN] finds, one at a time, so opening the page never sends a burst
 * of requests against the tower service's shared quota. The longest unchecked go first, so a list
 * longer than [MAX_PER_OPEN] is worked through over later opens instead of the newest finds being
 * asked about every time. A find is due when its
 * last check is older than [NewTowerFindStore.RECHECK_AGE_MS]. A failed check (no network, a
 * rate limit, a server error) leaves the find as it was and ends the run, since the checks after
 * it would most likely fail the same way.
 *
 * @param lookUp Asks the tower service about one tower without the local cache; see
 * [com.craxiom.networksurvey.ui.activesurvey.TowerDetectionManager.lookUp].
 */
class NewTowerRechecker(
    private val store: NewTowerFindStore,
    private val lookUp: suspend (TowerKey) -> TowerLookup,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Re-checks up to [limit] due finds, one after another.
     *
     * @return How many finds got an answer (in or not in the tower database).
     */
    suspend fun recheckDue(limit: Int = MAX_PER_OPEN): Int = withContext(io) {
        var answered = 0
        for (find in store.needingRecheck(clock(), limit)) {
            val key = find.towerKey() ?: continue
            val lookup = lookUp(key)
            if (lookup == TowerLookup.Failed) {
                Timber.i("Stopped re-checking new towers after a failed check of %s", key)
                break
            }
            store.recordLookup(find.id, lookup, clock())
            answered++
        }
        answered
    }

    companion object {
        /** The most finds one page open re-checks. */
        const val MAX_PER_OPEN = 10
    }
}
