package org.wordpress.android.fluxc.network.rest.wpcom.wc

import android.content.Context
import com.android.volley.RequestQueue
import com.google.gson.reflect.TypeToken
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.WCCoreAction
import org.wordpress.android.fluxc.generated.WCCoreActionBuilder
import org.wordpress.android.fluxc.generated.endpoint.WOOCOMMERCE
import org.wordpress.android.fluxc.generated.endpoint.WPCOMREST
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.WCSettingsModel
import org.wordpress.android.fluxc.model.WCSettingsModel.CurrencyPosition
import org.wordpress.android.fluxc.model.WCSimpleSiteModel
import org.wordpress.android.fluxc.network.UserAgent
import org.wordpress.android.fluxc.network.discovery.RootWPAPIRestResponse
import org.wordpress.android.fluxc.network.rest.wpcom.BaseWPComRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.WPComErrorListener
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.WPComGsonNetworkError
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken
import org.wordpress.android.fluxc.network.rest.wpcom.jetpacktunnel.JetpackTunnelGsonRequest
import org.wordpress.android.fluxc.store.WooCommerceStore
import org.wordpress.android.fluxc.store.WooCommerceStore.ApiVersionError
import org.wordpress.android.fluxc.store.WooCommerceStore.ApiVersionErrorType
import org.wordpress.android.fluxc.store.WooCommerceStore.FetchApiVersionResponsePayload
import org.wordpress.android.fluxc.store.WooCommerceStore.FetchWCSimpleSitesError
import org.wordpress.android.fluxc.store.WooCommerceStore.FetchWCSimpleSitesErrorType
import org.wordpress.android.fluxc.store.WooCommerceStore.FetchWCSimpleSitesResponsePayload
import org.wordpress.android.fluxc.store.WooCommerceStore.FetchWCSiteSettingsResponsePayload
import org.wordpress.android.fluxc.store.WooCommerceStore.WCSiteSettingsError
import org.wordpress.android.fluxc.store.WooCommerceStore.WCSiteSettingsErrorType
import java.util.ArrayList
import javax.inject.Singleton

