package org.wordpress.android.fluxc.model

import org.wordpress.android.fluxc.model.SiteModel.ORIGIN_WPCOM_REST

class WCSimpleSiteModel(
    val siteId: Long,
    val url: String,
    val name: String
) {
    fun toSiteModel() = SiteModel().also { site ->
        site.siteId = siteId
        site.url = url
        site.name = name
        site.setIsJetpackConnected(true)
        site.origin = ORIGIN_WPCOM_REST
    }
}
