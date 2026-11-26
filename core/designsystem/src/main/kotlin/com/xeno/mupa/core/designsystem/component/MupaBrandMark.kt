package com.xeno.mupa.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xeno.mupa.core.designsystem.R

/**
 * Glyph resmi "m" Mupa (on-dark) langsung dari path SVG sumber tanpa mengubah geometri.
 */
@Composable
fun MupaLogoGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    contentDescription: String? = "Logo Mupa",
) {
    Image(
        painter = painterResource(id = R.drawable.ic_mupa_glyph_ondark),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

/**
 * Lockup sementara yang tajam di semua DPI: glyph vektor resmi + teks tipografi Compose "mupa".
 * (Menghindari crop PNG sementara yang beresolusi rendah.)
 */
@Composable
fun MupaBrandMark(
    modifier: Modifier = Modifier,
    glyphSize: Dp = 28.dp,
    showWordmark: Boolean = true,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Mupa"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MupaLogoGlyph(
            size = glyphSize,
            contentDescription = null,
        )
        if (showWordmark) {
            Text(
                text = "mupa",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
    }
}
