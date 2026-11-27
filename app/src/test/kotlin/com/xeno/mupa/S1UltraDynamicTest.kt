package com.xeno.mupa

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.xeno.mupa.core.designsystem.component.UltraDynamicBackground
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.designsystem.theme.UltraDynamicExtractor
import com.xeno.mupa.core.designsystem.theme.UltraDynamicPalette
import com.xeno.mupa.core.model.GlassStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Verifikasi S1 Ultra Dynamic:
 * - Fixture cover programatik: biru/pasir, gelap pekat, putih/pastel, abu monokrom,
 *   merah hangat, hijau, cover bertulisan tebal, dan tanpa cover (fallback koral/amber).
 * - Membuktikan cover biru/pasir menghasilkan BIRU + PASIR (bukan abu-abu keruh!),
 *   cover monokrom jatuh ke netral abu/perak (ikut HTML V15), tanpa cover jatuh ke merek Mupa,
 *   kontras teks utama #F6F4EF di atas bgDeep >= 4.5:1, onAccent OKLCH L >= 0.87.
 * - Menyimpan visual bukti ke docs/screens/ud/ untuk diperiksa langsung.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class S1UltraDynamicTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private data class CoverFixture(
        val fileName: String,
        val label: String,
        val bitmap: Bitmap?,
    )

    private fun buildPixels(size: Int = 96, colorAt: (x: Int, y: Int, size: Int) -> Int): Bitmap {
        val px = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                px[y * size + x] = colorAt(x, y, size)
            }
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun makeBlueSandBeachCover(): Bitmap = buildPixels { x, y, s ->
        // Mirip sampul "blue" (yung kai): langit & laut biru di atas/kiri, pantai pasir krem hangat di bawah/kanan
        val diagonal = y.toFloat() / s + x.toFloat() / (s * 3.5f)
        when {
            diagonal < 0.56f -> Color(0xFF3E84D8).toArgb() // biru langit/laut cerah
            diagonal < 0.66f -> Color(0xFF72AEE8).toArgb() // ombak biru muda
            else -> Color(0xFFE7C68E).toArgb() // pasir krem hangat
        }
    }

    private fun makePitchDarkCover(): Bitmap = buildPixels { x, y, s ->
        val dx = x - s / 2
        val dy = y - s / 2
        if (dx * dx + dy * dy < (s / 5) * (s / 5)) Color(0xFF1A1A20).toArgb() else Color(0xFF0A0A0D).toArgb()
    }

    private fun makeWhitePastelCover(): Bitmap = buildPixels { x, y, s ->
        when {
            y < s / 2 -> Color(0xFFF3F6FB).toArgb()
            x < s / 2 -> Color(0xFFBFDDF8).toArgb() // pastel biru
            else -> Color(0xFFF6C6D4).toArgb() // pastel pink
        }
    }

    private fun makeMonochromeGrayCover(): Bitmap = buildPixels { x, y, s ->
        val shade = ((x + y) * 180 / (2 * s) + 36).coerceIn(20, 225)
        (0xFF shl 24) or (shade shl 16) or (shade shl 8) or shade
    }

    private fun makeWarmRedCover(): Bitmap = buildPixels { x, y, s ->
        if (y < s * 0.6f) Color(0xFFD13A2F).toArgb() else Color(0xFFF0983B).toArgb()
    }

    private fun makeForestGreenCover(): Bitmap = buildPixels { x, y, s ->
        if (x < s * 0.58f) Color(0xFF288B57).toArgb() else Color(0xFF9AC94B).toArgb()
    }

    private fun makeBoldTextCover(): Bitmap = buildPixels { x, y, s ->
        // Cover biru-ungu dengan blok teks putih tebal besar di tengah
        val inTextBand = y in (s * 35 / 100)..(s * 65 / 100) && x in (s * 14 / 100)..(s * 86 / 100) && ((x / 6) % 2 == 0)
        when {
            inTextBand -> Color.White.toArgb()
            y < s / 2 -> Color(0xFF3E4EB8).toArgb()
            else -> Color(0xFFC2438B).toArgb()
        }
    }

    @Test
    fun verifyPaletteMathAndGenerateAllUdFixtureScreenshots() {
        val blueSandBmp = makeBlueSandBeachCover()
        val blueSand = UltraDynamicExtractor.extractFromBitmap(blueSandBmp)
        assertFalse("Blue/sand must NOT be monochrome", blueSand.isMonochrome)
        assertFalse("Blue/sand must NOT be no-cover fallback", blueSand.isNoCoverFallback)

        val (_, c1, h1) = UltraDynamicExtractor.colorToOklch(blueSand.glow1)
        val (_, c2, h2) = UltraDynamicExtractor.colorToOklch(blueSand.glow2)
        val gap = UltraDynamicExtractor.hueGapDeg(h1, h2)
        assertTrue("Blue/sand glow1 chroma must be vivid (>=0.09), got $c1", c1 >= 0.09f)
        assertTrue("Blue/sand glow2 chroma must be vivid (>=0.08), got $c2", c2 >= 0.08f)
        assertTrue("Blue/sand glow1 & glow2 hue gap must be >= 25 deg, got $gap (h1=$h1, h2=$h2)", gap >= 25f)
        val hasBlue = (h1 in 210f..285f) || (h2 in 210f..285f)
        val hasSand = (h1 in 55f..115f) || (h2 in 55f..115f)
        assertTrue("Blue/sand MUST extract blue + sand (h1=$h1, h2=$h2)", hasBlue && hasSand)

        val mono = UltraDynamicExtractor.extractFromBitmap(makeMonochromeGrayCover())
        assertTrue("Monochrome gray must return neutral HTML palette", mono.isMonochrome)
        assertFalse("Monochrome must NOT use brand coral/amber fallback", mono.isNoCoverFallback)

        val pitchDark = UltraDynamicExtractor.extractFromBitmap(makePitchDarkCover())
        assertTrue("Pitch dark monochrome must return neutral palette", pitchDark.isMonochrome)

        val noCover = UltraDynamicExtractor.extractFromBitmap(null)
        assertTrue("Null cover must return Mupa brand Coral/Amber fallback", noCover.isNoCoverFallback)

        val fixtures = listOf(
            CoverFixture("ud_01_blue_sand", "Biru & Pasir (blue)", blueSandBmp),
            CoverFixture("ud_02_pitch_dark", "Gelap Pekat", makePitchDarkCover()),
            CoverFixture("ud_03_white_pastel", "Putih & Pastel", makeWhitePastelCover()),
            CoverFixture("ud_04_monochrome_gray", "Monokrom Abu (HTML Neutral)", makeMonochromeGrayCover()),
            CoverFixture("ud_05_warm_red", "Merah Hangat & Amber", makeWarmRedCover()),
            CoverFixture("ud_06_forest_green", "Hijau Hutan & Lime", makeForestGreenCover()),
            CoverFixture("ud_07_bold_text_cover", "Cover Teks Tebal", makeBoldTextCover()),
            CoverFixture("ud_08_no_cover_brand", "Tanpa Cover (Brand Coral/Amber)", null),
        )

        for (fx in fixtures) {
            val palette = UltraDynamicExtractor.extractFromBitmap(fx.bitmap)
            val contrast = UltraDynamicExtractor.wcagContrastRatio(MupaPalette.LogoCream, palette.bgDeep)
            assertTrue("${fx.fileName}: WCAG contrast over bgDeep must be >= 4.5, got $contrast", contrast >= 4.5f)
            val (onAccentL, _, _) = UltraDynamicExtractor.colorToOklch(palette.onAccent)
            assertTrue("${fx.fileName}: onAccent OKLCH L must be >= 0.87, got $onAccentL", onAccentL >= 0.87f)
        }
    }

    @Test
    fun renderBlueSandFixtureFullScreen() {
        val outDir = File("../docs/screens/ud").apply { mkdirs() }
        val bmp = makeBlueSandBeachCover()
        val pal = UltraDynamicExtractor.extractFromBitmap(bmp)
        renderFixtureCard(outDir, "ud_01_blue_sand_full", CoverFixture("ud_01_blue_sand", "Biru & Pasir (blue)", bmp), pal)
    }

    @Test
    fun renderMonochromeFixtureFullScreen() {
        val outDir = File("../docs/screens/ud").apply { mkdirs() }
        val bmp = makeMonochromeGrayCover()
        val pal = UltraDynamicExtractor.extractFromBitmap(bmp)
        renderFixtureCard(outDir, "ud_04_monochrome_full", CoverFixture("ud_04_monochrome_gray", "Monokrom Abu (HTML Neutral)", bmp), pal)
    }

    @Test
    fun renderAll8FixturesGrid() {
        val outDir = File("../docs/screens/ud").apply { mkdirs() }
        val fixtures = listOf(
            CoverFixture("ud_01_blue_sand", "Biru & Pasir (blue)", makeBlueSandBeachCover()),
            CoverFixture("ud_02_pitch_dark", "Gelap Pekat", makePitchDarkCover()),
            CoverFixture("ud_03_white_pastel", "Putih & Pastel", makeWhitePastelCover()),
            CoverFixture("ud_04_monochrome_gray", "Monokrom Abu (HTML Neutral)", makeMonochromeGrayCover()),
            CoverFixture("ud_05_warm_red", "Merah Hangat & Amber", makeWarmRedCover()),
            CoverFixture("ud_06_forest_green", "Hijau Hutan & Lime", makeForestGreenCover()),
            CoverFixture("ud_07_bold_text_cover", "Cover Teks Tebal", makeBoldTextCover()),
            CoverFixture("ud_08_no_cover_brand", "Tanpa Cover (Brand Coral/Amber)", null),
        )
        renderFixtureGrid(outDir, "ud_all_8_fixtures", fixtures)
    }

    private fun renderFixtureCard(dir: File, name: String, fx: CoverFixture, palette: UltraDynamicPalette) {
        composeRule.setContent {
            MupaTheme(
                appearance = MupaAppearance(
                    glassStyle = GlassStyle.SOLID,
                    isUltraDynamicEnabled = true,
                    ultraDynamicPalette = palette,
                ),
            ) {
                Box(Modifier.fillMaxSize()) {
                    UltraDynamicBackground(palette = palette, headerOnly = false)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                    ) {
                        Text(
                            text = fx.label,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MupaPalette.LogoCream,
                        )
                        fx.bitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = fx.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(24.dp)),
                            )
                        }
                        SwatchStrip(palette)
                        Text(
                            text = "Wipe Lirik (onAccent L >= 0.87)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = palette.onAccent,
                        )
                    }
                }
            }
        }
        savePng(dir, name)
    }

    private fun renderFixtureGrid(dir: File, name: String, fixtures: List<CoverFixture>) {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MupaPalette.Background)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Ultra Dynamic — 8 Fixture Palet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MupaPalette.LogoCream,
                    )
                    fixtures.forEach { fx ->
                        val pal = UltraDynamicExtractor.extractFromBitmap(fx.bitmap)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        ) {
                            UltraDynamicBackground(palette = pal, headerOnly = false)
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (fx.bitmap != null) {
                                    Image(
                                        bitmap = fx.bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MupaPalette.Surface2),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fx.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MupaPalette.LogoCream,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = "Lirik aktif",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = pal.onAccent,
                                    )
                                }
                                SwatchStrip(pal, compact = true)
                            }
                        }
                    }
                }
            }
        }
        savePng(dir, name)
    }

    @Composable
    private fun SwatchStrip(palette: UltraDynamicPalette, compact: Boolean = false) {
        val sz = if (compact) 18.dp else 28.dp
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
            listOf(palette.bgDeep, palette.glow1, palette.glow2, palette.glow3, palette.onAccent).forEach { c ->
                Box(
                    modifier = Modifier
                        .size(sz)
                        .clip(CircleShape)
                        .background(c)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                )
            }
        }
    }

    private fun savePng(dir: File, name: String) {
        composeRule.waitForIdle()
        val file = File(dir, "$name.png")
        val contentView = composeRule.activity.findViewById<View>(android.R.id.content)
        val bmp = contentView.drawToBitmap(Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("UD fixture screenshot $name must exist", file.exists() && file.length() > 1_000L)
    }
}
