package dev.glass.soundbar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * SF Symbol glyph outline of a named TEXT node inside an exported scene JSON.
 * Coordinates stay in the node's local pt box; Y-up is corrected at draw time,
 * matching FigmaSceneView's glyph transform (translate, rotate, scale(size, -size)).
 */
internal class SceneGlyph(
    val path: Path,
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float,
    val nodeWidth: Float = 32f,
)

private val sceneCache = HashMap<String, JSONObject>()

internal fun findNode(n: JSONObject, name: String): JSONObject? {
    if (n.optString("name") == name) return n
    val children = n.optJSONArray("children") ?: return null
    for (i in 0 until children.length()) findNode(children.getJSONObject(i), name)?.let { return it }
    return null
}

private fun findNodeOccurrence(n: JSONObject, name: String, target: Int, seen: IntArray): JSONObject? {
    if (n.optString("name") == name) {
        if (seen[0] == target) return n
        seen[0]++
    }
    val children = n.optJSONArray("children") ?: return null
    for (i in 0 until children.length()) {
        findNodeOccurrence(children.getJSONObject(i), name, target, seen)?.let { return it }
    }
    return null
}

internal fun findNodePrefix(n: JSONObject, prefix: String): JSONObject? {
    if (n.optString("name").startsWith(prefix)) return n
    val children = n.optJSONArray("children") ?: return null
    for (i in 0 until children.length()) findNodePrefix(children.getJSONObject(i), prefix)?.let { return it }
    return null
}

/** Navigation-bar titles: the INSTANCE named Title wraps the actual TEXT; plain rows keep bare TEXT nodes. */
private fun findTextNode(n: JSONObject, name: String): JSONObject? {
    if (n.optString("name") == name && n.optString("type") == "INSTANCE") {
        n.optJSONArray("children")?.let { children ->
            for (i in 0 until children.length()) {
                val child = children.getJSONObject(i)
                if (child.optString("name") == name && child.optString("type") == "TEXT") return child
            }
        }
    }
    val children = n.optJSONArray("children") ?: return null
    for (i in 0 until children.length()) findTextNode(children.getJSONObject(i), name)?.let { return it }
    return null
}

internal fun loadScene(context: Context, file: String): JSONObject = sceneCache.getOrPut(file) {
    JSONObject(context.assets.open("iphone-examples/$file").bufferedReader().use { it.readText() })
}

internal fun loadSceneGlyph(context: Context, file: String, nodeName: String, occurrence: Int = 0): SceneGlyph? {
    val scene = loadScene(context, file)
    val root = scene.optJSONObject("scene") ?: scene
    val node = (if (occurrence == 0) findNode(root, nodeName)
    else findNodeOccurrence(root, nodeName, occurrence, intArrayOf(0)))
        ?: return null
    val glyph = node.optJSONArray("glyphs")?.optJSONObject(0) ?: return null
    val data = glyph.getJSONArray("path")
    val path = Path()
    var i = 0
    while (i < data.length()) when (data.getInt(i++)) {
        0 -> path.close()
        1 -> { path.moveTo(data.getDouble(i).toFloat(), data.getDouble(i + 1).toFloat()); i += 2 }
        2 -> { path.lineTo(data.getDouble(i).toFloat(), data.getDouble(i + 1).toFloat()); i += 2 }
        3 -> { path.quadraticBezierTo(data.getDouble(i).toFloat(), data.getDouble(i + 1).toFloat(),
            data.getDouble(i + 2).toFloat(), data.getDouble(i + 3).toFloat()); i += 4 }
        4 -> { path.cubicTo(data.getDouble(i).toFloat(), data.getDouble(i + 1).toFloat(),
            data.getDouble(i + 2).toFloat(), data.getDouble(i + 3).toFloat(),
            data.getDouble(i + 4).toFloat(), data.getDouble(i + 5).toFloat()); i += 6 }
        else -> return null
    }
    return SceneGlyph(path, glyph.optDouble("x").toFloat(), glyph.optDouble("y").toFloat(),
        glyph.optDouble("size", 1.0).toFloat(), glyph.optDouble("rotation").toFloat(),
        node.optDouble("w", 32.0).toFloat())
}

/**
 * A full TEXT node: every glyph in reading order plus the node's own pt box and solid fill.
 * shadowBlur/shadowOffsetY carry the authored DROP_SHADOW, which FigmaSceneView paints on the node outline.
 */
