package com.xeno.mupa.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val MupaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object MupaRadii {
    val Pill = CircleShape
    val FloatingNavBar = CircleShape
    val MiniPlayer = RoundedCornerShape(20.dp)
    val Card = RoundedCornerShape(24.dp)
    val Tile = RoundedCornerShape(20.dp)
    val Chip = CircleShape
    val ArtworkSmall = RoundedCornerShape(12.dp)  // Cover baris 12dp
    val ArtworkMedium = RoundedCornerShape(16.dp) // Cover kartu 16dp
    val ArtworkLarge = RoundedCornerShape(28.dp)  // Cover Now Playing 28dp
}

object MupaSpacing {
    val Grid = 4.dp
    val ScreenHorizontal = 20.dp
    val SectionGap = 28.dp
    val ItemGap = 12.dp
    val MinTouchTarget = 48.dp
}
