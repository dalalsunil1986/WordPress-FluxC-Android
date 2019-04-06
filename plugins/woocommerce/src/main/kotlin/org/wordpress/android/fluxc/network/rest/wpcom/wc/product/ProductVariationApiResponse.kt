package org.wordpress.android.fluxc.network.rest.wpcom.wc.product

import com.google.gson.JsonElement
import org.wordpress.android.fluxc.network.Response

@Suppress("PropertyName")
class ProductVariationApiResponse : Response {
    var id: Long = 0L

    var description: String? = null
    var permalink: String? = null
    var sku: String? = null
    var status: String? = null
    var price: String? = null
    var regular_price: String? = null
    var sale_price: String? = null

    var date_created: String? = null
    var date_modified: String? = null

    var on_sale = false
    var purchasable = false
    var virtual = false

    var downloadable = false
    var download_limit = 0
    var download_expiry = 0

    var manage_stock = false
    var stock_quantity = 0
    var stock_status: String? = null

    var shipping_class: String? = null
    var shipping_class_id = 0

    var backorders: String? = null
    var backorders_allowed = false
    var backordered = false

    var tax_status: String? = null
    var tax_class: String? = null

    var image: JsonElement? = null

    var weight: String? = null
    var dimensions: JsonElement? = null
    var attributes: JsonElement? = null
}
