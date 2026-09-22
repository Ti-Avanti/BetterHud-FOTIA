package kr.toxicity.hud.text

import kr.toxicity.hud.util.IntEntryMap
import kr.toxicity.hud.util.IntKeyMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2IntMaps
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import java.util.concurrent.ConcurrentHashMap

/** 同一字体资源只为不同缩放/图标宽度组合构建一次；随字体资源一起释放。 */
internal class GlyphWidthCache(private val glyphs: IntKeyMap<TextScale>) {
    private data class Key(val scale: Double, val images: Map<Int, Int>)
    private val tables = ConcurrentHashMap<Key, IntEntryMap>()

    fun widths(scale: Double, images: Map<Int, Int>): IntEntryMap {
        val key = Key(scale, images.toMap())
        return tables.computeIfAbsent(key) {
            val result = Int2IntOpenHashMap(glyphs.size + it.images.size)
            val iterator = Int2ObjectMaps.fastIterator(glyphs)
            while (iterator.hasNext()) {
                val entry = iterator.next()
                result.put(entry.intKey, (entry.value * it.scale).normalizedWidth)
            }
            it.images.forEach { (codepoint, width) -> result.put(codepoint, width) }
            Int2IntMaps.unmodifiable(result)
        }
    }
}