internal class SceneText(val width: Float, val height: Float, val color: Color?, val glyphs: List<SceneGlyph>,
    val shadowBlur: Float = 0f, val shadowOffsetY: Float = 0f, val shadowAlpha: Float = 1f)

internal fun parseSceneText(node: JSONObject): SceneText? {
    val glyphs = node.optJSONArray("glyphs") ?: return null
    val parsed = ArrayList<SceneGlyph>(glyphs.length())
    for (g in 0 until glyphs.length()) {
        val glyph = glyphs.getJSONObject(g)
        val data = glyph.optJSONArray("path") ?: continue
        val path = Path()
        var i = 0
        var ok = true
        while (i < data.length() && ok) when (data.getInt(i++)) {
            0 -> path.close()
            1 -> { path.moveTo(data.getDouble(i).toFloat(), data.getDouble(i + 1).toFloat()); i += 2 }
            2 -> { path.lineTo(data.getDouble(i).toFloat(), data.getDouble(i + 1).toFloat()); i += 2 }
            3 -> { path.quadraticBezierTo(data.getDouble(i).toFloat(), data.getDouble(i + 1).toFloat(),
                data.getDouble(i + 2).toFloat(), data.getDouble(i + 3).toFloat()); i += 4 }
            4 -> { path.cubicTo(data.getDouble(i).toFloat(), data.getDouble(i + 1).toFloat(),
                data.getDouble(i + 2).toFloat(), data.getDouble(i + 3).toFloat(),
                data.getDouble(i + 4).toFloat(), data.getDouble(i + 5).toFloat()); i += 6 }
            else -> ok = false
        }
        if (ok) parsed.add(SceneGlyph(path, glyph.optDouble("x").toFloat(), glyph.optDouble("y").toFloat(),
            glyph.optDouble("size", 1.0).toFloat(), glyph.optDouble("rotation").toFloat()))
    }
    if (parsed.isEmpty()) return null
    val fillColor = node.optJSONArray("fills")?.optJSONObject(0)?.optJSONObject("color")?.let {
        Color(it.optDouble("r").toFloat(), it.optDouble("g").toFloat(), it.optDouble("b").toFloat(),
            it.optDouble("a", 1.0).toFloat())
    }
    var shadowBlur = 0f
    var shadowOffsetY = 0f
    var shadowAlpha = 1f
    node.optJSONArray("effects")?.let { effects ->
        for (e in 0 until effects.length()) {
            val effect = effects.optJSONObject(e) ?: continue
            if (effect.optString("type") != "DROP_SHADOW" || !effect.optBoolean("visible", true)) continue
            val radius = effect.optDouble("radius").toFloat()
            if (radius <= 0f) continue
            // Paint.setShadowLayer(radius, ...) blurs with radius/2, which is what the reference renderer uses.
            shadowBlur = radius / 2f
            shadowOffsetY = effect.optJSONObject("offset")?.optDouble("y")?.toFloat() ?: 0f
            shadowAlpha = effect.optJSONObject("color")?.optDouble("a", 1.0)?.toFloat() ?: 1f
            break
        }
    }
    return SceneText(node.optDouble("w").toFloat(), node.optDouble("h").toFloat(), fillColor, parsed,
        shadowBlur, shadowOffsetY, shadowAlpha)
}

/** First glyph-bearing TEXT node inside a subtree (button symbols live one level deep). */
private fun firstGlyphText(n: JSONObject): SceneText? {
    if (n.optString("type") == "TEXT") parseSceneText(n)?.let { return it }
    val children = n.optJSONArray("children") ?: return null
    for (i in 0 until children.length()) firstGlyphText(children.getJSONObject(i))?.let { return it }
    return null
}

internal fun loadSceneText(context: Context, file: String, nodeName: String): SceneText? {
    val scene = loadScene(context, file)
    val root = scene.optJSONObject("scene") ?: scene
    val node = findTextNode(root, nodeName) ?: findNode(root, nodeName)?.takeIf {
        it.optString("type") == "TEXT" && it.optJSONArray("glyphs")?.length() ?: 0 > 0
    } ?: return null
    return parseSceneText(node)
}

/** One original toolbar button: position/size in toolbar pt space plus its glyph texts. */
internal class SceneToolbarButton(
    val x: Float, val y: Float, val w: Float, val h: Float, val tinted: Boolean,
    val texts: List<Triple<Float, Float, SceneText>>,
)

