package com.nujiak.recce.database

import com.nujiak.recce.utils.round
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/*
Static references to serializers for encoding and decoding
 */
private val pinListSerializer = ListSerializer(Pin.serializer())
private val chainListSerializer = ListSerializer(Chain.serializer())
private val pairListsSerializer = PairSerializer(pinListSerializer, chainListSerializer)

/**
 * Deserializes a Share Code into a pair of Lists.
 *
 * @param shareCode Share Code to be deserializes
 * @return Pair of Lists, List<Pin> and List<Chain> deserialized from the Share Code
 */
fun toPinsAndChains(shareCode: String): Pair<List<Pin>, List<Chain>>? {
    return try {
        Json.decodeFromString(pairListsSerializer, shareCode)
    } catch (e: Exception) {
        Pair(listOf(), listOf())
    }
}

/**
 * Serializes a List of Pins and a List of Chains into a Share Code
 *
 * @param pins List of Pins
 * @param chains List of Chains
 * @return Serialized Share Code
 */
fun toShareCode(pins: List<Pin>?, chains: List<Chain>?): String {
    return Json.encodeToString(pairListsSerializer, Pair(pins ?: listOf(), chains ?: listOf()))
}

/**
 * Serializer object for Pin, using PinSurrogate.
 */
object PinSerializer : KSerializer<Pin> {

    override val descriptor: SerialDescriptor = PinSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Pin) {
        val surrogate = PinSurrogate(
            n = value.name,
            lt = value.latitude.round(6),
            lg = value.longitude.round(6),
            c = value.color,
            g = value.group,
            d = value.description
        )
        encoder.encodeSerializableValue(PinSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): Pin {
        val surrogate = decoder.decodeSerializableValue(PinSurrogate.serializer())
        return Pin(
            name = surrogate.n,
            latitude = surrogate.lt,
            longitude = surrogate.lg,
            color = surrogate.c,
            group = surrogate.g,
            description = surrogate.d
        )
    }

    /**
     * Pin surrogate class for serializing into JSON. Corresponds to Pin but with shorter
     * member names for compactness.
     *
     * Used for Share Code.
     *
     * @property n name
     * @property lt latitude
     * @property lg longitude
     * @property c color
     * @property g group
     * @property d description
     */
    @Serializable
    private data class PinSurrogate(
        val n: String,
        val lt: Double,
        val lg: Double,
        val c: Int,
        val g: String,
        val d: String
    )
}

/**
 * Serializer for Chain, using ChainSurrogate
 */
object ChainSerializer : KSerializer<Chain> {
    override val descriptor: SerialDescriptor = ChainSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Chain) {
        val surrogate = ChainSurrogate(
            n = value.name,
            dt = serializeNodeList(value.nodes),
            c = value.color,
            g = value.group,
            cy = if (value.cyclical) 1 else 0,
            d = value.description
        )
        encoder.encodeSerializableValue(ChainSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): Chain {
        val surrogate = decoder.decodeSerializableValue(ChainSurrogate.serializer())
        return Chain(
            name = surrogate.n,
            nodes = deserializeNodeList(surrogate.dt),
            color = surrogate.c,
            group = surrogate.g,
            cyclical = surrogate.cy != 0,
            description = surrogate.d
        )
    }

    /**
     * Chain surrogate class for serializing into JSON. Corresponds to Chain but with shorter
     * member names for compactness.
     *
     * Used for Share Code.
     *
     * @property n name
     * @property dt data
     * @property c color
     * @property g group
     * @property cy cyclical
     * @property d description
     */
    @Serializable
    private data class ChainSurrogate(
        val n: String,
        val dt: String,
        val c: Int,
        val g: String,
        val cy: Int,
        val d: String
    )
}
