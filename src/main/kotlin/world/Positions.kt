package org.koremc.world

import kotlin.math.floor
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    /** The block this point is inside, rounding each coordinate down, so -0.5 is in block -1. */
    fun toBlockPos() = BlockPos(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())

    /** How far it is from [other], in a straight line. */
    fun distanceTo(other: Vec3): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

data class BlockPos(val x: Int, val y: Int, val z: Int) {
    /** The chunk column this block is in. */
    val chunk get() = ChunkPos(x shr 4, z shr 4)
}

/** A chunk column's position: the block coordinates of its corner, divided by 16. */
data class ChunkPos(val x: Int, val z: Int)
