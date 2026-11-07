package com.flopster101.siliconplayer.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Every corner radius in the app. Values are the ones already in use, named so a
 * screen picks a token instead of inventing a number.
 *
 * The ladder is deliberately not collapsed: 12 vs 14 and 16 vs 18 are established
 * per-surface choices, so unifying them would be a visual change, not a cleanup.
 */
object Radius {
    /**
     * Raw values, for the places that rebuild a shape with per-corner values.
     * Each shape below is derived from its size, so the two cannot drift.
     */
    val InnerSize = 4.dp
    val PanelSize = 20.dp
    val ControlRoomySize = 18.dp

    val None = RoundedCornerShape(0.dp)
    /** Nested rows inside a settings card. */
    val Inner = RoundedCornerShape(InnerSize)
    /** Labels and badges sitting on artwork. */
    val Tiny = RoundedCornerShape(6.dp)
    /** Menu items, compact pills, watch-sized rows. */
    val Small = RoundedCornerShape(8.dp)
    /** Inset containers and clipped preview boxes. */
    val Compact = RoundedCornerShape(10.dp)
    /** Source/format chips in the browsers. */
    val Chip = RoundedCornerShape(11.dp)
    /** Buttons and fields inside settings dialogs. */
    val ControlCompact = RoundedCornerShape(12.dp)
    /** Default button and text-field corner across the app. */
    val Control = RoundedCornerShape(14.dp)
    /** Cards and list tiles. */
    val Card = RoundedCornerShape(16.dp)
    /** Roomier controls and action pills that are not fully rounded. */
    val ControlRoomy = RoundedCornerShape(ControlRoomySize)
    /** Settings card outer corner. */
    val Panel = RoundedCornerShape(PanelSize)

    /** One row of a grouped card: big corner on the group ends, inner corner between rows. */
    fun cardSegment(isFirst: Boolean, isLast: Boolean): Shape = RoundedCornerShape(
        topStart = if (isFirst) PanelSize else InnerSize,
        topEnd = if (isFirst) PanelSize else InnerSize,
        bottomStart = if (isLast) PanelSize else InnerSize,
        bottomEnd = if (isLast) PanelSize else InnerSize
    )

    /** Modal dialogs and bottom sheets. */
    val Dialog = RoundedCornerShape(24.dp)
    /** Large dialogs and picker sheets. */
    val Sheet = RoundedCornerShape(28.dp)

    /** Fully rounded ends, whatever the height. */
    val Pill: Shape = CircleShape

    /**
     * Source/format chip. [wide] is the row-leading chip, 14dp on a watch and 11dp
     * elsewhere; the compact form is 8dp on a watch and 11dp elsewhere.
     */
    fun chip(isWatch: Boolean, wide: Boolean = false): Shape = when {
        wide -> if (isWatch) Control else Chip
        isWatch -> Small
        else -> Chip
    }

    /** Full-bleed list row on phones, a floating card on watch. */
    fun row(isWatch: Boolean): Shape = if (isWatch) Control else None

    /** Multi-select row: rounds only the ends that are not joined to a selected neighbour. */
    fun selection(
        isWatch: Boolean,
        hasSelectedAbove: Boolean,
        hasSelectedBelow: Boolean
    ): Shape {
        if (isWatch) return Control
        return RoundedCornerShape(
            topStart = if (hasSelectedAbove) 0.dp else ControlRoomySize,
            topEnd = if (hasSelectedAbove) 0.dp else ControlRoomySize,
            bottomStart = if (hasSelectedBelow) 0.dp else ControlRoomySize,
            bottomEnd = if (hasSelectedBelow) 0.dp else ControlRoomySize
        )
    }
}
