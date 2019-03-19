package org.wordpress.android.fluxc.model.list

import android.arch.paging.DataSource
import android.arch.paging.PositionalDataSource
import org.wordpress.android.fluxc.model.list.datastore.ListStoreInternalDataStore

/**
 * TODO: Update this comment if necessary
 *
 * A [DataSource.Factory] instance for `ListStore` lists. It creates instances of [PagedListPositionalDataSource].
 *
 * All properties are passed to [PagedListPositionalDataSource] during instantiation.
 */
class PagedListFactory<T, I>(
    private val createDataStore: () -> ListStoreInternalDataStore<T, I>
) : DataSource.Factory<Int, T>() {
    private var currentSource: PagedListPositionalDataSource<T, I>? = null

    override fun create(): DataSource<Int, T> {
        val source = PagedListPositionalDataSource(dataStore = createDataStore.invoke())
        currentSource = source
        return source
    }

    fun invalidate() {
        currentSource?.invalidate()
    }
}

///**
// TODO: Update these comments now that it's fully simplified
// * A positional data source for [PagedListItemType].
// *
// * @param listDescriptor Which list this data source is for.
// * @param dataStore Describes how to take certain actions such as fetching list for the item type [T].
// * @param getList A function to get the list for the given [ListDescriptor]
// * @param isListFullyFetched A function to check whether the list is fully fetched. It's used to add an
// * [EndListIndicatorItem] at the end of the list.
// * @param transform A transform function from the actual item type [T], to the resulting item type [R]. In many
// * cases there are a lot of expensive calculations that needs to be made before an item can be used. This function
// * provides a way to do that during the pagination step in a background thread, so the clients won't need to
// * worry about these expensive operations.
// */
private class PagedListPositionalDataSource<T, I>(
    private val dataStore: ListStoreInternalDataStore<T, I>
) : PositionalDataSource<T>() {
    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<T>) {
        val startPosition = computeInitialLoadPosition(params, dataStore.totalSize)
        val loadSize = computeInitialLoadSize(params, startPosition, dataStore.totalSize)
        val items = loadRangeInternal(startPosition, loadSize)
        if (params.placeholdersEnabled) {
            callback.onResult(items, startPosition, dataStore.totalSize)
        } else {
            callback.onResult(items, startPosition)
        }
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<T>) {
        val items = loadRangeInternal(params.startPosition, params.loadSize)
        callback.onResult(items)
    }

    private fun loadRangeInternal(startPosition: Int, loadSize: Int): List<T> {
        val endPosition = startPosition + loadSize
        if (startPosition == endPosition) {
            return emptyList()
        }
        return dataStore.getItemsInRange(startPosition, endPosition)
    }
}
