package org.wordpress.android.fluxc.action;

import org.wordpress.android.fluxc.annotations.Action;
import org.wordpress.android.fluxc.annotations.ActionEnum;
import org.wordpress.android.fluxc.annotations.action.IAction;
import org.wordpress.android.fluxc.model.PostModel;
import org.wordpress.android.fluxc.store.PostStore.FetchPostResponsePayload;
import org.wordpress.android.fluxc.store.PostStore.FetchPostsPayload;
import org.wordpress.android.fluxc.store.PostStore.SearchPostsPayload;
import org.wordpress.android.fluxc.store.PostStore.SearchPostsResponsePayload;
import org.wordpress.android.fluxc.store.PostStore.FetchPostsResponsePayload;
import org.wordpress.android.fluxc.store.PostStore.RemotePostPayload;

@ActionEnum
public enum PostAction implements IAction {
    // Remote actions
    @Action(payloadType = FetchPostsPayload.class)
    FETCH_POSTS,
    @Action(payloadType = FetchPostsPayload.class)
    FETCH_PAGES,
    @Action(payloadType = FetchPostsPayload.class)
    FETCH_PORTFOLIOS,
    @Action(payloadType = RemotePostPayload.class)
    FETCH_POST,
    @Action(payloadType = RemotePostPayload.class)
    PUSH_POST,
    @Action(payloadType = RemotePostPayload.class)
    DELETE_POST,
    @Action(payloadType = SearchPostsPayload.class)
    SEARCH_POSTS,
    @Action(payloadType = SearchPostsPayload.class)
    SEARCH_PAGES,

    // Remote responses
    @Action(payloadType = FetchPostsResponsePayload.class)
    FETCHED_POSTS,
    @Action(payloadType = FetchPostResponsePayload.class)
    FETCHED_POST,
    @Action(payloadType = RemotePostPayload.class)
    PUSHED_POST,
    @Action(payloadType = RemotePostPayload.class)
    DELETED_POST,
    @Action(payloadType = SearchPostsResponsePayload.class)
    SEARCHED_POSTS,

    // Local actions
    @Action(payloadType = PostModel.class)
    UPDATE_POST,
    @Action(payloadType = PostModel.class)
    REMOVE_POST,
    @Action
    REMOVE_ALL_POSTS
}

