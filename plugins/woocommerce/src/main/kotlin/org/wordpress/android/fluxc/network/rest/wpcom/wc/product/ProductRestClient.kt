package org.wordpress.android.fluxc.network.rest.wpcom.wc.product

import android.content.Context
import com.android.volley.RequestQueue
import com.google.gson.reflect.TypeToken
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.WCProductActionBuilder
import org.wordpress.android.fluxc.generated.endpoint.WOOCOMMERCE
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.WCProductModel
import org.wordpress.android.fluxc.model.WCProductVariationModel
import org.wordpress.android.fluxc.network.UserAgent
import org.wordpress.android.fluxc.network.rest.wpcom.BaseWPComRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.WPComErrorListener
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.WPComGsonNetworkError
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken
import org.wordpress.android.fluxc.network.rest.wpcom.jetpacktunnel.JetpackTunnelGsonRequest
import org.wordpress.android.fluxc.network.utils.getString
import org.wordpress.android.fluxc.store.WCProductStore.ProductError
import org.wordpress.android.fluxc.store.WCProductStore.ProductErrorType
import org.wordpress.android.fluxc.store.WCProductStore.RemoteProductPayload
import javax.inject.Singleton

@Singleton
class ProductRestClient(
    appContext: Context,
    private val dispatcher: Dispatcher,
    requestQueue: RequestQueue,
    accessToken: AccessToken,
    userAgent: UserAgent
) : BaseWPComRestClient(appContext, dispatcher, requestQueue, accessToken, userAgent) {
    /**
     * Makes a GET request to `/wp-json/wc/v3/products/[remoteProductId]` to fetch a single product, optionally
     * also fetching a product variation
     * Dispatches eitjer a WCProductAction.FETCHED_SINGLE_PRODUCT or WCProductAction.FETCHED_SINGLE_PRODUCT_VARIATION
     * action with the result
     */
    fun fetchSingleProduct(site: SiteModel, remoteProductId: Long, remoteVariationId: Long = 0L) {
        val url = WOOCOMMERCE.products.id(remoteProductId).pathV3
        val responseType = object : TypeToken<ProductApiResponse>() {}.type
        val params = emptyMap<String, String>()
        val request = JetpackTunnelGsonRequest.buildGetRequest(url, site.siteId, params, responseType,
                { response: ProductApiResponse? ->
                    response?.let {
                        val product = productResponseToProductModel(it).apply {
                            localSiteId = site.id
                        }

                        // we're done if this isn't a request for a product variation, otherwise we fire
                        // a separate request to fetch the variation
                        if (remoteVariationId == 0L) {
                            val payload = RemoteProductPayload(site, product)
                            dispatcher.dispatch(WCProductActionBuilder.newFetchedSingleProductAction(payload))
                        } else {
                            fetchProductVariation(site, product, remoteVariationId)
                        }
                    }
                },
                WPComErrorListener { networkError ->
                    val productError = networkErrorToProductError(networkError)
                    val payload = RemoteProductPayload(
                            site,
                            WCProductModel().apply { this.remoteProductId = remoteProductId },
                            0L,
                            productError
                    )
                    dispatcher.dispatch(WCProductActionBuilder.newFetchedSingleProductAction(payload))
                },
                { request: WPComGsonRequest<*> -> add(request) })
        add(request)
    }

    fun fetchProductVariation(site: SiteModel, product: WCProductModel, remoteVariationId: Long) {
        val url = WOOCOMMERCE.products.id(product.remoteProductId).variations.id(remoteVariationId).pathV3
        val responseType = object : TypeToken<ProductVariationApiResponse>() {}.type
        val params = emptyMap<String, String>()
        val request = JetpackTunnelGsonRequest.buildGetRequest(
                url, site.siteId, params, responseType,
                { response: ProductVariationApiResponse? ->
                    val variationModel = productVariationResponseToProductVariationModel(
                            site,
                            product,
                            response!!
                    )
                    product.updateFromVariation(variationModel)
                    val payload = RemoteProductPayload(site, product, remoteVariationId)
                    dispatcher.dispatch(
                            WCProductActionBuilder.newFetchedSingleProductAction(payload))
                },
                WPComErrorListener { networkError ->
                    val productError = networkErrorToProductError(networkError)
                    val payload = RemoteProductPayload(
                            site,
                            WCProductModel().apply {
                                this.remoteProductId = product.remoteProductId
                                this.remoteVariationId = remoteVariationId
                            },
                            0L,
                            productError
                    )
                    dispatcher.dispatch(
                            WCProductActionBuilder.newFetchedSingleProductAction(payload))
                },
                { request: WPComGsonRequest<*> -> add(request) })
        add(request)
    }

    private fun productResponseToProductModel(response: ProductApiResponse): WCProductModel {
        return WCProductModel().apply {
            remoteProductId = response.id ?: 0
            remoteVariationId = 0

            name = response.name ?: ""
            slug = response.slug ?: ""
            permalink = response.permalink ?: ""

            dateCreated = response.date_created ?: ""
            dateModified = response.date_modified ?: ""

            type = response.type ?: ""
            status = response.status ?: ""
            featured = response.featured
            catalogVisibility = response.catalog_visibility ?: ""
            description = response.description ?: ""
            shortDescription = response.short_description ?: ""
            sku = response.sku ?: ""

            price = response.price ?: ""
            regularPrice = response.regular_price ?: ""
            salePrice = response.sale_price ?: ""
            onSale = response.on_sale
            totalSales = response.total_sales

            virtual = response.virtual
            purchasable = response.purchasable
            downloadable = response.downloadable
            downloadLimit = response.download_limit
            downloadExpiry = response.download_expiry
            externalUrl = response.external_url ?: ""

            taxStatus = response.tax_status ?: ""
            taxClass = response.tax_class ?: ""

            manageStock = response.manage_stock
            stockQuantity = response.stock_quantity
            stockStatus = response.stock_status ?: ""

            backorders = response.backorders ?: ""
            backordersAllowed = response.backorders_allowed
            backordered = response.backordered
            soldIndividually = response.sold_individually
            weight = response.weight ?: ""

            shippingRequired = response.shipping_required
            shippingTaxable = response.shipping_taxable
            shippingClass = response.shipping_class ?: ""
            shippingClassId = response.shipping_class_id

            reviewsAllowed = response.reviews_allowed
            averageRating = response.average_rating ?: ""
            ratingCount = response.rating_count

            parentId = response.parent_id
            purchaseNote = response.purchase_note ?: ""

            categories = response.categories?.toString() ?: ""
            tags = response.tags?.toString() ?: ""
            images = response.images?.toString() ?: ""
            attributes = response.attributes?.toString() ?: ""
            variations = response.variations?.toString() ?: ""
            downloads = response.downloads?.toString() ?: ""
            relatedIds = response.related_ids?.toString() ?: ""
            crossSellIds = response.cross_sell_ids?.toString() ?: ""
            upsellIds = response.upsell_ids?.toString() ?: ""

            response.dimensions?.asJsonObject?.let { json ->
                length = json.getString("length") ?: ""
                width = json.getString("width") ?: ""
                height = json.getString("height") ?: ""
            }
        }
    }

    private fun productVariationResponseToProductVariationModel(
        site: SiteModel,
        product: WCProductModel,
        response: ProductVariationApiResponse
    ): WCProductVariationModel {
        return WCProductVariationModel(site.id, product.remoteProductId).apply {
            remoteVariationId = response.id
            permalink = response.permalink ?: ""

            dateCreated = response.date_created ?: ""
            dateModified = response.date_modified ?: ""

            status = response.status ?: ""
            description = response.description ?: ""
            sku = response.sku ?: ""

            price = response.price ?: ""
            regularPrice = response.regular_price ?: ""
            salePrice = response.sale_price ?: ""
            onSale = response.on_sale

            virtual = response.virtual
            downloadable = response.downloadable
            purchasable = response.purchasable

            manageStock = response.manage_stock
            stockQuantity = response.stock_quantity
            stockStatus = response.stock_status ?: ""

            attributes = response.attributes?.toString() ?: ""
            image = response.image?.toString() ?: ""

            weight = response.weight ?: ""

            response.dimensions?.asJsonObject?.let { json ->
                length = json.getString("length") ?: ""
                width = json.getString("width") ?: ""
                height = json.getString("height") ?: ""
            }
        }
    }

    private fun networkErrorToProductError(wpComError: WPComGsonNetworkError): ProductError {
        val productErrorType = when (wpComError.apiError) {
            "rest_invalid_param" -> ProductErrorType.INVALID_PARAM
            else -> ProductErrorType.fromString(wpComError.apiError)
        }
        return ProductError(productErrorType, wpComError.message)
    }
}
