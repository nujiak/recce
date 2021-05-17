package com.nujiak.recce.database

import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng
import kotlinx.parcelize.Parcelize

/**
 * Represents a point on the map which makes up a line or polygon.
 *
 * @property name Checkpoint name, empty for regular node
 * @property position Node location
 * @property parentChain Chain containing this node
 */
@Parcelize
data class ChainNode(
    val name: String,
    val position: LatLng,
    var parentChain: Chain? = null
) : Parcelable {
    val isCheckpoint: Boolean
        get() = name.isNotBlank()

    /**
     * Overriden with a reference comparison for parentChain to prevent an infinite loop.
     *
     * @param other other object to be compared
     * @return true if the two objects are equal in value
     */
    override fun equals(other: Any?): Boolean {
        if (other !is ChainNode) {
            return false
        }
        return this.name == other.name &&
                this.position == other.position &&
                this.parentChain === other.parentChain
    }
}