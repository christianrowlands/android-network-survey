package com.craxiom.networksurvey.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner shapes from the prototype. Read through [NsTheme.shapes]. */
object NsShapes {
    /** Filter and setup chips. */
    val chip = RoundedCornerShape(8.dp)

    /** Record-type tiles and text fields. */
    val tile = RoundedCornerShape(12.dp)

    /** Panels, cards and list-row groups. */
    val panel = RoundedCornerShape(14.dp)

    /** The setup card and other hero panels. */
    val hero = RoundedCornerShape(16.dp)

    /** Top corners of a bottom sheet. */
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /** Fully rounded: buttons, badges, the nav pill, segmented controls. */
    val pill = CircleShape
}

/** The Material 3 shape scale aligned with [NsShapes], so stock components match. */
val NsMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = NsShapes.chip,
    medium = NsShapes.tile,
    large = NsShapes.panel,
    extraLarge = RoundedCornerShape(20.dp),
)
