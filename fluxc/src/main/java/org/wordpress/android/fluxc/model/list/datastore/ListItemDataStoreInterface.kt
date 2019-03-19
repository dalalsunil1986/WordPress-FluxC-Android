package org.wordpress.android.fluxc.model.list.datastore

import org.wordpress.android.fluxc.model.LocalOrRemoteId.RemoteId
import org.wordpress.android.fluxc.model.list.ListDescriptor

/**
 * TODO: Document this interface. Temporary notes:
 * T is the return type of the list. This could just be the regular model, but it probably shouldn't be in most cases.
 * I is the identifier for whatever T is. If the T is a model, it could be the local id or the remote id, or both. If
 * it's an item such as End list indicator, it could be just the type information. This will need to be decided in a
 * case by case basis.
 */
interface ListItemDataStoreInterface<T, I> {
    fun getItemsAndFetchIfNecessary(itemIdentifiers: List<I>): List<T>
    fun getItemIdentifiers(remoteItemIds: List<RemoteId>, isListFullyFetched: Boolean): List<I>
    fun fetchList(listDescriptor: ListDescriptor, offset: Long)
}
