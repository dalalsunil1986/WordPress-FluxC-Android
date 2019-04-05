package org.wordpress.android.fluxc.mocked

import org.greenrobot.eventbus.Subscribe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.TestUtils
import org.wordpress.android.fluxc.action.WCProductAction
import org.wordpress.android.fluxc.annotations.action.Action
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.module.ResponseMockingInterceptor
import org.wordpress.android.fluxc.network.rest.wpcom.wc.product.ProductRestClient
import org.wordpress.android.fluxc.persistence.ProductSqlUtils
import org.wordpress.android.fluxc.store.WCProductStore.RemoteProductPayload
import org.wordpress.android.fluxc.store.WCProductStore.RemoteProductVariationPayload
import org.wordpress.android.fluxc.store.WCProductStore.RemoteProductVariationsPayload
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.properties.Delegates.notNull

class MockedStack_WCProductsTest : MockedStack_Base() {
    @Inject internal lateinit var productRestClient: ProductRestClient
    @Inject internal lateinit var dispatcher: Dispatcher

    @Inject internal lateinit var interceptor: ResponseMockingInterceptor

    private var lastAction: Action<*>? = null
    private var countDownLatch: CountDownLatch by notNull()

    private val remoteProductId = 1537L
    private val variationId = 193L

    private val siteModel = SiteModel().apply {
        id = 5
        siteId = 567
    }

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        mMockedNetworkAppComponent.inject(this)
        dispatcher.register(this)
        lastAction = null
    }

    @Test
    fun testFetchSingleProductSuccess() {
        interceptor.respondWith("wc-fetch-product-response-success.json")
        productRestClient.fetchSingleProduct(siteModel, remoteProductId)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))

        assertEquals(WCProductAction.FETCHED_SINGLE_PRODUCT, lastAction!!.type)
        val payload = lastAction!!.payload as RemoteProductPayload
        with(payload) {
            assertNull(error)
            assertEquals(remoteProductId, product.remoteProductId)
            assertEquals(product.getCategories().size, 2)
            assertEquals(product.getTags().size, 2)
            assertEquals(product.getImages().size, 2)
            assertNotNull(product.getFirstImageUrl())
            assertEquals(product.getAttributes().size, 2)
            assertEquals(product.getAttributes()[0].options.size, 3)
            assertEquals(product.getAttributes()[0].getCommaSeparatedOptions(), "Small, Medium, Large")
        }

        // save the product to the db
        assertEquals(ProductSqlUtils.insertOrUpdateProduct(payload.product), 1)

        // now verify the db stored the product correctly
        val productFromDb = ProductSqlUtils.getProductByRemoteId(siteModel, remoteProductId)
        assertNotNull(productFromDb)
        productFromDb?.let { product ->
            assertEquals(product.remoteProductId, remoteProductId)
            assertEquals(product.getCategories().size, 2)
            assertEquals(product.getTags().size, 2)
            assertEquals(product.getImages().size, 2)
            assertNotNull(product.getFirstImageUrl())
            assertEquals(product.getAttributes().size, 2)
            assertEquals(product.getAttributes()[0].options.size, 3)
            assertEquals(product.getAttributes()[0].getCommaSeparatedOptions(), "Small, Medium, Large")
        }
    }

    @Test
    fun testFetchSingleProductError() {
        interceptor.respondWithError("jetpack-tunnel-root-response-failure.json")
        productRestClient.fetchSingleProduct(siteModel, remoteProductId)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))

        assertEquals(WCProductAction.FETCHED_SINGLE_PRODUCT, lastAction!!.type)
        val payload = lastAction!!.payload as RemoteProductPayload
        assertNotNull(payload.error)
    }

    @Test
    fun testFetchSingleProductVariationSuccess() {
        interceptor.respondWith("wc-fetch-product-single-variation-response-success.json")
        productRestClient.fetchSingleProductVariation(siteModel, remoteProductId, variationId)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))

        assertEquals(WCProductAction.FETCHED_SINGLE_PRODUCT_VARIATION, lastAction!!.type)
        val payload = lastAction!!.payload as RemoteProductVariationPayload
        assertNull(payload.error)
        assertEquals(payload.remoteProductId, remoteProductId)
        assertNotNull(payload.variation)

        // save the variation to the db
        assertEquals(ProductSqlUtils.insertOrUpdateProductVariation(payload.variation!!), 1)

        // now delete all variations for this product and save again
        ProductSqlUtils.deleteVariationsForProduct(siteModel, remoteProductId)
        assertEquals(ProductSqlUtils.insertOrUpdateProductVariation(payload.variation!!), 1)

        // verify the db stored only this variation
        val variations = ProductSqlUtils.getVariationsForProduct(siteModel, remoteProductId)
        assertEquals(variations.size, 1)

        // verify single variation stored correctly
        val variation = ProductSqlUtils.getProductVariation(siteModel, remoteProductId, variationId)
        assertNotNull(variation)
        variation?.let {
            assertEquals(it.remoteProductId, remoteProductId)
            assertEquals(it.remoteVariationId, variationId)
            assertEquals(it.localSiteId, siteModel.id)
        }
    }

    @Test
    fun testFetchSingleProductVariationError() {
        interceptor.respondWithError("jetpack-tunnel-root-response-failure.json")
        productRestClient.fetchSingleProductVariation(siteModel, remoteProductId, variationId)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))

        assertEquals(WCProductAction.FETCHED_SINGLE_PRODUCT_VARIATION, lastAction!!.type)
        val payload = lastAction!!.payload as RemoteProductVariationPayload
        assertNotNull(payload.error)
    }

    @Test
    fun testFetchProductVariationsSuccess() {
        interceptor.respondWith("wc-fetch-product-variations-response-success.json")
        productRestClient.fetchProductVariations(siteModel, remoteProductId)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))

        assertEquals(WCProductAction.FETCHED_PRODUCT_VARIATIONS, lastAction!!.type)
        val payload = lastAction!!.payload as RemoteProductVariationsPayload
        assertNull(payload.error)
        assertEquals(payload.remoteProductId, remoteProductId)
        assertEquals(payload.variations.size, 3)

        // save the variations to the db
        assertEquals(ProductSqlUtils.insertOrUpdateProductVariations(payload.variations), 3)

        // now delete all variations for this product and save again
        ProductSqlUtils.deleteVariationsForProduct(siteModel, remoteProductId)
        assertEquals(ProductSqlUtils.insertOrUpdateProductVariations(payload.variations), 3)

        // now verify the db stored the variations correctly
        val dbVariations = ProductSqlUtils.getVariationsForProduct(siteModel, remoteProductId)
        assertEquals(dbVariations.size, 3)
        with(dbVariations.first()) {
            assertEquals(this.remoteProductId, remoteProductId)
            assertEquals(this.localSiteId, siteModel.id)
        }
    }

    @Test
    fun testFetchProductVariationsError() {
        interceptor.respondWithError("jetpack-tunnel-root-response-failure.json")
        productRestClient.fetchProductVariations(siteModel, remoteProductId)

        countDownLatch = CountDownLatch(1)
        assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))

        assertEquals(WCProductAction.FETCHED_PRODUCT_VARIATIONS, lastAction!!.type)
        val payload = lastAction!!.payload as RemoteProductVariationsPayload
        assertNotNull(payload.error)
    }

    @Suppress("unused")
    @Subscribe
    fun onAction(action: Action<*>) {
        lastAction = action
        countDownLatch.countDown()
    }
}
