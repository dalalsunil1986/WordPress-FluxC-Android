package org.wordpress.android.fluxc.network.rest.wpcom.wc.product

import android.content.Context
import com.android.volley.RequestQueue
import com.google.gson.reflect.TypeToken
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.WCProductAction
import org.wordpress.android.fluxc.generated.WCProductActionBuilder
import org.wordpress.android.fluxc.generated.endpoint.WOOCOMMERCE
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.WCProductModel
import org.wordpress.android.fluxc.model.WCProductReviewModel
import org.wordpress.android.fluxc.model.WCProductVariationModel
import org.wordpress.android.fluxc.network.UserAgent
import org.wordpress.android.fluxc.network.rest.wpcom.BaseWPComRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.WPComErrorListener
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.WPComGsonNetworkError
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken
import org.wordpress.android.fluxc.network.rest.wpcom.jetpacktunnel.JetpackTunnelGsonRequest
import org.wordpress.android.fluxc.network.utils.getString
import org.wordpress.android.fluxc.store.WCProductStore
import org.wordpress.android.fluxc.store.WCProductStore.FetchProductReviewsResponsePayload
import org.wordpress.android.fluxc.store.WCProductStore.ProductError
import org.wordpress.android.fluxc.store.WCProductStore.ProductErrorType
import org.wordpress.android.fluxc.store.WCProductStore.RemoteProductPayload
import org.wordpress.android.fluxc.store.WCProductStore.RemoteProductReviewPayload
import org.wordpress.android.fluxc.store.WCProductStore.RemoteProductVariationsPayload
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
     * Makes a GET request to `/wp-json/wc/v3/products/[remoteProductId]` to fetch a single product
     *
     * Dispatches a WCProductAction.FETCHED_SINGLE_PRODUCT action with the result
     *
     * @param [remoteProductId] Unique server id of the product to fetch
     */
    fun fetchSingleProduct(site: SiteModel, remoteProductId: Long) {
        val url = WOOCOMMERCE.products.id(remoteProductId).pathV3
        val responseType = object : TypeToken<ProductApiResponse>() {}.type
        val params = emptyMap<String, String>()
        val request = JetpackTunnelGsonRequest.buildGetRequest(url, site.siteId, params, responseType,
                { response: ProductApiResponse? ->
                    response?.let {
                        val newModel = productResponseToProductModel(it).apply {
                            localSiteId = site.id
                        }
                        val payload = RemoteProductPayload(newModel, site)
                        dispatcher.dispatch(WCProductActionBuilder.newFetchedSingleProductAction(payload))
                    }
                },
                WPComErrorListener { networkError ->
                    val productError = networkErrorToProductError(networkError)
                    val payload = RemoteProductPayload(
                            productError,
                            WCProductModel().apply { this.remoteProductId = remoteProductId },
                            site
                    )
                    dispatcher.dispatch(WCProductActionBuilder.newFetchedSingleProductAction(payload))
                },
                { request: WPComGsonRequest<*> -> add(request) })
        add(request)
    }

    /**
     * Makes a GET request to `POST /wp-json/wc/v3/products/[productId]/variations` to fetch
     * variations for a product
     *
     * Dispatches a WCProductAction.FETCHED_PRODUCT_VARIATIONS action with the result
     *
     * @param [productId] Unique server id of the product
     */
    fun fetchProductVariations(site: SiteModel, productId: Long) {
        val url = WOOCOMMERCE.products.id(productId).variations.pathV3
        val responseType = object : TypeToken<List<ProductVariationApiResponse>>() {}.type
        val params = emptyMap<String, String>()
        val request = JetpackTunnelGsonRequest.buildGetRequest(url, site.siteId, params, responseType,
                { response: List<ProductVariationApiResponse>? ->
                    val variationModels = response?.map {
                        productVariationResponseToProductVariationModel(it).apply {
                            localSiteId = site.id
                            remoteProductId = productId
                        }
                    }.orEmpty()

                    val payload = RemoteProductVariationsPayload(site, productId, variationModels)
                    dispatcher.dispatch(WCProductActionBuilder.newFetchedProductVariationsAction(payload))
                },
                WPComErrorListener { networkError ->
                    val productError = networkErrorToProductError(networkError)
                    val payload = RemoteProductVariationsPayload(
                            productError,
                            site,
                            productId
                    )
                    dispatcher.dispatch(WCProductActionBuilder.newFetchedProductVariationsAction(payload))
                },
                { request: WPComGsonRequest<*> -> add(request) })
        add(request)
    }

    /**
     * Makes a GET call to `/wc/v3/products/reviews` via the Jetpack tunnel (see [JetpackTunnelGsonRequest]),
     * retrieving a list of product reviews for a given WooCommerce [SiteModel].
     *
     * The number of reviews to fetch is defined in [WCProductStore.NUM_REVIEWS_PER_FETCH], and retrieving older
     * reviews is done by passing an [offset].
     *
     * Dispatches a [WCProductAction.FETCHED_PRODUCT_REVIEWS]
     *
     * @param [site] The site to fetch product reviews for
     * @param [offset] The offset to use for the fetch
     * @param [productIds] Optional. A list of remote product ID's to fetch product reviews for.
     * @param [filterByStatus] Optional. A list of product review statuses to fetch
     */
    fun fetchProductReviews(
        site: SiteModel,
        offset: Int,
        productIds: List<Long>? = null,
        filterByStatus: List<String>? = null
    ) {
        val statusFilter = filterByStatus?.joinToString { it } ?: "all"

        val url = WOOCOMMERCE.products.reviews.pathV3
        val responseType = object : TypeToken<List<ProductReviewApiResponse>>() {}.type
        val params = mutableMapOf(
                "per_page" to WCProductStore.NUM_REVIEWS_PER_FETCH.toString(),
                "offset" to offset.toString(),
                "status" to statusFilter)
        productIds?.let { ids ->
            params.put("product", ids.map { it }.joinToString())
        }
        val request = JetpackTunnelGsonRequest.buildGetRequest(url, site.siteId, params, responseType,
                { response: List<ProductReviewApiResponse>? ->
                    response?.let {
                        val reviews = it.map { review ->
                            productReviewResponseToProductReviewModel(review).apply { localSiteId = site.id }
                        }
                        val canLoadMore = reviews.size == WCProductStore.NUM_REVIEWS_PER_FETCH
                        val loadedMore = offset > 0
                        val payload = FetchProductReviewsResponsePayload(
                                site, reviews, productIds, filterByStatus, loadedMore, canLoadMore)
                        dispatcher.dispatch(WCProductActionBuilder.newFetchedProductReviewsAction(payload))
                    }
                },
                WPComErrorListener { networkError ->
                    val productReviewError = networkErrorToProductError(networkError)
                    val payload = FetchProductReviewsResponsePayload(productReviewError, site)
                    dispatcher.dispatch(WCProductActionBuilder.newFetchedProductReviewsAction(payload))
                },
                { request: WPComGsonRequest<*> -> add(request) })
        add(request)
    }

    /**
     * Makes a GET call to `/wc/v3/products/reviews/<id>` via the Jetpack tunnel (see [JetpackTunnelGsonRequest]),
     * retrieving a product review by it's remote ID for a given WooCommerce [SiteModel].
     *
     * Dispatches a [WCProductAction.FETCHED_SINGLE_PRODUCT_REVIEW]
     *
     * @param [site] The site to fetch product reviews for
     * @param [remoteReviewId] The remote id of the review to fetch
     */
    fun fetchProductReviewById(site: SiteModel, remoteReviewId: Long) {
        val url = WOOCOMMERCE.products.reviews.id(remoteReviewId).pathV3
        val responseType = object : TypeToken<ProductReviewApiResponse>() {}.type
        val params = emptyMap<String, String>()
        val request = JetpackTunnelGsonRequest.buildGetRequest(url, site.siteId, params, responseType,
                { response: ProductReviewApiResponse? ->
                    response?.let {
                        val review = productReviewResponseToProductReviewModel(response).apply {
                            localSiteId = site.id
                        }
                        val payload = RemoteProductReviewPayload(site, review)
                        dispatcher.dispatch(WCProductActionBuilder.newFetchedSingleProductReviewAction(payload))
                    }
                },
                WPComErrorListener { networkError ->
                    val productReviewError = networkErrorToProductError(networkError)
                    val payload = RemoteProductReviewPayload(
                            error = productReviewError,
                            site = site,
                            productReview = WCProductReviewModel()
                                    .apply { remoteProductReviewId = remoteReviewId })
                    dispatcher.dispatch(WCProductActionBuilder.newFetchedSingleProductReviewAction(payload))
                },
                { request: WPComGsonRequest<*> -> add(request) })
        add(request)
    }

    /**
     * Makes a PUT call to `/wc/v3/products/reviews/<id>` via the Jetpack tunnel (see [JetpackTunnelGsonRequest]),
     * updating the status for the given [productReview] to [newStatus].
     *
     * Dispatches a [WCProductAction.UPDATED_PRODUCT_REVIEW_STATUS]
     *
     * @param [site] The site to fetch product reviews for
     * @param [productReview] The [WCProductReviewModel] to be updated
     * @param [newStatus] The new status to update the product review to
     */
    fun updateProductReviewStatus(site: SiteModel, productReview: WCProductReviewModel, newStatus: String) {
        val url = WOOCOMMERCE.products.reviews.id(productReview.remoteProductReviewId).pathV3
        val responseType = object : TypeToken<ProductReviewApiResponse>() {}.type
        val params = mapOf("status" to newStatus)
        val request = JetpackTunnelGsonRequest.buildPutRequest(url, site.siteId, params, responseType,
                { response: ProductReviewApiResponse? ->
                    response?.let {
                        val review = productReviewResponseToProductReviewModel(response).apply {
                            localSiteId = site.id
                        }
                        val payload = RemoteProductReviewPayload(site, review)
                        dispatcher.dispatch(WCProductActionBuilder.newUpdatedProductReviewStatusAction(payload))
                    }
                },
                WPComErrorListener { networkError ->
                    val productReviewError = networkErrorToProductError(networkError)
                    val payload = RemoteProductReviewPayload(productReviewError, site, productReview)
                    dispatcher.dispatch(WCProductActionBuilder.newUpdatedProductReviewStatusAction(payload))
                })
        add(request)
    }

    private fun productResponseToProductModel(response: ProductApiResponse): WCProductModel {
        return WCProductModel().apply {
            remoteProductId = response.id ?: 0
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
            downloadable = response.downloadable
            downloadLimit = response.download_limit
            downloadExpiry = response.download_expiry
            externalUrl = response.external_url ?: ""

            taxStatus = response.tax_status ?: ""
            taxClass = response.tax_class ?: ""

            // variations may have "parent" here if inventory is enabled for the parent but not the variation
            manageStock = response.manage_stock?.let {
                it == "true" || it == "parent"
            } ?: false

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
        response: ProductVariationApiResponse
    ): WCProductVariationModel {
        return WCProductVariationModel().apply {
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

            weight = response.weight ?: ""

            response.dimensions?.asJsonObject?.let { json ->
                length = json.getString("length") ?: ""
                width = json.getString("width") ?: ""
                height = json.getString("height") ?: ""
            }

            response.image?.asJsonObject?.let { json ->
                imageUrl = json.getString("src") ?: ""
            }
        }
    }

    private fun productReviewResponseToProductReviewModel(response: ProductReviewApiResponse): WCProductReviewModel {
        return WCProductReviewModel().apply {
            remoteProductReviewId = response.id
            remoteProductId = response.product_id
            dateCreated = response.date_created ?: ""
            status = response.status ?: ""
            reviewerName = response.reviewer ?: ""
            reviewerEmail = response.reviewer_email ?: ""
            review = response.review ?: ""
            rating = response.rating
            verified = response.verified
            reviewerAvatarsJson = response.reviewer_avatar_urls?.toString() ?: ""
        }
    }

    private fun networkErrorToProductError(wpComError: WPComGsonNetworkError): ProductError {
        val productErrorType = when (wpComError.apiError) {
            "rest_invalid_param" -> ProductErrorType.INVALID_PARAM
            "woocommerce_rest_review_invalid_id" -> ProductErrorType.INVALID_REVIEW_ID
            else -> ProductErrorType.fromString(wpComError.apiError)
        }
        return ProductError(productErrorType, wpComError.message)
    }
}
