package com.craxiom.networksurvey.model;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.SortedList;

/**
 * Creating our own implementation for the {@link SortedList} because while the SortedList acts sort of like a set, it
 * depends on the current sort order on how it evicts old items (it uses the compare method too which means that the
 * current sorting option will change which records get added). Note that we have only implemented the {@link #add(Object)}
 * method, so don't use the addAll or any other add methods from the parent class if you want to make sure to evict
 * old duplicate records.
 *
 * @since 1.0.0
 */
public class SortedSet<T> extends SortedList<T>
{
    @NonNull
    private final Callback<T> callback;

    public SortedSet(@NonNull Class<T> klass, @NonNull Callback<T> callback)
    {
        super(klass, callback);
        this.callback = callback;
    }

    /**
     * This method first removes any item that is found to match the item being added (based on the
     * {@link androidx.recyclerview.widget.SortedList.Callback#areItemsTheSame(Object, Object)} method). Then, the new
     * item is added to this list.
     *
     * @param item The item to add (and remove the old matching item if found).
     * @return The index of the newly added item.
     */
    @Override
    public int add(T item)
    {
        final int sortedListSize = size();
        for (int i = 0; i < sortedListSize; ++i)
        {
            final T existingItem = get(i);

            if (callback.areItemsTheSame(existingItem, item))
            {
                removeItemAt(i);
                break;
            }
        }

        return super.add(item);
    }
}
