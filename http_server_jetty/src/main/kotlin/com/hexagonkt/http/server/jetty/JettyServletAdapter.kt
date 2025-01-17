package com.hexagonkt.http.server.jetty

import com.hexagonkt.core.fail
import com.hexagonkt.core.fieldsMapOf
import com.hexagonkt.http.model.HttpProtocol
import com.hexagonkt.http.model.HttpProtocol.HTTP
import com.hexagonkt.http.model.HttpProtocol.HTTP2
import com.hexagonkt.http.model.HttpProtocol.HTTPS
import com.hexagonkt.http.server.servlet.ServletFilter
import com.hexagonkt.http.server.HttpServer
import com.hexagonkt.http.server.HttpServerFeature
import com.hexagonkt.http.server.HttpServerFeature.ASYNC
import com.hexagonkt.http.server.HttpServerFeature.ZIP
import com.hexagonkt.http.server.HttpServerPort
import com.hexagonkt.http.server.HttpServerSettings
import com.hexagonkt.http.server.handlers.PathHandler
import com.hexagonkt.http.server.handlers.path
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletContextHandler.NO_SESSIONS
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.QueuedThreadPool
import java.security.KeyStore
import java.util.EnumSet
import jakarta.servlet.DispatcherType
import org.eclipse.jetty.server.Server as JettyServer

/**
 * Implements [HttpServerPort] using [JettyServer].
 */
class JettyServletAdapter(
    private val maxThreads: Int = 200,
    private val minThreads: Int = 8,

    private val acceptors: Int = -1,
    private val selectors: Int = -1,

    private val sendDateHeader: Boolean = false,
    private val sendServerVersion: Boolean = false,
    private val sendXPoweredBy: Boolean = false,

) : HttpServerPort {
    private var jettyServer: JettyServer? = null

    constructor() : this(
        maxThreads = 200,
        minThreads = 8,

        acceptors = -1,
        selectors = -1,

        sendDateHeader = false,
        sendServerVersion = false,
        sendXPoweredBy = false,
    )

    override fun runtimePort(): Int =
        ((jettyServer?.connectors?.get(0) ?: fail) as ServerConnector).localPort

    override fun started() =
        jettyServer?.isStarted ?: false

    override fun startUp(server: HttpServer) {
        val settings = server.settings
        val serverInstance = JettyServer(QueuedThreadPool(maxThreads, minThreads))
        jettyServer = serverInstance

        val pathHandler: PathHandler = path(settings.contextPath, server.handlers)

        val context = createServerContext(settings)
        val filter = ServletFilter(pathHandler, settings)
        val filterBind = context.servletContext.addFilter("filters", filter)
        val dispatcherTypes = EnumSet.allOf(DispatcherType::class.java)
        filterBind.setAsyncSupported(settings.features.contains(ASYNC))
        filterBind.addMappingForUrlPatterns(dispatcherTypes, true, "/*")

        val serverConnector =
            if (settings.sslSettings != null) setupSsl(settings, serverInstance)
            else ServerConnector(serverInstance, acceptors, selectors)

        serverConnector.host = settings.bindAddress.hostName
        serverConnector.port = settings.bindPort
        serverConnector.connectionFactories
            .filterIsInstance(HttpConnectionFactory::class.java)
            .map { it.httpConfiguration }
            .map {
                it.sendDateHeader = sendDateHeader
                it.sendServerVersion = sendServerVersion
                it.sendXPoweredBy = sendXPoweredBy
            }

        serverInstance.connectors = arrayOf(serverConnector)
        serverInstance.handler = context
        serverInstance.stopAtShutdown = true
        serverInstance.start()
    }

    override fun shutDown() {
        jettyServer?.stop()
    }

    override fun supportedProtocols(): Set<HttpProtocol> =
        setOf(HTTP, HTTPS, HTTP2)

    override fun supportedFeatures(): Set<HttpServerFeature> =
        setOf(ZIP, ASYNC)

    override fun options(): Map<String, *> =
        fieldsMapOf(
            JettyServletAdapter::maxThreads to maxThreads,
            JettyServletAdapter::minThreads to minThreads,
            JettyServletAdapter::acceptors to acceptors,
            JettyServletAdapter::selectors to selectors,
            JettyServletAdapter::sendDateHeader to sendDateHeader,
            JettyServletAdapter::sendServerVersion to sendServerVersion,
            JettyServletAdapter::sendXPoweredBy to sendXPoweredBy,
        )

    private fun createServerContext(settings: HttpServerSettings): ServletContextHandler {

        val features = settings.features
        val context = ServletContextHandler(NO_SESSIONS)

        if (features.contains(ZIP))
            context.insertHandler(GzipHandler())

        return context
    }

    private fun setupSsl(
        settings: HttpServerSettings, serverInstance: JettyServer): ServerConnector {

        val httpConfiguration = HttpConfiguration()
        httpConfiguration.secureScheme = "https"
        httpConfiguration.securePort = settings.bindPort
        httpConfiguration.addCustomizer(SecureRequestCustomizer())

        val sslContextFactory = SslContextFactory.Server()
        val sslSettings = settings.sslSettings ?: fail
        sslContextFactory.needClientAuth = sslSettings.clientAuth

        val keyStore = sslSettings.keyStore
        if (keyStore != null) {
            val keyStorePassword = sslSettings.keyStorePassword
            val keyStoreStream = keyStore.openStream()
            sslContextFactory.keyStore = KeyStore.getInstance("pkcs12")
            sslContextFactory.keyStore.load(keyStoreStream, keyStorePassword.toCharArray())
            sslContextFactory.setKeyStorePassword(keyStorePassword)
        }

        val trustStore = sslSettings.trustStore
        if (trustStore != null) {
            val trustStorePassword = sslSettings.trustStorePassword
            val trustStoreStream = trustStore.openStream()
            sslContextFactory.trustStore = KeyStore.getInstance("pkcs12")
            sslContextFactory.trustStore.load(trustStoreStream, trustStorePassword.toCharArray())
            sslContextFactory.setTrustStorePassword(trustStorePassword)
        }

        return if (settings.protocol != HTTP2)
            ServerConnector(
                serverInstance,
                SslConnectionFactory(sslContextFactory, "http/1.1"),
                HttpConnectionFactory(httpConfiguration)
            )
        else {
            val alpn = ALPNServerConnectionFactory()
            ServerConnector(
                serverInstance,
                SslConnectionFactory(sslContextFactory, alpn.protocol),
                alpn,
                HTTP2ServerConnectionFactory(httpConfiguration),
                HttpConnectionFactory(httpConfiguration)
            )
        }
    }
}
