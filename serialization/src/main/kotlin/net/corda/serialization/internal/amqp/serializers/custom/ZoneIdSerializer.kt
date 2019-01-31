package net.corda.serialization.internal.amqp.serializers.custom

import net.corda.core.KeepForDJVM
import net.corda.serialization.internal.amqp.serializers.CustomSerializer
import net.corda.serialization.internal.amqp.api.SerializerFactory
import java.time.ZoneId

/**
 * A serializer for [ZoneId] that uses a proxy object to write out the string form.
 */
class ZoneIdSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<ZoneId, ZoneIdSerializer.ZoneIdProxy>(ZoneId::class.java, ZoneIdProxy::class.java, factory) {
    override val revealSubclassesInSchema: Boolean = true

    override fun toProxy(obj: ZoneId): ZoneIdProxy = ZoneIdProxy(obj.id)

    override fun fromProxy(proxy: ZoneIdProxy): ZoneId = ZoneId.of(proxy.id)

    @KeepForDJVM
    data class ZoneIdProxy(val id: String)
}