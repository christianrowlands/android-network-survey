package com.craxiom.networksurvey.ui.watchlist

import com.craxiom.networksurvey.model.WatchlistImportSet

/**
 * A tiny in-memory handoff for a pending watchlist import. The Activity parses an import deep link and
 * stashes the validated set here, then navigates to the Watchlist screen, whose ViewModel consumes it.
 *
 * Deliberately not persisted: a watchlist import carries no secrets (unlike the NS Analytics
 * registration token, which uses encrypted storage), and an import that is interrupted by process
 * death should simply be re-triggered by tapping the link again rather than resurrected later. If two
 * links arrive before the first is consumed, the later one wins.
 */
object WatchlistImportHolder {

    @Volatile
    private var pending: WatchlistImportSet? = null

    /** Stash a parsed import set for the Watchlist screen to pick up. */
    @Synchronized
    fun set(importSet: WatchlistImportSet) {
        pending = importSet
    }

    /** Return the pending import (if any) and clear it, so it is handled exactly once. */
    @Synchronized
    fun consume(): WatchlistImportSet? {
        val value = pending
        pending = null
        return value
    }
}
