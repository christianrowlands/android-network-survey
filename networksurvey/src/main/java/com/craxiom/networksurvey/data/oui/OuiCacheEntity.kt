package com.craxiom.networksurvey.data.oui

import androidx.room.Entity
import androidx.room.Index

/**
 * Persistent cache row for a single OUI lookup result.
 *
 * Keyed on the MAC prefix + prefix length. A 24-bit prefix `AA:BB:CC:xx:xx:xx` is stored as
 * `prefixLong = 0xAABBCC000000L, prefixLen = 24`. A 28-bit prefix `AA:BB:CC:Dx:xx:xx` is stored
 * as `prefixLong = 0xAABBCCD00000L, prefixLen = 28`. This mirrors the server's longest-prefix-
 * first match semantics so the client can probe 36 → 28 → 24 and use the first hit.
 *
 * `status` mirrors [OuiStatus.name] for persisted outcomes. Only RESOLVED / PRIVATE entries
 * carry a `vendor` string. `TRANSIENT_FAILURE` / `OFFLINE` / `LAA` / `LOOKUP_DISABLED` /
 * `LOADING` are never persisted.
 *
 * `datasetVersion` stamps the entry with the server's dataset hash at fetch time so stale rows
 * can be lazily evicted when the server version changes.
 */
@Entity(
    tableName = "oui_cache_entry",
    primaryKeys = ["prefix_long", "prefix_len"],
    indices = [Index(value = ["last_seen_ts"])]
)
data class OuiCacheEntity(
    @androidx.room.ColumnInfo(name = "prefix_long") val prefixLong: Long,
    @androidx.room.ColumnInfo(name = "prefix_len") val prefixLen: Int,
    @androidx.room.ColumnInfo(name = "status") val status: String,
    @androidx.room.ColumnInfo(name = "vendor") val vendor: String?,
    @androidx.room.ColumnInfo(name = "dataset_version") val datasetVersion: String,
    @androidx.room.ColumnInfo(name = "last_seen_ts") val lastSeenTs: Long
)
