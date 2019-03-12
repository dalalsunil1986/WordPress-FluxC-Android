package org.wordpress.android.fluxc.action;

import org.wordpress.android.fluxc.annotations.Action;
import org.wordpress.android.fluxc.annotations.ActionEnum;
import org.wordpress.android.fluxc.annotations.action.IAction;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.store.WooCommerceStore.FetchApiVersionResponsePayload;
import org.wordpress.android.fluxc.store.WooCommerceStore.FetchWCSimpleSitesResponsePayload;
import org.wordpress.android.fluxc.store.WooCommerceStore.FetchWCSiteSettingsResponsePayload;

@ActionEnum
public enum WCCoreAction implements IAction {
    // Remote actions
    @Action(payloadType = SiteModel.class)
    FETCH_SITE_API_VERSION,
    @Action(payloadType = SiteModel.class)
    FETCH_SITE_SETTINGS,
    @Action
    FETCH_WOO_SIMPLE_SITES,

    // Remote responses
    @Action(payloadType = FetchApiVersionResponsePayload.class)
    FETCHED_SITE_API_VERSION,
    @Action(payloadType = FetchWCSiteSettingsResponsePayload.class)
    FETCHED_SITE_SETTINGS,
    @Action(payloadType = FetchWCSimpleSitesResponsePayload.class)
    FETCHED_WOO_SIMPLE_SITES
}