@Composable
internal fun SceneToolbarButtonContent(
    button: SceneToolbarButton?,
    tint: Color,
    useSourceColor: Boolean = false,
    modifier: Modifier = Modifier,
    onGlyphClick: ((Int) -> Unit)? = null,
) {
    Box(modifier.fillMaxSize()) {
        button?.texts?.forEachIndexed { index, (x, y, text) ->
            val clickModifier = if (onGlyphClick == null) {
                Modifier
            } else {
                Modifier
                    .size(text.width.dp, text.height.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onGlyphClick(index) },
                    )
            }
            Box(Modifier.offset(x.dp, y.dp).then(clickModifier)) {
                SceneGlyphText(
                    text,
                    if (useSourceColor) text.color ?: tint else tint,
                )
            }
        }
    }
}

/** Original top toolbar: every button group, the centered title, and the large title block. */
internal class SceneToolbar(
    val buttons: List<SceneToolbarButton>,
    val title: SceneText?,
    val titleX: Float?,
    val titleY: Float?,
    val subtitle: SceneText?,
    val subtitleX: Float?,
    val subtitleY: Float?,
    val largeTitle: SceneText?,
    val largeSubtitle: SceneText?,
)

internal fun collectTexts(n: JSONObject, ox: Float, oy: Float, out: MutableList<Triple<Float, Float, SceneText>>) {
    val m = n.optJSONArray("matrix")
    val x = ox + (m?.optDouble(2) ?: 0.0).toFloat()
    val y = oy + (m?.optDouble(5) ?: 0.0).toFloat()
    if (n.optString("type") == "TEXT") {
        parseSceneText(n)?.let { out.add(Triple(x, y, it)) }
        return
    }
    val children = n.optJSONArray("children") ?: return
    for (i in 0 until children.length()) collectTexts(children.getJSONObject(i), x, y, out)
}

private fun collectButtons(n: JSONObject, ox: Float, oy: Float, out: MutableList<SceneToolbarButton>) {
    val m = n.optJSONArray("matrix")
    val x = ox + (m?.optDouble(2) ?: 0.0).toFloat()
    val y = oy + (m?.optDouble(5) ?: 0.0).toFloat()
    if (n.optString("name").startsWith("Button Group")) {
        val texts = ArrayList<Triple<Float, Float, SceneText>>()
        // The outer native button already applies n.matrix; glyph offsets are button-local.
        collectTexts(n, -(m?.optDouble(2) ?: 0.0).toFloat(), -(m?.optDouble(5) ?: 0.0).toFloat(), texts)
        if (texts.isNotEmpty()) out.add(SceneToolbarButton(x, y,
            n.optDouble("w").toFloat(), n.optDouble("h").toFloat(), findNode(n, "Tint") != null, texts))
        return
    }
    val children = n.optJSONArray("children") ?: return
    for (i in 0 until children.length()) collectButtons(children.getJSONObject(i), x, y, out)
}

private fun matrixCoordinate(node: JSONObject?, index: Int): Float? {
    val matrix = node?.optJSONArray("matrix") ?: return null
    return matrix.optDouble(index, Double.NaN).takeUnless { it.isNaN() }?.toFloat()
}

internal fun loadSceneToolbar(context: Context, file: String): SceneToolbar? {
    val scene = loadScene(context, file)
    val root = scene.optJSONObject("scene") ?: scene
    val toolbar = findNode(root, "Toolbar") ?: findNodePrefix(root, "Toolbar - Top") ?: return null
    val buttons = ArrayList<SceneToolbarButton>()
    toolbar.optJSONArray("children")?.let { children ->
        for (i in 0 until children.length()) collectButtons(children.getJSONObject(i), 0f, 0f, buttons)
    }
    val titleBlock = findNode(toolbar, "Title and Subtitle")
    // A 54pt toolbar owns its title block. Only the 125pt variant moves that block
    // into the scrollable large-title area below the navigation row.
    val isLargeTitle = toolbar.optDouble("h", 54.0) > 80.0
    // The nav title is a direct child (Toolbar - Top style); Title and Subtitle holds the large title.
    val navTitle = toolbar.optJSONArray("children")?.let { children ->
        (0 until children.length()).map(children::getJSONObject).firstOrNull { it.optString("name") == "Title" }
    }?.let { firstGlyphText(it) }
    val blockTitleNode = titleBlock?.let { findNode(it, "Title") }
    val blockSubtitleNode = titleBlock?.let { findNode(it, "Subtitle") }
    val titleNode = toolbar.optJSONArray("children")?.let { children ->
        (0 until children.length()).map(children::getJSONObject).firstOrNull { it.optString("name") == "Title" }
    } ?: if (!isLargeTitle) blockTitleNode else null
    val titleParentX = if (titleNode === blockTitleNode) matrixCoordinate(titleBlock, 2) ?: 0f else 0f
    val titleParentY = if (titleNode === blockTitleNode) matrixCoordinate(titleBlock, 5) ?: 0f else 0f
    val subtitle = if (!isLargeTitle) blockSubtitleNode?.let { firstGlyphText(it) } else null
    return SceneToolbar(
        buttons = buttons,
        title = navTitle ?: if (!isLargeTitle) blockTitleNode?.let { firstGlyphText(it) } else null,
        titleX = titleNode?.let { (matrixCoordinate(it, 2) ?: 0f) + titleParentX },
        titleY = titleNode?.let { (matrixCoordinate(it, 5) ?: 0f) + titleParentY },
        subtitle = subtitle,
        subtitleX = if (subtitle != null) (matrixCoordinate(blockSubtitleNode, 2) ?: 0f) + (matrixCoordinate(titleBlock, 2) ?: 0f) else null,
        subtitleY = if (subtitle != null) (matrixCoordinate(blockSubtitleNode, 5) ?: 0f) + (matrixCoordinate(titleBlock, 5) ?: 0f) else null,
        largeTitle = if (isLargeTitle) blockTitleNode?.let { firstGlyphText(it) } else null,
        largeSubtitle = if (isLargeTitle) blockSubtitleNode?.let { firstGlyphText(it) } else null,
    )
}

