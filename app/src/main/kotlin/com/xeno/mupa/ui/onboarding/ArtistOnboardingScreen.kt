package com.xeno.mupa.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.xeno.mupa.core.designsystem.component.MupaLogoGlyph
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.model.Artist
import kotlin.math.abs

private enum class OpeningStep {
    WELCOME,
    PICK_ARTISTS,
}

private object OpeningSheetTokens {
    val Backdrop = Color(0xFF09090C)
    val SheetSurface = Color(0xFF16161B)
    val CardSurface = Color(0xFF22222A)
    val FieldSurface = Color(0xFF24242D)
    val HandleColor = Color(0xFF3E3E48)
    val SubtitleColor = Color(0xFFB8B6C0)
    val MutedColor = Color(0xFF83818E)
    val DisabledButton = Color(0xFF2B2B34)
    val MaxContentWidth = 560.dp
    val SheetTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

/**
 * Layar Pembuka & Onboarding Artis Favorit Mupa bergaya editorial dark-sheet
 * dengan identitas Ember Editorial Mupa dan animasi nol overshoot.
 * - Mode awal: Welcome -> Pilih Artis Favorit (wajib >= 2, anjuran 4, maks 8, tanpa tombol lewati).
 * - Mode edit (dari Akun/Pengaturan): langsung ke Pilih Artis Favorit.
 */
@Composable
fun ArtistOnboardingScreen(
    uiState: ArtistOnboardingUiState,
    isEditMode: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleArtist: (String) -> Unit,
    onRetry: () -> Unit,
    onSubmit: () -> Unit,
    onNavigateBackOrExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable(isEditMode) {
        mutableStateOf(if (isEditMode) OpeningStep.PICK_ARTISTS else OpeningStep.WELCOME)
    }

    BackHandler(enabled = true) {
        if (!isEditMode && step == OpeningStep.PICK_ARTISTS) {
            step = OpeningStep.WELCOME
        } else {
            onNavigateBackOrExit()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OpeningSheetTokens.Backdrop)
            .statusBarsPadding(),
    ) {
        // Sheet gelap terangkat dengan sudut atas bulat 28dp (gaya editorial sheet)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp)
                .clip(OpeningSheetTokens.SheetTopShape)
                .background(OpeningSheetTokens.SheetSurface),
        ) {
            // Cahaya radial Ember sangat halus di puncak sheet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MupaPalette.CoralPrimary.copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                            radius = 650f,
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Pegangan sheet minimalis
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 6.dp)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(OpeningSheetTokens.HandleColor),
                )

                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val slideIn = slideInHorizontally(
                            animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationMediumMs),
                        ) { fullWidth -> if (targetState == OpeningStep.PICK_ARTISTS) fullWidth / 4 else -fullWidth / 4 }
                        val slideOut = slideOutHorizontally(
                            animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
                        ) { fullWidth -> if (targetState == OpeningStep.PICK_ARTISTS) -fullWidth / 5 else fullWidth / 5 }
                        (slideIn + fadeIn(MupaMotion.emphasizedTween(MupaMotion.DurationMediumMs))) togetherWith
                            (slideOut + fadeOut(MupaMotion.standardTween(MupaMotion.DurationShortMs)))
                    },
                    label = "mupaOpeningStep",
                    modifier = Modifier.weight(1f),
                ) { currentStep ->
                    when (currentStep) {
                        OpeningStep.WELCOME -> WelcomeEditorialPage(
                            onStart = { step = OpeningStep.PICK_ARTISTS },
                        )
                        OpeningStep.PICK_ARTISTS -> ArtistPickerEditorialPage(
                            uiState = uiState,
                            isEditMode = isEditMode,
                            onQueryChange = onQueryChange,
                            onToggleArtist = onToggleArtist,
                            onRetry = onRetry,
                            onSubmit = onSubmit,
                            onBack = {
                                if (isEditMode) onNavigateBackOrExit() else step = OpeningStep.WELCOME
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeEditorialPage(
    onStart: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sidePadding = if (maxWidth < 360.dp) 20.dp else 28.dp
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = OpeningSheetTokens.MaxContentWidth)
                .fillMaxSize()
                .padding(horizontal = sidePadding, vertical = 18.dp),
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            // Lencana bulat ikon Mupa di kiri atas (pola editorial bersih)
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MupaPalette.LogoCharcoal)
                    .border(1.dp, MupaPalette.CoralPrimary.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MupaLogoGlyph(size = 30.dp)
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "Selamat datang\ndi Mupa",
                color = MupaPalette.LogoCream,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.04).em,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Streaming musik dan pustaka lokal dalam satu ruang yang bersih, cepat, dan tenang.",
                color = OpeningSheetTokens.SubtitleColor,
                fontSize = 17.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.025).em,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WelcomeFeatureCard(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Streaming & Lirik Adaptif",
                    description = "Pemutaran instan dengan cache pintar, lirik sinkron, dan equalizer 5-band.",
                )
                WelcomeFeatureCard(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "Pustaka Lokal & Mix Mupa",
                    description = "Pindai musik perangkatmu dengan rapi dan racik rekomendasi personal setiap hari.",
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            EditorialPillButton(
                text = "Mulai",
                enabled = true,
                onClick = onStart,
            )
        }
    }
}

