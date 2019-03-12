package org.wordpress.android.fluxc.network.rest.wpcom.wc

import org.wordpress.android.fluxc.network.Response

@Suppress("PropertyName")
class WooSiteApiResponse : Response {
    inner class Options {
        var woocommerce_is_active: Boolean = false
    }

    val ID: Long? = null
    var options: Options? = null
}

class WooSitesResponse {
    var sites: List<WooSiteApiResponse>? = null
}