@Composable
internal fun rememberSceneToolbar(file: String): SceneToolbar? {
    val context = LocalContext.current
    return remember(file) { loadSceneToolbar(context, file) }
}

internal class SceneListItem(val label: String, val text: SceneText)
internal class SceneListGroup(val heading: SceneListItem, val rows: List<SceneListItem>)

/** Typed content only: native lists own hit testing, state and separators, not the scene viewer. */
@Composable
internal fun rememberSceneListGroups(file: String): List<SceneListGroup> {
    val context = LocalContext.current
    return remember(file) {
        val scene = loadScene(context, file)
        val content = findNode(scene.optJSONObject("scene") ?: scene, "Content Area")
        val children = requireNotNull(content?.optJSONArray("children"))
        val groups = ArrayList<SceneListGroup>()
        var heading: SceneListItem? = null
        var rows = ArrayList<SceneListItem>()
        for (i in 0 until children.length()) {
            val node = children.getJSONObject(i)
            val isHeading = node.optString("name") == "Section Title"
            val textNode = findNode(node, if (isHeading) "Section title" else "Title") ?: continue
            val text = parseSceneText(textNode) ?: continue
            val item = SceneListItem(textNode.optString("text"), text)
            if (isHeading) {
                heading?.let { groups.add(SceneListGroup(it, rows.toList())) }
                heading = item
                rows = ArrayList()
            } else rows.add(item)
        }
        heading?.let { groups.add(SceneListGroup(it, rows.toList())) }
        groups
    }
}

/** True when the original Content Area is empty (e.g. toolbar-only pages). */
@Composable
internal fun rememberSceneContentEmpty(file: String): Boolean {
    val context = LocalContext.current
    return remember(file) {
        val scene = loadScene(context, file)
        val root = scene.optJSONObject("scene") ?: scene
        val content = findNode(root, "Content Area")
        content != null && content.optJSONArray("children")?.length() ?: 0 == 0
    }
}

@Composable
internal fun rememberSceneText(file: String, nodeName: String): SceneText? {
    val context = LocalContext.current
    return remember(file, nodeName) { loadSceneText(context, file, nodeName) }
}

/** A specific occurrence of a TEXT node with the given source name. */
@Composable
internal fun rememberSceneTextOccurrence(
    file: String,
    nodeName: String,
    occurrence: Int,
): SceneText? {
    val context = LocalContext.current
    return remember(file, nodeName, occurrence) {
        val scene = loadScene(context, file)
        val root = scene.optJSONObject("scene") ?: scene
        findNodeOccurrence(root, nodeName, occurrence, intArrayOf(0))
            ?.takeIf {
                it.optString("type") == "TEXT" &&
                    (it.optJSONArray("glyphs")?.length() ?: 0) > 0
            }
            ?.let(::parseSceneText)
    }
}

/** First glyph-bearing TEXT node inside a named source subtree. */
@Composable
internal fun rememberSceneSubtreeText(file: String, nodeName: String): SceneText? {
    val context = LocalContext.current
    return remember(file, nodeName) {
        val scene = loadScene(context, file)
        val root = scene.optJSONObject("scene") ?: scene
        findNode(root, nodeName)?.let(::firstGlyphText)
    }
}

