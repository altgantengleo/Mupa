package com.xeno.mupa.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val MupaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

object MupaRadii {
    val Pill = CircleShape
    val FloatingNavBar = RoundedCornerShape(32.dp)
    val MiniPlayer = RoundedCornerShape(20.dp)
    val Card = RoundedCornerShape(18.dp)
    val Chip = RoundedCornerShape(14.dp)
    val ArtworkSmall = RoundedCornerShape(10.dp)
    val ArtworkMedium = RoundedCornerShape(16.dp)
    val ArtworkLarge = RoundedCornerShape(24.dp)
}
