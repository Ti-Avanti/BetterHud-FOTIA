package kr.toxicity.hud.text

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertFailsWith

class GlyphWidthCacheTest {
    private fun glyphs() = Int2ObjectOpenHashMap<TextScale>().apply {
        put(65, TextScale(7.0, 16.0))
        put(0x4E2D, TextScale(16.0, 16.0))
        put(0xAC00, TextScale(13.5, 15.75))
        put(0x1F600, TextScale(8.25, 12.5))
    }

    @Test
    fun repeatedLayoutsShareOneWidthTable() {
        val cache = GlyphWidthCache(glyphs())
        val expected = cache.widths(0.5, mapOf(0xE001 to 19))
        repeat(3408) {
            assertSame(expected, cache.widths(0.5, mapOf(0xE001 to 19)))
        }
    }

    @Test
    fun preservesLegacyRoundingAndImageOverrides() {
        val source = glyphs()
        val cache = GlyphWidthCache(source)
        for (scale in listOf(0.1, 0.5, 0.75, 1.0, 1.15, 2.0)) {
            val widths = cache.widths(scale, mapOf(65 to 0, 0xE001 to 27))
            source.forEach { (codepoint, metrics) ->
                assertEquals(if (codepoint == 65) 0 else (metrics * scale).normalizedWidth, widths[codepoint])
            }
            assertEquals(27, widths[0xE001])
            assertEquals(0, widths[0x10FFFF])
        }
    }

    @Test
    fun differentScaleAndImageWidthsDoNotShareResults() {
        val cache = GlyphWidthCache(glyphs())
        val first = cache.widths(0.5, mapOf(0xE001 to 10))
        val second = cache.widths(1.0, mapOf(0xE001 to 10))
        val third = cache.widths(0.5, mapOf(0xE001 to 20))
        assertNotSame(first, second)
        assertNotSame(first, third)
        assertEquals(8, first[0x4E2D])
        assertEquals(16, second[0x4E2D])
        assertEquals(20, third[0xE001])
    }

    @Test
    fun newFontResourceDoesNotReusePreviousReloadMetrics() {
        val first = GlyphWidthCache(glyphs()).widths(1.0, emptyMap())
        val updated = glyphs().apply { put(65, TextScale(13.0, 16.0)) }
        val second = GlyphWidthCache(updated).widths(1.0, emptyMap())
        assertEquals(7, first[65])
        assertEquals(13, second[65])
    }

    @Test
    fun sharedTablesCannotBeModifiedByOneLayout() {
        val table = GlyphWidthCache(glyphs()).widths(1.0, emptyMap())
        assertFailsWith<UnsupportedOperationException> { table.put(65, 999) }
        assertEquals(7, table[65])
    }

    @Test
    fun changingCallerImageMapDoesNotCorruptCachedKey() {
        val cache = GlyphWidthCache(glyphs())
        val images = mutableMapOf(0xE001 to 10)
        val first = cache.widths(1.0, images)
        images[0xE001] = 20
        assertEquals(20, cache.widths(1.0, images)[0xE001])
        assertEquals(10, first[0xE001])
        assertSame(first, cache.widths(1.0, mapOf(0xE001 to 10)))
    }

    @Test
    fun parallelLayoutLoadingPublishesOnlyOneTable() {
        val cache = GlyphWidthCache(glyphs())
        val executor = java.util.concurrent.Executors.newFixedThreadPool(8)
        try {
            val futures = List(128) {
                java.util.concurrent.CompletableFuture.supplyAsync({
                    cache.widths(0.5, mapOf(0xE001 to 10))
                }, executor)
            }
            val first = futures.first().get(5, java.util.concurrent.TimeUnit.SECONDS)
            futures.forEach { assertSame(first, it.get(5, java.util.concurrent.TimeUnit.SECONDS)) }
        } finally {
            executor.shutdownNow()
        }
    }
}