@Composable
private fun WelcomeFeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(OpeningSheetTokens.CardSurface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MupaPalette.CoralPrimary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MupaPalette.CoralBright,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                color = MupaPalette.LogoCream,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).em,
            )
            Text(
                text = description,
                color = OpeningSheetTokens.SubtitleColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun ArtistPickerEditorialPage(
    uiState: ArtistOnboardingUiState,
    isEditMode: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleArtist: (String) -> Unit,
    onRetry: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sidePadding = if (maxWidth < 360.dp) 18.dp else 24.dp
        val columns = if (maxWidth >= 520.dp) 4 else 3

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = OpeningSheetTokens.MaxContentWidth)
                .fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header ringkas bergaya editorial
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sidePadding)
                        .padding(top = 6.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(OpeningSheetTokens.CardSurface),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Kembali",
                                    tint = MupaPalette.LogoCream,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            MupaLogoGlyph(size = 24.dp)
                        }

                        // Pil counter progres pilihan
                        val counterBg by animateColorAsState(
                            targetValue = if (uiState.canContinue) {
                                MupaPalette.CoralPrimary.copy(alpha = 0.2f)
                            } else {
                                OpeningSheetTokens.CardSurface
                            },
                            animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
                            label = "counterBg",
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(counterBg)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "${uiState.selectedCount} / ${ArtistOnboardingUiState.MAX_ARTISTS}",
                                color = if (uiState.canContinue) MupaPalette.CoralBright else OpeningSheetTokens.SubtitleColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Text(
                        text = if (isEditMode) "Ubah artis favorit" else "Pilih artis favoritmu",
                        color = MupaPalette.LogoCream,
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.04).em,
                    )

                    val helperText = when {
                        uiState.selectedCount < ArtistOnboardingUiState.MIN_ARTISTS ->
                            "Pilih minimal ${ArtistOnboardingUiState.MIN_ARTISTS} artis (anjuran ${ArtistOnboardingUiState.RECOMMENDED_ARTISTS}) untuk meracik Beranda."
                        uiState.reachedLimit ->
                            "Batas maksimal ${ArtistOnboardingUiState.MAX_ARTISTS} artis tercapai — siap lanjut."
                        else ->
                            "Pilihan mantap! Kamu bisa menambah hingga ${ArtistOnboardingUiState.MAX_ARTISTS} artis."
                    }
                    Text(
                        text = helperText,
                        color = OpeningSheetTokens.SubtitleColor,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        letterSpacing = (-0.015).em,
                    )

                    // Search bar pil tanpa bingkai kaku
                    EditorialSearchField(
                        value = uiState.query,
                        onValueChange = onQueryChange,
                        placeholder = "Cari artis favoritmu...",
                    )
                }

                // Grid Artis dengan foto HD Coil3
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when {
                        uiState.isLoading && uiState.artists.isEmpty() -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                contentPadding = PaddingValues(
                                    start = sidePadding,
                                    end = sidePadding,
                                    top = 8.dp,
                                    bottom = 96.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(12) {
                                    ArtistSkeletonItem()
                                }
                            }
                        }
                        uiState.errorMessage != null && uiState.artists.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = uiState.errorMessage,
                                    color = MupaPalette.LogoCream,
                                    textAlign = TextAlign.Center,
                                )
                                OutlinedButton(onClick = onRetry) {
                                    Text("Coba Lagi")
                                }
                            }
                        }
                        uiState.artists.isEmpty() -> {
                            Text(
                                text = "Artis tidak ditemukan untuk \"${uiState.query}\".",
                                color = OpeningSheetTokens.MutedColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                            )
                        }
                        else -> {
                            val uniqueArtists = remember(uiState.artists) { uiState.artists.distinctBy { it.id } }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                contentPadding = PaddingValues(
                                    start = sidePadding,
                                    end = sidePadding,
                                    top = 8.dp,
                                    bottom = 104.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(
                                    items = uniqueArtists,
                                    key = { it.id },
                                ) { artist ->
                                    val isSelected = artist.id in uiState.selectedArtistIds
                                    ArtistPortraitItem(
                                        artist = artist,
                                        isSelected = isSelected,
                                        isDisabled = !isSelected && uiState.reachedLimit,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onToggleArtist(artist.id)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Gradien lembut di bawah + tombol CTA pil (tanpa tombol Lewati)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        OpeningSheetTokens.SheetSurface.copy(alpha = 0.92f),
                                        OpeningSheetTokens.SheetSurface,
                                    ),
                                ),
                            )
                            .padding(horizontal = sidePadding, vertical = 16.dp),
                    ) {
                        val buttonText = when {
                            isEditMode -> "Simpan Pilihan (${uiState.selectedCount})"
                            uiState.canContinue -> "Lanjut (${uiState.selectedCount})"
                            else -> "Pilih ${ArtistOnboardingUiState.MIN_ARTISTS - uiState.selectedCount} artis lagi"
                        }
                        EditorialPillButton(
                            text = buttonText,
                            enabled = uiState.canContinue,
                            onClick = onSubmit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = MupaPalette.LogoCream,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        ),
        cursorBrush = SolidColor(MupaPalette.CoralPrimary),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(OpeningSheetTokens.FieldSurface)
                    .padding(start = 16.dp, end = if (value.isNotEmpty()) 6.dp else 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = OpeningSheetTokens.MutedColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = OpeningSheetTokens.MutedColor,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Hapus pencarian",
                            tint = OpeningSheetTokens.MutedColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun EditorialPillButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
        label = "pillScale",
    )
    val bgColor by animateColorAsState(
        targetValue = if (enabled) MupaPalette.CoralPrimary else OpeningSheetTokens.DisabledButton,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
        label = "pillBg",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) MupaPalette.LogoCharcoal else OpeningSheetTokens.MutedColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.015).em,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistPortraitItem(
    artist: Artist,
    isSelected: Boolean,
    isDisabled: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.94f else 1f,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
        label = "artistScale_${artist.id}",
    )
    val ringColor by animateColorAsState(
        targetValue = if (isSelected) MupaPalette.CoralPrimary else Color.White.copy(alpha = 0.08f),
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
        label = "artistRing_${artist.id}",
    )

    val fallbackTints = listOf(
        Color(0xFF42302E),
        Color(0xFF2D3846),
        Color(0xFF3A3042),
        Color(0xFF2A3D3B),
        Color(0xFF45362A),
    )
    val fallbackBg = fallbackTints[abs(artist.id.hashCode()) % fallbackTints.size]
    val initials = artist.name
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                selected = isSelected
                contentDescription = if (isSelected) "${artist.name}, dipilih" else "Pilih ${artist.name}"
            }
            .clickable(
                enabled = !isDisabled,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (isDisabled) 0.42f else 1f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(92.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Wadah lingkaran foto artis HD (Coil 3) + fallback inisial elegan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(fallbackBg)
                    .border(
                        width = if (isSelected) 2.8.dp else 1.dp,
                        color = ringColor,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    color = MupaPalette.LogoCream.copy(alpha = 0.72f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )

                if (!artist.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artist.artworkUrl,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.24f)),
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MupaPalette.CoralPrimary)
                        .border(2.dp, OpeningSheetTokens.SheetSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MupaPalette.LogoCharcoal,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        Text(
            text = artist.name,
            color = if (isSelected) MupaPalette.CoralBright else MupaPalette.LogoCream,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ArtistSkeletonItem() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(OpeningSheetTokens.CardSurface),
        )
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(12.dp)
                .clip(CircleShape)
                .background(OpeningSheetTokens.CardSurface),
        )
    }
}

@Preview(name = "Mupa Opening Welcome", showBackground = true)
@Composable
private fun OpeningWelcomePreview() {
    MupaTheme(appearance = MupaAppearance()) {
        Surface {
            ArtistOnboardingScreen(
                uiState = ArtistOnboardingUiState(isLoading = false),
                isEditMode = false,
                onQueryChange = {},
                onToggleArtist = {},
                onRetry = {},
                onSubmit = {},
                onNavigateBackOrExit = {},
            )
        }
    }
}
