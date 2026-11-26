package com.xeno.mupa.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.xeno.mupa.core.model.LyricsLine
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.TrackDisplayRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Mode Cinema imersif layar penuh (perbaikan B11):
 * - Backdrop = cover yang di-blur SEKALI dan di-cache di memori (bukan blur live per frame) + scrim gradasi.
 * - Mode OLED = hitam pekat #000000 tanpa blur.
 * - Jika lirik tersedia: tampilkan lirik sinkron besar di tengah; jika tidak: cover besar + judul & artis.
 * - Kontrol minimal (Play/Pause + seek tipis) memudar otomatis setelah 3 detik tanpa sentuhan; tap untuk tampilkan lagi.
 * - Keluar lewat drag ke bawah atau tombol Back (bertahap: Cinema -> Full -> Mini).
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
    val appearance = LocalMupaAppearance.current
    val context = LocalContext.current
    val display = remember(track, stripArtistName) {
        TrackDisplayRules.formatTrack(track, stripArtistName)
    }

    // Cache one-shot blurred cover bitmap (tidak pernah blur live tiap frame)
    var cachedBlurredBackdrop by remember(track.artworkUrl, appearance.isOledMode) {
        mutableStateOf<Bitmap?>(null)
    }
    LaunchedEffect(track.artworkUrl, appearance.isOledMode, appearance.isPerformanceMode) {
        if (appearance.isOledMode || appearance.isPerformanceMode || track.artworkUrl.isNullOrBlank()) {
            cachedBlurredBackdrop = null
            return@LaunchedEffect
        }
        cachedBlurredBackdrop = withContext(Dispatchers.IO) {
            runCatching {
                val model: Any = if (track.artworkUrl!!.startsWith("/")) File(track.artworkUrl!!) else track.artworkUrl!!
                val loader = SingletonImageLoader.get(context)
                val req = ImageRequest.Builder(context)
                    .data(model)
                    .size(96)
                    .allowHardware(false)
                    .build()
                val rawBitmap = loader.execute(req).image?.toBitmap() ?: return@runCatching null
                fastBoxBlurOnce(rawBitmap, radius = 8)
            }.getOrNull()
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var lastTouchMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(controlsVisible, lastTouchMs, playerState.isPlaying) {
        if (controlsVisible && playerState.isPlaying) {
            delay(3_000L)
            controlsVisible = false
        }
    }

    var dragAccumY by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("mupa_cinema_overlay")
            .background(if (appearance.isOledMode) MupaPalette.OledBackground else MupaPalette.Background)
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
        // Backdrop blurred one-shot + scrim gradasi
        cachedBlurredBackdrop?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.65f),
                            0.45f to Color.Black.copy(alpha = 0.58f),
                            1.0f to Color.Black.copy(alpha = 0.86f),
                        ),
                    ),
            )
        }

        // Konten utama Cinema: lirik besar atau cover besar + judul
        if (playerState.lyrics.isNotEmpty()) {
            val edgeColor = if (appearance.isOledMode) MupaPalette.OledBackground else MupaPalette.Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 68.dp, bottom = 168.dp),
            ) {
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
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(Brush.verticalGradient(listOf(edgeColor.copy(alpha = 0.92f), Color.Transparent))),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, edgeColor.copy(alpha = 0.95f)))),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                MupaArtworkBox(
                    artworkUrl = track.artworkUrl,
                    contentDescription = display.title,
                    size = 256.dp,
                    shape = MupaRadii.ArtworkLarge,
                    iconSize = 64.dp,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = display.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MupaPalette.LogoCream,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = display.subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MupaPalette.TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Top bar & kontrol minimal yang memudar setelah 3 detik idle
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
                            tint = MupaPalette.LogoCream,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "CINEMA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MupaPalette.TextSecondary,
                        )
                        Text(
                            text = display.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MupaPalette.LogoCream,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Kontrol minimal bawah: seekbar tipis + Prev / Play-Pause / Next
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
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
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
                                contentDescription = "Lagu sebelumnya",
                                tint = MupaPalette.LogoCream,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(MupaPalette.CoralPrimary)
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
                                modifier = Modifier.size(30.dp),
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
                                contentDescription = "Lagu berikutnya",
                                tint = MupaPalette.LogoCream,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Box blur cepat 2-pass pada bitmap kecil (<= 96px) yang dipanggil SEKALI saat ganti lagu.
 */
internal fun fastBoxBlurOnce(source: Bitmap, radius: Int): Bitmap {
    val w = source.width.coerceAtLeast(1)
    val h = source.height.coerceAtLeast(1)
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
    val temp = IntArray(w * h)
    val r = radius.coerceIn(1, 16)
    val window = r * 2 + 1

    // Pass horizontal
    for (y in 0 until h) {
        val rowOff = y * w
        for (x in 0 until w) {
            var aSum = 0
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (k in -r..r) {
                val sx = (x + k).coerceIn(0, w - 1)
                val px = pixels[rowOff + sx]
                aSum += (px ushr 24) and 0xFF
                rSum += (px ushr 16) and 0xFF
                gSum += (px ushr 8) and 0xFF
                bSum += px and 0xFF
            }
            temp[rowOff + x] = ((aSum / window) shl 24) or
                ((rSum / window) shl 16) or
                ((gSum / window) shl 8) or
                (bSum / window)
        }
    }
    // Pass vertikal
    for (x in 0 until w) {
        for (y in 0 until h) {
            var aSum = 0
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (k in -r..r) {
                val sy = (y + k).coerceIn(0, h - 1)
                val px = temp[sy * w + x]
                aSum += (px ushr 24) and 0xFF
                rSum += (px ushr 16) and 0xFF
                gSum += (px ushr 8) and 0xFF
                bSum += px and 0xFF
            }
            pixels[y * w + x] = ((aSum / window) shl 24) or
                ((rSum / window) shl 16) or
                ((gSum / window) shl 8) or
                (bSum / window)
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}
