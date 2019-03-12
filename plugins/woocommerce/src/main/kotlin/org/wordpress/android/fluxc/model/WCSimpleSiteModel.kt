package org.wordpress.android.fluxc.model

class WCSimpleSiteModel(
    val siteId: Long,
    val url: String,
    val name: String
) {
    companion object {
        fun fromSiteModel(site: SiteModel): WCSimpleSiteModel = WCSimpleSiteModel(site.siteId, site.url, site.name)
    }

    fun toSiteModel(): SiteModel = SiteModel().also { site ->
        site.siteId = siteId
        site.url = url
        site.name = name
    }
}
