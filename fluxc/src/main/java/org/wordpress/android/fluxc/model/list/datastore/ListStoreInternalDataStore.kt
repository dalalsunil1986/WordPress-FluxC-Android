package org.wordpress.android.fluxc.model.list.datastore

import org.wordpress.android.fluxc.model.LocalOrRemoteId.RemoteId

/**
 * TODO: Explain what this class does and also why it's necessary. Some temporary notes:
 * Why is this class necessary:
 *
 * 1. Keeps everything else stateless
 * 2. Extracts common part of the data store interfaces however small that part is
 * 3. Brings more structure
 * 4. Testability
 */
class ListStoreInternalDataStore<T, I>(
    remoteItemIds: List<RemoteId>,
    isListFullyFetched: Boolean,
    private val itemDataStore: ListItemDataStoreInterface<T, I>
) {
    // TODO: This is how we can make everything else stateless. We take a snapshot of the data when store is created
    // and easiest way to do that is to just take a snapshot of the identifiers. This should also be the least expensive
    // in most cases (at least in theory)
    // TODO: All this probably should be documented
    private val itemIdentifiers = itemDataStore.getItemIdentifiers(remoteItemIds, isListFullyFetched)
    val totalSize: Int = itemIdentifiers.size

    fun getItemsInRange(startPosition: Int, endPosition: Int): List<T> =
            itemDataStore.getItemsAndFetchIfNecessary(getItemIds(startPosition, endPosition))

    private fun getItemIds(startPosition: Int, endPosition: Int): List<I> {
        if (startPosition < 0 || endPosition < 0 || startPosition > endPosition || endPosition >= totalSize) {
            // TODO: Add a message
            throw IllegalArgumentException()
        }

        return itemIdentifiers.subList(startPosition, endPosition)
    }
}
