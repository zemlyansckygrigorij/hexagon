package com.hexagonkt.http.server

import com.hexagonkt.http.model.HttpProtocol.H2C
import com.hexagonkt.http.server.HttpServerFeature.ASYNC
import com.hexagonkt.http.server.HttpServerFeature.ZIP
import com.hexagonkt.http.server.handlers.PathHandler
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import java.net.InetAddress.getByName as address

internal class HttpServerTest {

    @Test fun `Default banner includes documentation URL`() {
        assert(HttpServer.banner.contains("https://hexagonkt.com/http_server"))
    }

    @Test fun `Default parameters`() {
        val serverSettings = HttpServerSettings(address("localhost"), 9999, banner = "name")
        val server = HttpServer(VoidAdapter, listOf(PathHandler()), serverSettings)

        assertEquals("name", server.settings.banner)
        assertEquals(VoidAdapter.javaClass.simpleName, server.portName)
        assertEquals(address("localhost"), server.settings.bindAddress)
        assertEquals(9999, server.settings.bindPort)
    }

    @Test fun `Runtime port`() {
        val serverSettings = HttpServerSettings(address("localhost"), 9999, banner = "name")
        val server = HttpServer(VoidAdapter, listOf(PathHandler()), serverSettings)

        assertFailsWith<IllegalStateException>("Server is not running") { server.runtimePort }
        assert(!server.started())

        server.start()

        assert(server.started())
        assertEquals(12345, server.runtimePort)
    }

    @Test fun `Banner creation`() {
        val bannerPrefix = "Test Banner"
        val serverSettings = HttpServerSettings(
            address("localhost"),
            12345,
            banner = bannerPrefix,
        )

        val server = serve(VoidAdapter, serverSettings) {}
        val createdBanner = server.createBanner(System.currentTimeMillis())
        server.stop()

        assertEquals(bannerPrefix, createdBanner.lines()[0].trimIndent())
        assertContains(createdBanner, "✅HTTP" )
        assertContains(createdBanner, "HTTPS")
        assertContains(createdBanner, "ASYNC")
        assertFalse(createdBanner.contains("ZIP"))
        assertFalse(createdBanner.contains("✅HTTPS"))
        assertFalse(createdBanner.contains("✅ASYNC"))
    }

    @Test fun `Banner creation with enabled features and custom options`() {
        val serverSettings = HttpServerSettings(
            address("localhost"),
            12345,
            features = setOf(ASYNC),
        )

        val server = serve(VoidAdapter, serverSettings) {}
        val createdBanner = server.createBanner(System.currentTimeMillis())
        server.stop()

        assertContains(createdBanner, "✅HTTP" )
        assertContains(createdBanner, "HTTPS")
        assertContains(createdBanner, "✅ASYNC")
        assertContains(createdBanner, "option1(1)")
        assertContains(createdBanner, "option2(2)")
        assertFalse(createdBanner.contains("ZIP"))
        assertFalse(createdBanner.contains("✅HTTPS"))
    }

    @Test fun `Server can not be created with features or options not supported by its adapter`() {
        val handlers = emptyList<PathHandler>()

        assertFailsWith<IllegalStateException> {
            HttpServer(VoidAdapter, handlers, HttpServerSettings(features = setOf(ZIP)))
        }.let {
            assertContains(it.message ?: "", "Requesting unsupported feature. Adapter's features:")
        }
    }

    @Test fun `Server can not be created with a protocol not supported by its adapter`() {
        val handlers = emptyList<PathHandler>()

        assertFailsWith<IllegalStateException> {
            HttpServer(VoidAdapter, handlers, HttpServerSettings(protocol = H2C))
        }.let {
            val message = it.message ?: ""
            assertContains(message, "Requesting unsupported protocol. Adapter's protocols:")
        }
    }
}
