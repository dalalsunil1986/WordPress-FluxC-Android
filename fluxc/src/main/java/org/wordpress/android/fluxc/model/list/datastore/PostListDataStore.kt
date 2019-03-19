package org.wordpress.android.fluxc.model.list.datastore

import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.PostActionBuilder
import org.wordpress.android.fluxc.model.LocalOrRemoteId.RemoteId
import org.wordpress.android.fluxc.model.PostModel
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.list.ListDescriptor
import org.wordpress.android.fluxc.model.list.PostListDescriptor
import org.wordpress.android.fluxc.model.list.datastore.PostListItemIdentifier.EndListIndicator
import org.wordpress.android.fluxc.model.list.datastore.PostListItemIdentifier.LocalPostId
import org.wordpress.android.fluxc.model.list.datastore.PostListItemIdentifier.RemotePostId
import org.wordpress.android.fluxc.store.PostStore
import org.wordpress.android.fluxc.store.PostStore.FetchPostListPayload
import org.wordpress.android.fluxc.store.PostStore.RemotePostPayload

// TODO: Remove this comment
// This is used for a temporary solution for preventing duplicate fetch requests for posts. This workaround should
// be moved in a later minor rework of how we fetch individual posts for the paged list.
private data class SitePostId(val localSiteId: Int, val remotePostId: Long)

sealed class PostListItemIdentifier {
    data class LocalPostId(val value: Int) : PostListItemIdentifier()
    data class RemotePostId(val value: Long) : PostListItemIdentifier()
    object EndListIndicator : PostListItemIdentifier()
}

// TODO: Introduce an internal data store that's used by PostListDataStore and make sure WPAndroid or another client doesn't need to pass anything that FluxC itself already knows about. Below is just a quick mock up to clean up other places:

// TODO: There is quite a bit of cleanup in this class, but they should be straightforward to handle once all other pieces are figured out
class PostListDataStore<T>(
    private val postListDescriptor: PostListDescriptor,
    private val dispatcher: Dispatcher,
    private val postStore: PostStore,
    private val site: SiteModel?,
    private val transform: (PostModel) -> T,
    private val performGetItemIdsToHide: ((ListDescriptor) -> List<Pair<Int?, Long?>>)? = null
) : ListItemDataStoreInterface<T, PostListItemIdentifier> {
    override fun getItemsAndFetchIfNecessary(itemIdentifiers: List<PostListItemIdentifier>): List<T> {
        TODO("Use the identifier to get the local and remote posts from PostStore. A single sql query should be enough to do both of them. Fetch all missing remote posts. No local posts should normally be missing, but an edge case could occur where the local post is removed but not reflected immediately in the list. In this case we'll probably want to just log it and handle it gracefully. Finally, the items should be transformed to R")
    }

    override fun getItemIdentifiers(
        remoteItemIds: List<RemoteId>,
        isListFullyFetched: Boolean
    ): List<PostListItemIdentifier> {
        val localItems = localPostIds()
        val remoteItemIdsToHide = getItemIdsToHide().mapNotNull { it.second }
        val remoteItems = remoteItemIds.asSequence().filter {
            !remoteItemIdsToHide.contains(it.value)
        }.map { RemotePostId(it.value) }.toList()
        val actualItems = localItems.plus(remoteItems)
        // We only want to show the end list indicator if the list is fully fetched and it's not empty
        return if (isListFullyFetched && actualItems.isNotEmpty()) {
            actualItems.plus(EndListIndicator)
        } else {
            actualItems
        }
    }

    private val fetchingSet = HashSet<SitePostId>()

    private fun fetchItem(remoteItemId: Long) {
        site?.let {
            val sitePostId = SitePostId(localSiteId = it.id, remotePostId = remoteItemId)
            // Only fetch the post if there is no request going on
            if (!fetchingSet.contains(sitePostId)) {
                fetchingSet.add(sitePostId)

                val postToFetch = PostModel()
                postToFetch.remotePostId = remoteItemId
                val payload = RemotePostPayload(postToFetch, it)
                dispatcher.dispatch(PostActionBuilder.newFetchPostAction(payload))
            }
        }
    }

    override fun fetchList(listDescriptor: ListDescriptor, offset: Long) {
        // TODO: Should we also check whether listDescriptor is the same as postListDescriptor
        if (listDescriptor is PostListDescriptor) {
            val fetchPostListPayload = FetchPostListPayload(listDescriptor, offset)
            dispatcher.dispatch(PostActionBuilder.newFetchPostListAction(fetchPostListPayload))
        }
    }

    private fun localPostIds(): List<LocalPostId> {
        val localPostIdsToHide = getItemIdsToHide().mapNotNull { it.first }
        return postStore.getLocalPostIdsForDescriptor(postListDescriptor)
                .filter { !localPostIdsToHide.contains(it.value) }
    }

    private fun getItemByRemoteId(remotePostId: RemotePostId): PostModel? {
        val post = postStore.getPostByRemotePostId(remotePostId.value, site)
        if (post != null) {
            val sitePostId = SitePostId(localSiteId = postListDescriptor.site.id, remotePostId = remotePostId.value)
            fetchingSet.remove(sitePostId)
        }
        return post
    }

    private fun getItemIdsToHide(): List<Pair<Int?, Long?>> {
        return performGetItemIdsToHide?.invoke(postListDescriptor) ?: emptyList()
    }
}