@Composable
internal fun rememberSceneSubtreeNamedText(
    file: String,
    subtreeName: String,
    nodeName: String,
): SceneText? {
    val context = LocalContext.current
    return remember(file, subtreeName, nodeName) {
        val scene = loadScene(context, file)
        val root = scene.optJSONObject("scene") ?: scene
        findNode(root, subtreeName)?.let { subtree ->
            findNode(subtree, nodeName)?.let(::firstGlyphText)
        }
    }
}

/**
 * FigmaSceneView paints a TEXT drop shadow on the node outline (its w x h rect), never on the glyphs, so the
 * original label sits on a soft blob. Baked into a software bitmap because a hardware canvas ignores BlurMaskFilter.
 */
private fun textShadowBitmap(text: SceneText, density: Float): Pair<ImageBitmap, Int> {
    val pad = (text.shadowBlur * 2.5f * density).roundToInt().coerceAtLeast(1)
    val bodyW = text.width * density
    val bodyH = text.height * density
    val bitmap = Bitmap.createBitmap(bodyW.roundToInt() + pad * 2, bodyH.roundToInt() + pad * 2,
        Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        maskFilter = BlurMaskFilter(text.shadowBlur * density, BlurMaskFilter.Blur.NORMAL)
    }
    val top = pad + text.shadowOffsetY * density
    android.graphics.Canvas(bitmap).drawRect(pad.toFloat(), top, pad + bodyW, top + bodyH, paint)
    return bitmap.asImageBitmap() to pad
}

/** Original glyph outlines of a whole TEXT node, drawn inside its own pt box (1dp = 1pt here). */
@Composable
internal fun SceneGlyphText(text: SceneText, tint: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val shadow = if (text.shadowBlur > 0f) remember(text, density) { textShadowBitmap(text, density) } else null
    Canvas(modifier.size(text.width.dp, text.height.dp)) {
        val factor = size.width / text.width
        shadow?.let { (bitmap, pad) ->
            drawImage(bitmap, dstOffset = IntOffset(-pad, -pad), alpha = text.shadowAlpha)
        }
        text.glyphs.forEach { glyph ->
            withTransform({
                scale(factor, factor, Offset.Zero)
                translate(glyph.x, glyph.y)
                rotate(glyph.rotation, Offset.Zero)
                scale(glyph.size, -glyph.size, Offset.Zero)
            }) {
                drawPath(glyph.path, tint)
            }
        }
    }
}

/**
 * Draw a source TEXT node into an externally sized viewport without scaling the source box to that viewport.
 * Useful when Figma intentionally places a larger glyph box outside a clipped parent (for example 70×70 at -2,-2
 * inside a 66×66 circular action button).
 */
@Composable
internal fun SceneGlyphTextViewport(
    text: SceneText,
    tint: Color,
    modifier: Modifier = Modifier,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
) {
    val density = LocalDensity.current.density
    Canvas(modifier) {
        text.glyphs.forEach { glyph ->
            withTransform({
                translate(offsetX * density, offsetY * density)
                scale(density, density, Offset.Zero)
                translate(glyph.x, glyph.y)
                rotate(glyph.rotation, Offset.Zero)
                scale(glyph.size, -glyph.size, Offset.Zero)
            }) {
                drawPath(glyph.path, tint)
            }
        }
    }
}

/** Draws the glyph inside the original TEXT node box; size the modifier to the node's w×h in dp. */
@Composable
internal fun SceneGlyphIcon(file: String, nodeName: String, tint: Color, modifier: Modifier = Modifier,
    occurrence: Int = 0) {
    val context = LocalContext.current
    val glyph = remember(file, nodeName, occurrence) { loadSceneGlyph(context, file, nodeName, occurrence) }
    if (glyph == null) {
        Box(modifier)
        return
    }
    Canvas(modifier) {
        // Glyph coordinates are pt in the original node box; preserve each source node's width.
        // Sheets' Symbol is 36pt wide, while older symbols use 32pt.
        val factor = size.width / glyph.nodeWidth.coerceAtLeast(1f)
        withTransform({
            scale(factor, factor, Offset.Zero)
            translate(glyph.x, glyph.y)
            rotate(glyph.rotation, Offset.Zero)
            scale(glyph.size, -glyph.size, Offset.Zero)
        }) {
            drawPath(glyph.path, tint)
        }
    }
}
