package com.xeno.mupa.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.designsystem.theme.TABULAR_NUMS_FEATURE
import java.io.File

/**
 * Thumbnail cover art reusable dengan decode Coil terukur (tidak pernah decode bitmap penuh)
 * dan placeholder tenang berbasis Surface2.
 */
@Composable
fun MupaArtworkBox(
    artworkUrl: String?,
    contentDescription: String?,
    size: Dp,
    shape: Shape = MupaRadii.ArtworkSmall,
    iconSize: Dp = (size * 0.40f).coerceIn(18.dp, 48.dp),
    memoryCacheKey: String? = null,
    enableCrossfade: Boolean = true,
    decodeSizePxOverride: Int? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val decodePx = decodeSizePxOverride ?: with(LocalDensity.current) { size.roundToPx().coerceIn(96, 768) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(iconSize),
        )
        if (!artworkUrl.isNullOrBlank()) {
            val dataModel: Any = if (artworkUrl.startsWith("/")) File(artworkUrl) else artworkUrl
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(dataModel)
                    .size(decodePx)
                    .apply {
                        if (!memoryCacheKey.isNullOrBlank()) {
                            memoryCacheKey(memoryCacheKey)
                            placeholderMemoryCacheKey(memoryCacheKey)
                        }
                        if (enableCrossfade) crossfade(160) else crossfade(false)
                    }
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun MupaSectionHeader(
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actionText != null && onActionClick != null) {
            MupaTextButton(text = actionText, onClick = onActionClick)
        }
    }
}

/**
 * Kartu cover shelf 148dp (perbaikan B2):
 * - Clip HANYA pada kotak cover 16dp, TIDAK pada teks judul/subjudul.
 * - Lebar teks = lebar kartu (148dp), 1 baris Ellipsis.End tanpa fade/mask horizontal.
 */
@Composable
fun MupaCoverCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 148.dp,
) {
    Column(
        modifier = modifier
            .width(size)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MupaArtworkBox(
            artworkUrl = artworkUrl,
            contentDescription = title,
            size = size,
            shape = MupaRadii.ArtworkMedium,
            iconSize = 36.dp,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Baris lagu standar Mupa Calm (perbaikan B4 & B5):
 * - Cover 48dp radius 12dp, 2 baris teks, durasi angka tabular (disembunyikan jika null).
 * - Baris yang sedang diputar: judul koral + indikator batang animasi sederhana (tanpa overshoot).
 */
@Composable
fun MupaTrackRow(
    title: String,
    subtitle: String,
    durationText: String?,
    artworkUrl: String?,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MupaRadii.ArtworkMedium)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MupaArtworkBox(
            artworkUrl = artworkUrl,
            contentDescription = title,
            size = 48.dp,
            shape = MupaRadii.ArtworkSmall,
            iconSize = 20.dp,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isPlaying) {
                    PlayingBarsIndicator()
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlaying) MupaPalette.CoralPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!durationText.isNullOrBlank()) {
            Text(
                text = durationText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFeatureSettings = TABULAR_NUMS_FEATURE,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        if (onMoreClick != null) {
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Opsi lainnya untuk $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayingBarsIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "playingBars")
    val bar1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "bar1",
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(340), RepeatMode.Reverse),
        label = "bar2",
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "bar3",
    )

    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(bar1, bar2, bar3).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(14.dp * fraction)
                    .clip(CircleShape)
                    .background(MupaPalette.CoralPrimary),
            )
        }
    }
}