@Singleton
class WooCommerceRestClient(
    appContext: Context,
    private val dispatcher: Dispatcher,
    requestQueue: RequestQueue,
    accessToken: AccessToken,
    userAgent: UserAgent
) : BaseWPComRestClient(appContext, dispatcher, requestQueue, accessToken, userAgent) {
    /**
     * Makes a GET call to the root wp-json endpoint (`/`) via the Jetpack tunnel (see [JetpackTunnelGsonRequest])
     * for the given [SiteModel], and parses through the `namespaces` field in the result for supported versions
     * of the Woo API.
     *
     * Dispatches a [WCCoreAction.FETCHED_SITE_API_VERSION] action with the highest version of the Woo API supported
     * by the site (but no newer than the latest supported by FluxC).
     */
    fun getSupportedWooApiVersion(site: SiteModel) {
        val url = "/"
        val params = mapOf("_fields" to "authentication,namespaces")
        val request = JetpackTunnelGsonRequest.buildGetRequest(url, site.siteId, params,
                RootWPAPIRestResponse::class.java,
                { response: RootWPAPIRestResponse? ->
                    val namespaces = response?.namespaces

                    val maxWooApiVersion = namespaces?.run {
                        find { it == WooCommerceStore.WOO_API_NAMESPACE_V3 }
                                ?: find { it == WooCommerceStore.WOO_API_NAMESPACE_V2 }
                                ?: find { it == WooCommerceStore.WOO_API_NAMESPACE_V1 }
                    }

                    maxWooApiVersion?.let { maxApiVersion ->
                        val payload = FetchApiVersionResponsePayload(site, maxApiVersion)
                        dispatcher.dispatch(WCCoreActionBuilder.newFetchedSiteApiVersionAction(payload))
                    } ?: run {
                        val apiVersionError = ApiVersionError(ApiVersionErrorType.NO_WOO_API)
                        val payload = FetchApiVersionResponsePayload(apiVersionError, site)
                        dispatcher.dispatch(WCCoreActionBuilder.newFetchedSiteApiVersionAction(payload))
                    }
                },
                WPComErrorListener { networkError ->
                    val payload = FetchApiVersionResponsePayload(networkErrorToApiVersionError(networkError), site)
                    dispatcher.dispatch(WCCoreActionBuilder.newFetchedSiteApiVersionAction(payload))
                },
                { request: WPComGsonRequest<*> -> add(request) })
        add(request)
    }

    /**
     * Makes a GET call to `/wc/v3/settings/general` via the Jetpack tunnel (see [JetpackTunnelGsonRequest]),
     * retrieving site settings for the given WooCommerce [SiteModel].
     *
     * Dispatches a [WCCoreAction.FETCHED_SITE_SETTINGS] action with a selected subset of the response values,
     * converted to a [WCSettingsModel].
     */
    fun getSiteSettingsGeneral(site: SiteModel) {
        val url = WOOCOMMERCE.settings.general.pathV3
        val responseType = object : TypeToken<List<SiteSettingsGeneralResponse>>() {}.type
        val request = JetpackTunnelGsonRequest.buildGetRequest(url, site.siteId, emptyMap(), responseType,
                { response: List<SiteSettingsGeneralResponse>? ->
                    response?.let {
                        val currencyCode = getValueForSettingsField(it, "woocommerce_currency")
                        val currencyPosition = getValueForSettingsField(it, "woocommerce_currency_pos")
                        val currencyThousandSep = getValueForSettingsField(it, "woocommerce_price_thousand_sep")
                        val currencyDecimalSep = getValueForSettingsField(it, "woocommerce_price_decimal_sep")
                        val currencyNumDecimals = getValueForSettingsField(it, "woocommerce_price_num_decimals")
                        val settings = WCSettingsModel(
                                localSiteId = site.id,
                                currencyCode = currencyCode ?: "",
                                currencyPosition = CurrencyPosition.fromString(currencyPosition),
                                currencyThousandSeparator = currencyThousandSep ?: "",
                                currencyDecimalSeparator = currencyDecimalSep ?: "",
                                currencyDecimalNumber = currencyNumDecimals?.toIntOrNull() ?: 2
                        )

                        val payload = FetchWCSiteSettingsResponsePayload(site, settings)
                        dispatcher.dispatch(WCCoreActionBuilder.newFetchedSiteSettingsAction(payload))
                    } ?: run {
                        val wcSiteSettingsError = WCSiteSettingsError(WCSiteSettingsErrorType.INVALID_RESPONSE)
                        val payload = FetchWCSiteSettingsResponsePayload(wcSiteSettingsError, site)
                        dispatcher.dispatch(WCCoreActionBuilder.newFetchedSiteSettingsAction(payload))
                    }
                },
                WPComErrorListener { networkError ->
                    val payload = FetchWCSiteSettingsResponsePayload(networkErrorToSettingsError(networkError), site)
                    dispatcher.dispatch(WCCoreActionBuilder.newFetchedSiteSettingsAction(payload))
                },
                { request: WPComGsonRequest<*> -> add(request) })
        add(request)
    }

    private fun getValueForSettingsField(settingsResponse: List<SiteSettingsGeneralResponse>, field: String): String? {
        return settingsResponse.find { it.id != null && it.id == field }?.value?.asString
    }

    private fun networkErrorToApiVersionError(wpComError: WPComGsonNetworkError): ApiVersionError {
        val apiVersionErrorErrorType = ApiVersionErrorType.fromString(wpComError.apiError)
        return ApiVersionError(apiVersionErrorErrorType, wpComError.message)
    }

    private fun networkErrorToSettingsError(wpComError: WPComGsonNetworkError): WCSiteSettingsError {
        val wcSiteSettingsErrorType = WCSiteSettingsErrorType.fromString(wpComError.apiError)
        return WCSiteSettingsError(wcSiteSettingsErrorType, wpComError.message)
    }

    /**
     * Lightweight version of SiteStore.fetchSites() which returns only the ID, name, and URL of sites
     * running WooCommerce
     */
    fun fetchWooSimpleSites() {
        val url = WPCOMREST.me.sites.urlV1_1

        // limit the response to just these fields and limit the options field to just the one that tells us
        // whether the site is running WooCommerce
        val params = mapOf(
                "fields" to "ID,URL,options,name",
                "options" to "woocommerce_is_active"
        )

        val responseType = object : TypeToken<WooSimpleSitesResponse>() {}.type

        val request = WPComGsonRequest.buildGetRequest(url, params, responseType,
                { response: WooSimpleSitesResponse? ->
                    val simoleSiteList = ArrayList<WCSimpleSiteModel>()
                    response?.sites?.forEach { site ->
                        site.options?.let { options ->
                            if (options.woocommerce_is_active) {
                                val simpleSite = WCSimpleSiteModel(site.ID ?: 0, site.URL ?: "", site.name ?: "")
                                simoleSiteList.add(simpleSite)
                            }
                        }
                    }
                    val payload = FetchWCSimpleSitesResponsePayload(simoleSiteList)
                    dispatcher.dispatch(WCCoreActionBuilder.newFetchedWooSimpleSitesAction(payload))
                },
                { error ->
                    val wcSimpleSitesError = FetchWCSimpleSitesError(FetchWCSimpleSitesErrorType.GENERIC_ERROR)
                    val payload = FetchWCSimpleSitesResponsePayload(wcSimpleSitesError, emptyList())
                    dispatcher.dispatch(WCCoreActionBuilder.newFetchedWooSimpleSitesAction(payload))
                }
        )
        add(request)
    }
}
