package com.nujiak.recce.database

import android.os.Parcelable
import androidx.room.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Superclass for Pin and Chain containing the common properties.
 */
sealed class RecceData {
    abstract val name: String
    abstract val color: Int
    abstract val group: String
    abstract val description: String
}

/**
 * Represents a point on the map defined by its latitude and longitude
 *
 * @property name Pin name
 * @property latitude WGS84 latitude
 * @property longitude WGS84 longitude
 * @property color Pin color
 * @property pinId Unique pin ID
 * @property group Pin group
 * @property description Pin description
 */
@Parcelize
@Serializable(with = PinSerializer::class)
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

) : Parcelable, RecceData()

/**
 * Represents a chain which can be a line (route) or polygon (area). Contains a list of ChainNodes
 * each representing a point.
 *
 * @property name Chain name
 * @property nodes List of ChainNodes making up the line/polygon
 * @property color Chain color
 * @property chainId Unique chain ID
 * @property group Chain group
 * @property cyclical Whether the Chain is a polygon
 * @property description Chain description
 */
@Parcelize
@Serializable(with = ChainSerializer::class)
@TypeConverters(ChainConverters::class)
@Entity(tableName = "chains_table")
data class Chain(

    override val name: String,

    @ColumnInfo(name = "data")
    val nodes: List<ChainNode>,

    override val color: Int = 0,

    @PrimaryKey(autoGenerate = true)
    val chainId: Long = 0L,

    @ColumnInfo(defaultValue = "")
    override val group: String = "",

    @ColumnInfo(defaultValue = "0")
    val cyclical: Boolean = false,

    @ColumnInfo(defaultValue = "")
    override val description: String = ""

) : Parcelable, RecceData() {
    init {
        // Set parentChain of each every node to this
        for (node in this.nodes) {
            node.parentChain = this
        }
    }
}

/**
 * Deserializes a list of ChainNodes from a String.
 *
 * Used in ChainConverter and for deserializing Chain data for sharing.
 *
 * @param data string to be deserialized
 * @return list of ChainNodes deserialized from String
 */
fun deserializeNodeList(data: String) : List<ChainNode> {
    val dataList = data.split(';')
    val nodeList = mutableListOf<ChainNode>()

    for (item in dataList) {
        if (item.isNotBlank()) {
            val (lat, lng, name) = item.split(',')
            val node = ChainNode(
                name = name,
                position = LatLng(lat.toDouble(), lng.toDouble()),
            )
            nodeList.add(node)
        }
    }
    return nodeList.toList()
}

/**
 * Serializes a list of ChainNodes to a String.
 *
 * Used in ChainConverter and for serializing Chain data for sharing
 *
 * @param list list of ChainNodes to be serialized
 * @return string serialization of list
 */
fun serializeNodeList(list: List<ChainNode>) : String {
    val newDataBuilder = StringBuilder()

    for (node in list) {
        newDataBuilder.append(node.position.latitude)
        newDataBuilder.append(',')
        newDataBuilder.append(node.position.longitude)
        newDataBuilder.append(',')
        newDataBuilder.append(node.name.trim())
        newDataBuilder.append(';')
    }

    return newDataBuilder.toString()
}

/**
 * Android Room TypeConverter for Chain
 */
class ChainConverters {

    @TypeConverter
    fun fromNodeList(list : List<ChainNode>) = serializeNodeList(list)

    @TypeConverter
    fun toNodeList(str : String) = deserializeNodeList(str)
}