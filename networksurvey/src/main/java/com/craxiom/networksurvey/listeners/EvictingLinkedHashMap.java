package com.craxiom.networksurvey.listeners;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link LinkedHashMap} that drops its eldest entry once it grows past a fixed capacity. Used
 * by the CDR content observers as a small cache of recently logged message IDs so that the
 * repeated change notifications the platform fires for a single message do not produce
 * duplicate CDR records.
 */
class EvictingLinkedHashMap<K, V> extends LinkedHashMap<K, V>
{
    private final int maxEntries;

    EvictingLinkedHashMap(int maxEntries)
    {
        this.maxEntries = maxEntries;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest)
    {
        return size() > maxEntries;
    }
}
