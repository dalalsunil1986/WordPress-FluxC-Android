package org.wordpress.android.fluxc.network.rest.wpcom.wc

import org.wordpress.android.fluxc.network.Response

@Suppress("PropertyName")
class WooSimpleSiteApiResponse : Response {
    inner class Options {
        var woocommerce_is_active: Boolean = false
    }

    val ID: Long? = null
    val URL: String? = null
    val name: String? = null
    var options: Options? = null
}

class WooSimpleSitesResponse {
    var sites: List<WooSimpleSiteApiResponse>? = null
}
