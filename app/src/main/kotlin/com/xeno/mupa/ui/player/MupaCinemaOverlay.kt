package com.xeno.mupa.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.xeno.mupa.core.designsystem.component.MupaArtworkBox
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.TrackDisplayRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Latar belakang penuh (full-bleed edge-to-edge) dari sampul lagu yang di-blur satu kali & di-cache.
 * - Mengisi 100% layar tanpa garis hitam atas/bawah.
 * - Mode OLED tetap memakai hitam murni #000000.
 */
@Composable
fun FullBleedBlurredCoverBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    topScrimAlpha: Float = 0.34f,
    midScrimAlpha: Float = 0.46f,
    bottomScrimAlpha: Float = 0.78f,
) {
    val appearance = LocalMupaAppearance.current
    val context = LocalContext.current

    if (appearance.isOledMode) {
        Box(modifier = modifier.fillMaxSize().background(MupaPalette.OledBackground))
        return
    }

    var blurredBitmap by remember(artworkUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(artworkUrl, appearance.isPerformanceMode) {
        if (appearance.isPerformanceMode || artworkUrl.isNullOrBlank()) {
            blurredBitmap = null
            return@LaunchedEffect
        }
        blurredBitmap = withContext(Dispatchers.IO) {
            runCatching {
                val model: Any = if (artworkUrl.startsWith("/")) File(artworkUrl) else artworkUrl
                val loader = SingletonImageLoader.get(context)
                val req = ImageRequest.Builder(context)
                    .data(model)
                    .size(128)
                    .allowHardware(false)
                    .build()
                val raw = loader.execute(req).image?.toBitmap() ?: return@runCatching null
                fastBoxBlurOnce(raw, radius = 10)
            }.getOrNull()
        }
    }

    val coverAccent = appearance.effectiveCoverAccent
    val imageAlpha by animateFloatAsState(
        targetValue = if (blurredBitmap != null) 1f else 0f,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationMediumMs),
        label = "fullBleedBackdropAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        coverAccent.primary.copy(alpha = 0.42f),
                        Color(0xFF1D1820),
                        Color(0xFF121216),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(0f, 1600f),
                ),
            ),
    ) {
        blurredBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = imageAlpha
                        scaleX = 1.18f
                        scaleY = 1.18f
                    },
            )
        }

        // Scrim lembut agar teks putih/krem selalu kontras tajam tanpa garis hitam kaku
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = topScrimAlpha),
                        0.45f to Color.Black.copy(alpha = midScrimAlpha),
                        1.0f to Color.Black.copy(alpha = bottomScrimAlpha),
                    ),
                ),
        )
    }
}

/**
 * Mode Cinema layar penuh (full-bleed cover art blurred backdrop + lirik besar atau cover imersif).
 */
@Composable
fun MupaCinemaOverlay(
    track: Track,
    stripArtistName: Boolean,
    playerState: MupaPlayerUiState,
    positionProvider: () -> Long,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onExitCinema: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = remember(track, stripArtistName) {
        TrackDisplayRules.formatTrack(track, stripArtistName)
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var lastTouchMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(controlsVisible, lastTouchMs, playerState.isPlaying) {
        if (controlsVisible && playerState.isPlaying) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    var dragAccumY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("mupa_cinema_overlay")
            .pointerInput(onExitCinema) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumY = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0f) dragAccumY += dragAmount
                    },
                    onDragEnd = {
                        if (dragAccumY > 140f) onExitCinema()
                        dragAccumY = 0f
                    },
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                controlsVisible = !controlsVisible
                lastTouchMs = System.currentTimeMillis()
            },
    ) {
        // Full-bleed blurred album art backdrop (0 garis hitam!)
        FullBleedBlurredCoverBackdrop(
            artworkUrl = track.artworkUrl,
            topScrimAlpha = 0.36f,
            midScrimAlpha = 0.44f,
            bottomScrimAlpha = 0.72f,
        )

        // Konten utama Cinema: lirik besar di atas full-bleed cover, atau cover besar + judul
        if (playerState.lyrics.isNotEmpty()) {
            MupaSyncedLyricsList(
                lines = playerState.lyrics,
                isPlain = playerState.lyricsIsPlain,
                positionProvider = positionProvider,
                offsetMs = playerState.lyricsOffsetMs,
                isCinemaScale = true,
                onSeekTo = {
                    lastTouchMs = System.currentTimeMillis()
                    onSeekTo(it)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 68.dp, bottom = 168.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                MupaArtworkBox(
                    artworkUrl = track.artworkUrl,
                    contentDescription = display.title,
                    size = 276.dp,
                    shape = MupaRadii.ArtworkLarge,
                    iconSize = 68.dp,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = display.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = display.subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.78f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Kontrol minimal auto-fade 3.5 detik
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(MupaMotion.standardTween(MupaMotion.DurationShortMs)),
            exit = fadeOut(MupaMotion.standardTween(MupaMotion.DurationShortMs)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = onExitCinema,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Keluar dari mode Cinema",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "CINEMA",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                        Text(
                            text = display.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.size(48.dp))
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MupaCustomSeekbar(
                        progress = playerState.progressFraction,
                        bufferedFraction = playerState.bufferedFraction,
                        showBuffer = track.sourceType == AudioSourceType.STREAMING,
                        positionMs = playerState.positionMs,
                        durationMs = playerState.durationMs,
                        onSeekTo = {
                            lastTouchMs = System.currentTimeMillis()
                            onSeekTo(it)
                        },
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        IconButton(
                            onClick = {
                                lastTouchMs = System.currentTimeMillis()
                                onSkipPrevious()
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Sebelumnya",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable {
                                    lastTouchMs = System.currentTimeMillis()
                                    onTogglePlayPause()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Jeda" else "Putar",
                                tint = MupaPalette.LogoCharcoal,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        IconButton(
                            onClick = {
                                lastTouchMs = System.currentTimeMillis()
                                onSkipNext()
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Berikutnya",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Box blur cepat 2-pass pada bitmap kecil (<=128px) satu kali di Dispatchers.IO.
 */
internal fun fastBoxBlurOnce(source: Bitmap, radius: Int): Bitmap {
    val safeRadius = radius.coerceIn(2, 16)
    val w = source.width.coerceAtLeast(1)
    val h = source.height.coerceAtLeast(1)
    val working = if (source.config == Bitmap.Config.ARGB_8888) {
        source.copy(Bitmap.Config.ARGB_8888, true)
    } else {
        source.copy(Bitmap.Config.ARGB_8888, true)
    }
    val pixels = IntArray(w * h)
    val temp = IntArray(w * h)
    working.getPixels(pixels, 0, w, 0, 0, w, h)

    repeat(2) {
        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                val x0 = (x - safeRadius).coerceAtLeast(0)
                val x1 = (x + safeRadius).coerceAtMost(w - 1)
                for (kx in x0..x1) {
                    val p = pixels[rowOff + kx]
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                    count++
                }
                temp[rowOff + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                val y0 = (y - safeRadius).coerceAtLeast(0)
                val y1 = (y + safeRadius).coerceAtMost(h - 1)
                for (ky in y0..y1) {
                    val p = temp[ky * w + x]
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                    count++
                }
                pixels[y * w + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
    }

    working.setPixels(pixels, 0, w, 0, 0, w, h)
    return working
}
