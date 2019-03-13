package org.wordpress.android.fluxc.model

class WCSimpleSiteModel(
    val siteId: Long,
    val url: String,
    val name: String
) {
    /**
     * Converts this simple model to a SiteModel, filling in only the information we have. Note that we must set the
     * origin to REST and isJetpackConnected to True or else requests using this site model will use XmlRpc (which will
     * fail). We're safe to do this since the WooCommerce app requires Jetpack, but it does mean we shouldn't use this
     * model outside of WCAndroid
     */
    fun toSiteModel() = SiteModel().also { site ->
        site.siteId = siteId
        site.url = url
        site.name = name
        site.setIsJetpackConnected(true)
        site.origin = SiteModel.ORIGIN_WPCOM_REST
    }
}
