package com.xeno.mupa.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xeno.mupa.R
import com.xeno.mupa.core.designsystem.component.MupaGlassSurface
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.model.GlassStyle

/**
 * Floating Glass Bottom Navigation 4 Tab (Beranda, Cari, Pustaka, Akun).
 * - Indikator pil bergeser halus dengan Emphasized Decelerate (nol overshoot/bounce).
 * - Target sentuh tiap tab >= 48dp, lengkap dengan semantics dan contentDescription.
 */
@Composable
fun MupaFloatingBottomBar(
    currentDestination: MupaTopLevelDestination,
    onSelectDestination: (MupaTopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations = remember { MupaTopLevelDestination.entries }
    val selectedIndex = destinations.indexOf(currentDestination).coerceAtLeast(0)
    val appearance = LocalMupaAppearance.current

    val indicatorColor = if (appearance.isOledMode) {
        Color.White.copy(alpha = 0.14f)
    } else {
        MupaPalette.CoralPrimary.copy(alpha = 0.18f)
    }
    val activeContentColor = if (appearance.isOledMode) {
        MupaPalette.LogoCream
    } else {
        MupaPalette.CoralBright
    }
    val inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant

    val navContentDescription = stringResource(R.string.cd_bottom_nav)

    MupaGlassSurface(
        shape = MupaRadii.FloatingNavBar,
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(64.dp)
            .semantics { contentDescription = navContentDescription },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            val slotWidth = maxWidth / destinations.size
            val indicatorOffsetX by animateDpAsState(
                targetValue = slotWidth * selectedIndex,
                animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationMediumMs),
                label = "bottomNavIndicatorOffset",
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = indicatorOffsetX.roundToPx(), y = 0) }
                    .width(slotWidth)
                    .fillMaxHeight()
                    .clip(MupaRadii.Pill)
                    .background(indicatorColor),
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    val isSelected = destination == currentDestination
                    val label = stringResource(destination.labelRes)
                    val selectedDescription = stringResource(R.string.cd_tab_selected, label)
                    val itemColor by animateColorAsState(
                        targetValue = if (isSelected) activeContentColor else inactiveContentColor,
                        animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
                        label = "bottomNavColor_${destination.route}",
                    )
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(MupaRadii.Pill)
                            .semantics {
                                selected = isSelected
                                contentDescription = if (isSelected) selectedDescription else label
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelectDestination(destination) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = null,
                                tint = itemColor,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = itemColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Floating Bottom Bar — Liquid+", showBackground = true, backgroundColor = 0xFF0E0E11)
@Composable
private fun MupaFloatingBottomBarLiquidPreview() {
    MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.LIQUID_PLUS)) {
        Box(modifier = Modifier.padding(16.dp)) {
            MupaFloatingBottomBar(
                currentDestination = MupaTopLevelDestination.HOME,
                onSelectDestination = {},
            )
        }
    }
}

@Preview(name = "Floating Bottom Bar — Crystal", showBackground = true, backgroundColor = 0xFF0E0E11)
@Composable
private fun MupaFloatingBottomBarCrystalPreview() {
    MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.CRYSTAL)) {
        Box(modifier = Modifier.padding(16.dp)) {
            MupaFloatingBottomBar(
                currentDestination = MupaTopLevelDestination.LIBRARY,
                onSelectDestination = {},
            )
        }
    }
}

@Preview(name = "Floating Bottom Bar — OLED", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MupaFloatingBottomBarOledPreview() {
    MupaTheme(appearance = MupaAppearance(isOledMode = true)) {
        Box(modifier = Modifier.padding(16.dp)) {
            MupaFloatingBottomBar(
                currentDestination = MupaTopLevelDestination.ACCOUNT,
                onSelectDestination = {},
            )
        }
    }
}
