package co.there4.hexagon.rest.ratpack

import ratpack.registry.Registry
import ratpack.registry.RegistrySpec
import ratpack.server.RatpackServerSpec
import ratpack.server.ServerConfigBuilder

class KServerSpec(val delegate: RatpackServerSpec) : RatpackServerSpec by delegate {
    fun serverConfig(cb: ServerConfigBuilder.() -> Unit) = delegate.serverConfig { it.(cb)() }
    fun registry(cb: RegistrySpec.() -> Unit) = delegate.registry (Registry.of(cb))
    fun handlers(cb: KChain.() -> Unit) = delegate.handlers { KChain(it).(cb)() }
}