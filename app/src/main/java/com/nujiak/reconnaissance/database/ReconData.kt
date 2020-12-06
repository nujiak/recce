package com.nujiak.reconnaissance.database

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nujiak.reconnaissance.utils.round
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

sealed class ReconData {
    abstract val name: String
    abstract val color: Int
    abstract val group: String
    abstract val description: String
}

/**
 * Represents a point on the map defined by its latitude and longitude
 */
@Parcelize
@Serializable (with = PinSerializer::class)
@Entity(tableName = "pins_table")
data class Pin(

    override val name: String,

    val latitude: Double,

    val longitude: Double,

    override val color: Int = 0,

    @PrimaryKey(autoGenerate = true)
    val pinId: Long = 0L,

    @ColumnInfo(defaultValue = "")
    override val group: String = "",

    @ColumnInfo(defaultValue = "")
    override val description: String = ""

) : Parcelable, ReconData()

@Parcelize
@Serializable (with = ChainSerializer::class)
@Entity(tableName = "chains_table")
data class Chain(

    override val name: String,

    val data: String,

    override val color: Int = 0,

    @PrimaryKey(autoGenerate = true)
    val chainId: Long = 0L,

    @ColumnInfo(defaultValue = "")
    override val group: String = "",

    @ColumnInfo(defaultValue = "0")
    val cyclical: Boolean = false,

    @ColumnInfo(defaultValue = "")
    override val description: String = ""

) : Parcelable, ReconData()

@Serializable
private data class PinSurrogate(
    val n: String, val lt: Double, val lg: Double,
    val c: Int, val g: String, val d: String
)

@Serializable
private data class ChainSurrogate(
    val n: String, val dt: String, val c: Int,
    val g: String, val cy: Int, val d: String
)

fun toPinsAndChains(shareCode: String): Pair<List<Pin>, List<Chain>> {
    return try {
        val pins = Json.decodeFromString<List<Pin>>(shareCode.substringBefore('\n'))
        val chains = Json.decodeFromString<List<Chain>>(shareCode.substringAfter('\n'))
        Pair(pins, chains)
    } catch (e: Exception) {
        Pair(listOf(), listOf())
    }
}

fun toShareCode(pins: List<Pin>?, chains: List<Chain>?): String {
    val shareCodeBuilder = StringBuilder().apply {
        append(Json.encodeToString(pins))
        append("\n")
        append(Json.encodeToString(chains))
    }

    return shareCodeBuilder.toString()
}

private object PinSerializer : KSerializer<Pin> {
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
}

private object ChainSerializer : KSerializer<Chain> {
    override val descriptor: SerialDescriptor = ChainSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Chain) {
        val surrogate = ChainSurrogate(
            n = value.name,
            dt = value.data,
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
            data = surrogate.dt,
            color = surrogate.c,
            group = surrogate.g,
            cyclical = surrogate.cy != 0,
            description = surrogate.d
        )
    }
}