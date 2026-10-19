package org.koremc.protocol

/**
 * The versions of the game Kore speaks, oldest first, each by its protocol
 * number. A client on any of them can join, and Kore writes and reads its
 * packets as its version lays them out, as Mojang's protocol schemas for
 * each have them: https://github.com/Mojang/bedrock-protocol-docs
 */
enum class GameVersion(val protocol: Int, val title: String) {
    V1_26_50(2193, "1.26.50"),

    /** 1.26.60, as its last preview numbers it. */
    V1_26_60(2225, "1.26.60"),
    ;

    companion object {
        val OLDEST = entries.first()
        val NEWEST = entries.last()

        /** The version that speaks [protocol], if Kore speaks it. */
        fun of(protocol: Int) = entries.find { it.protocol == protocol }
    }
}
