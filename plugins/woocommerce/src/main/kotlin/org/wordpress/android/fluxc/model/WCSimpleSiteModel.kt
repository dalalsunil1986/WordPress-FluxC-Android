package org.wordpress.android.fluxc.model

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
        site.origin = SiteModel.ORIGIN_WPCOM_REST
    }
}
