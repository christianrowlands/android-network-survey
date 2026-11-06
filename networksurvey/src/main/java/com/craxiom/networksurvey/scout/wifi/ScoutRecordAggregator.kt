package com.craxiom.networksurvey.scout.wifi

/**
 * Turns a Scout's observations (one per item per channel dwell) into at most one record per item
 * per window, the way a phone scan gives one record per access point per scan.
 *
 * Time is cut into windows by capture time (`floor(time / windowMs)`), so a backlog that arrives
 * all at once after a reconnect still gives one record per item for every window it was heard
 * in, each placed where and when it was heard. Within a window the strongest observation wins,
 * and [absorb] can fold into it what the window's other observations of the item add.
 * A window is drained once it has ended and [graceMs] has passed, which leaves time for the
 * board's batching. An item already emitted for a window is not emitted for it again, so a late
 * observation of an emitted window is dropped rather than duplicated.
 *
 * Generic so access points (BSSID), clients (MAC) and probes (MAC and SSID) share it. Not thread
 * safe; one owner thread calls it.
 *
 * @param keyOf The item an observation belongs to.
 * @param strengthOf The value compared to keep the strongest observation (RSSI).
 * @param timeOf The observation's capture time, wall clock, Unix epoch milliseconds.
 * @param absorb Given the observation that stays (the strongest so far) and another of the same
 *               item and window, returns what stays. By default the other is dropped.
 */
class ScoutRecordAggregator<K : Any, T : Any>(
    private val keyOf: (T) -> K,
    private val strengthOf: (T) -> Int,
    private val timeOf: (T) -> Long,
    private val graceMs: Long = DEFAULT_GRACE_MS,
    private val emittedRetentionMs: Long = DEFAULT_EMITTED_RETENTION_MS,
    private val absorb: (kept: T, other: T) -> T = { kept, _ -> kept },
) {
    /** One window's records, in the order their items were first heard in it. */
    data class Window<T>(val startMs: Long, val endMs: Long, val items: List<T>)

    private data class Bucket(val startMs: Long, val endMs: Long)

    private val pending = LinkedHashMap<Bucket, LinkedHashMap<K, T>>()
    private val emitted = HashMap<Bucket, MutableSet<K>>()

    /** Adds [item] to its window of length [windowMs]. */
    fun add(item: T, windowMs: Long) {
        val length = windowMs.coerceAtLeast(MIN_WINDOW_MS)
        val start = Math.floorDiv(timeOf(item), length) * length
        val bucket = Bucket(start, start + length)
        val key = keyOf(item)
        if (emitted[bucket]?.contains(key) == true) return
        val items = pending.getOrPut(bucket) { LinkedHashMap() }
        val existing = items[key]
        items[key] = when {
            existing == null -> item
            strengthOf(item) > strengthOf(existing) -> absorb(item, existing)
            else -> absorb(existing, item)
        }
    }

    /** @return Every window that ended at least [graceMs] before [nowMs], oldest first. */
    fun drainClosed(nowMs: Long): List<Window<T>> {
        pruneEmitted(nowMs)
        return drain { it.endMs + graceMs <= nowMs }
    }

    /** @return Everything still pending, oldest first, for a stop or a window change. */
    fun drainAll(): List<Window<T>> = drain { true }

    /** @return The record slot of [item] in [window], so a record keeps its number across sinks. */
    fun slotOf(window: Window<T>, item: T): ScoutRecordSlot = ScoutRecordSlot(window.startMs, keyOf(item))

    /** @return Everything still pending, oldest first, without draining it. */
    fun peekAll(): List<Window<T>> = pending.entries.sortedBy { it.key.startMs }
        .map { (bucket, items) -> Window(bucket.startMs, bucket.endMs, items.values.toList()) }

    /** Forgets everything, pending and emitted. */
    fun clear() {
        pending.clear()
        emitted.clear()
    }

    private fun drain(closed: (Bucket) -> Boolean): List<Window<T>> {
        val ready = pending.keys.filter(closed).sortedBy { it.startMs }
        return ready.map { bucket ->
            val items = pending.remove(bucket)!!
            emitted.getOrPut(bucket) { HashSet() }.addAll(items.keys)
            Window(bucket.startMs, bucket.endMs, items.values.toList())
        }
    }

    private fun pruneEmitted(nowMs: Long) {
        emitted.keys.removeAll { it.endMs < nowMs - emittedRetentionMs }
    }

    companion object {
        /** Time for an observation to arrive after its window ended (board batching). */
        const val DEFAULT_GRACE_MS = 2_000L

        /** How long emitted items are remembered to drop late duplicates. */
        const val DEFAULT_EMITTED_RETENTION_MS = 10L * 60 * 1000

        /** The shortest window, so a zero or negative scan rate cannot divide by zero. */
        const val MIN_WINDOW_MS = 1_000L
    }
}