@Composable
fun MupaSegmentedTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MupaRadii.Pill)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.Pill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val bg by animateColorAsState(
                targetValue = if (isSelected) MupaPalette.CoralPrimary else Color.Transparent,
                animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
                label = "segBg_$index",
            )
            val fg by animateColorAsState(
                targetValue = if (isSelected) MupaPalette.LogoCharcoal else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
                label = "segFg_$index",
            )
            Box(
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .clip(MupaRadii.Pill)
                    .background(bg)
                    .semantics { selected = isSelected }
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Satu baris chip yang dapat di-scroll horizontal (perbaikan B9: tidak pernah membungkus ke baris kedua). */
@Composable
fun MupaChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun MupaFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MupaPalette.CoralPrimary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
        label = "chipBg_$label",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MupaPalette.LogoCharcoal else MaterialTheme.colorScheme.onSurface,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
        label = "chipFg_$label",
    )
    Row(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(MupaRadii.Pill)
            .background(bg)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                shape = MupaRadii.Pill,
            )
            .semantics { this.selected = selected }
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MupaFilterSheet(
    visible: Boolean,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    extraToggleLabel: String? = null,
    extraToggleChecked: Boolean = false,
    onExtraToggleChange: ((Boolean) -> Unit)? = null,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (extraToggleLabel != null && onExtraToggleChange != null) {
                MupaSettingRow(
                    title = extraToggleLabel,
                    subtitle = null,
                    checked = extraToggleChecked,
                    onCheckedChange = onExtraToggleChange,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            options.forEachIndexed { idx, label ->
                val isSelected = idx == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(MupaRadii.ArtworkSmall)
                        .background(if (isSelected) MupaPalette.CoralTint14 else Color.Transparent)
                        .clickable {
                            onSelect(idx)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) MupaPalette.CoralPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Dipilih",
                            tint = MupaPalette.CoralPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MupaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MupaRadii.Pill,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MupaPalette.CoralPrimary,
            contentColor = MupaPalette.LogoCharcoal,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MupaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MupaRadii.Pill,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MupaTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (accent) MupaPalette.CoralPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** StatTile tenang: angka KREM (bukan oranye, perbaikan B8) dengan angka tabular. */
@Composable
fun MupaStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MupaRadii.ArtworkMedium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.ArtworkMedium)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFeatureSettings = TABULAR_NUMS_FEATURE,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MupaSettingRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MupaPalette.LogoCharcoal,
                checkedTrackColor = MupaPalette.CoralPrimary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

@Composable
fun MupaSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = MupaRadii.ArtworkSmall,
) {
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Composable
fun MupaTrackRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MupaSkeletonBox(modifier = Modifier.size(48.dp), shape = MupaRadii.ArtworkSmall)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MupaSkeletonBox(modifier = Modifier.fillMaxWidth(0.62f).height(14.dp), shape = CircleShape)
            MupaSkeletonBox(modifier = Modifier.fillMaxWidth(0.38f).height(12.dp), shape = CircleShape)
        }
    }
}

@Composable
fun MupaCoverCardSkeleton(
    modifier: Modifier = Modifier,
    size: Dp = 148.dp,
) {
    Column(
        modifier = modifier.width(size),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MupaSkeletonBox(modifier = Modifier.size(size), shape = MupaRadii.ArtworkMedium)
        MupaSkeletonBox(modifier = Modifier.width(size * 0.75f).height(14.dp), shape = CircleShape)
        MupaSkeletonBox(modifier = Modifier.width(size * 0.48f).height(12.dp), shape = CircleShape)
    }
}

@Composable
fun MupaEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MupaRadii.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.Card)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MupaPrimaryButton(text = actionLabel, onClick = onAction)
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    MupaSecondaryButton(text = secondaryActionLabel, onClick = onSecondaryAction)
                }
            }
        }
    }
}

@Composable
fun MupaErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MupaEmptyState(
        icon = Icons.Rounded.ErrorOutline,
        title = "Terjadi kendala",
        subtitle = message,
        actionLabel = "Coba lagi",
        onAction = onRetry,
        modifier = modifier,
    )
}

@Preview(name = "Mupa Calm Components", showBackground = true, backgroundColor = 0xFF16161A)
@Composable
private fun MupaComponentsPreview() {
    MupaTheme(appearance = MupaAppearance()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MupaSectionHeader(title = "Untukmu", subtitle = "Berdasarkan artis pilihan", actionText = "Lihat semua", onActionClick = {})
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MupaCoverCard(title = "blue", subtitle = "yung kai", artworkUrl = null, onClick = {})
                    MupaCoverCard(title = "Bertaut", subtitle = "Nadin Amizah", artworkUrl = null, onClick = {})
                }
                MupaTrackRow(
                    title = "blue",
                    subtitle = "yung kai",
                    durationText = "3:34",
                    artworkUrl = null,
                    isPlaying = true,
                    onClick = {},
                    onMoreClick = {},
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MupaStatTile(label = "Lagu", value = "42", modifier = Modifier.weight(1f))
                    MupaStatTile(label = "Favorit", value = "12", modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
