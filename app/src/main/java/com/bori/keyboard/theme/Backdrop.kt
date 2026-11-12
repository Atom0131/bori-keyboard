package com.bori.keyboard.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.bori.keyboard.ime.DiagLog
import kotlinx.serialization.Serializable
import java.io.File
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A picture behind the whole keyboard (strip + keys), as saved in a theme or chosen by the user
 * in Settings → Themes → Background (Stage 3.4). All fields optional, like the rest of ThemeSpec.
 */
@Serializable
data class BackgroundSpec(
    /** none (plain theme colour), gradient, scene or photo. */
    val type: String = "none",
    /** gradient: 2 or more colours, same syntax as ThemeColors. */
    val colors: List<String>? = null,
    /** gradient direction in degrees: 90 top to bottom, 0 left to right, 45 and 135 diagonals. */
    val angle: Int = 90,
    /** scene: mountains, beach, skyline or dots. */
    val scene: String? = null,
    /** photo: file name, next to the theme or (user's pick) in filesDir/backgrounds. */
    val image: String? = null,
    /** photo: 1 (whole photo fits) to 3. */
    val zoom: Float = 1f,
    /** photo: which part shows when it's cropped, 0 to 1. */
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    /** -0.8 (darker) to 0.6 (lighter). */
    val brightness: Float = 0f,
    /** 0 (sharp) to 1 (very soft). */
    val blur: Float = 0f,
    /** How strongly the picture shows over the theme's background colour, 0.2 to 1. */
    val opacity: Float = 1f,
)

/** A resolved background: everything needed to draw it (views read only this). */
data class Backdrop(
    val type: String,
    val colors: List<Int>,
    val angle: Int,
    val scene: String,
    val night: Boolean,
    val imageFile: File?,
    /** The photo's last-modified time, so a replaced photo is never served from the cache. */
    val imageStamp: Long,
    val zoom: Float,
    val focusX: Float,
    val focusY: Float,
    val brightness: Float,
    val blur: Float,
    val opacity: Float,
    /** The theme's background colour, under the picture. */
    val baseColor: Int,
) {
    companion object {
        val SCENES = listOf("mountains" to "Mountains", "beach" to "Beach", "skyline" to "City", "yunque" to "El Yunque", "dots" to "Dot grid")

        /** Ready-made gradients for Settings (the editor in 3.7 adds custom colours). */
        val GRADIENTS = listOf(
            listOf("#FF9A8B", "#FF6A88", "#FF99AC"),
            listOf("#2E3192", "#1BFFFF"),
            listOf("#0F2027", "#203A43", "#2C5364"),
            listOf("#F7971E", "#FFD200"),
            listOf("#8E2DE2", "#4A00E0"),
            listOf("#00B09B", "#96C93D"),
            listOf("#232526", "#414345"),
            listOf("#FBD3E9", "#BB377D"),
            listOf("#89F7FE", "#66A6FF"),
            listOf("#0B486B", "#F56217"),
            listOf("#C6FFDD", "#FBD786", "#F7797D"),
            listOf("#41B3E5", "#FFFFFF", "#E4002B"),
        )

        fun resolve(context: Context, s: BackgroundSpec?, dir: File, night: Boolean, base: Int): Backdrop? {
            if (s == null) return null
            val colors = s.colors?.mapNotNull { ThemeResolver.parseColor(context, it) }.orEmpty()
            val file = s.image?.let { File(dir, File(it).name) }?.takeIf { it.isFile }
            val type = when (s.type) {
                "gradient" -> if (colors.size >= 2) "gradient" else return null
                "scene" -> "scene"
                "photo" -> if (file != null) "photo" else return null
                else -> return null
            }
            return Backdrop(
                type, colors, s.angle, s.scene ?: "mountains", night, file, file?.lastModified() ?: 0L,
                s.zoom.coerceIn(1f, 3f), s.focusX.coerceIn(0f, 1f), s.focusY.coerceIn(0f, 1f),
                s.brightness.coerceIn(-0.8f, 0.6f), s.blur.coerceIn(0f, 1f), s.opacity.coerceIn(0.2f, 1f), base,
            )
        }
    }
}

/**
 * Draws backdrops into bitmaps the size of the view, cached, so a keyboard draws one bitmap per
 * frame. Main thread only. Photos are decoded at about the size needed (inSampleSize).
 */
object BackdropRenderer {
    private const val MAX_CACHED = 5
    private val cache = LinkedHashMap<String, Bitmap>(8, 0.75f, true)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun get(context: Context, b: Backdrop, w: Int, h: Int): Bitmap? {
        if (w <= 0 || h <= 0) return null
        val key = "$b/$w/$h"
        cache[key]?.let { return it }
        val bmp = runCatching { render(context, b, w, h) }
            .onFailure { DiagLog.event("backdrop ${b.type} failed: $it") }
            .getOrNull() ?: return null
        cache[key] = bmp
        // Old bitmaps are dropped, never recycled: a view may still be drawing one.
        while (cache.size > MAX_CACHED) {
            val it = cache.keys.iterator()
            it.next()
            it.remove()
        }
        return bmp
    }

    /** Average colour of the backdrop, for picking readable text (see ThemeResolver). */
    fun averageColor(context: Context, b: Backdrop): Int {
        val bmp = get(context, b, 48, 24) ?: return b.baseColor
        var r = 0L
        var g = 0L
        var bl = 0L
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (p in px) {
            r += Color.red(p); g += Color.green(p); bl += Color.blue(p)
        }
        val n = px.size.coerceAtLeast(1)
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    private fun render(context: Context, b: Backdrop, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(b.baseColor)
        val layered = b.opacity < 1f
        if (layered) c.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), (b.opacity * 255).toInt())
        when (b.type) {
            "gradient" -> drawGradient(c, b, w, h)
            "scene" -> Scenes.draw(c, b.scene, w, h, b.night)
            "photo" -> drawPhoto(c, b, w, h)
        }
        if (layered) c.restore()
        if (b.type != "photo" && b.brightness != 0f) {
            // Photos get brightness in their colour filter; others get a wash on top.
            paint.shader = null
            paint.colorFilter = null
            paint.color = if (b.brightness < 0f) Color.BLACK else Color.WHITE
            paint.alpha = (kotlin.math.abs(b.brightness) * 255).toInt()
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.alpha = 255
        }
        return out
    }

    private fun drawGradient(c: Canvas, b: Backdrop, w: Int, h: Int) {
        val a = Math.toRadians(b.angle.toDouble())
        val cx = w / 2f
        val cy = h / 2f
        // Half the length of the line through the centre at this angle that spans the box.
        val half = (kotlin.math.abs(cos(a)) * w + kotlin.math.abs(sin(a)) * h).toFloat() / 2f
        val dx = (cos(a) * half).toFloat()
        val dy = (sin(a) * half).toFloat()
        paint.colorFilter = null
        paint.shader = LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, b.colors.toIntArray(), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }

    private fun drawPhoto(c: Canvas, b: Backdrop, w: Int, h: Int) {
        val f = b.imageFile ?: return
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        val iw = bounds.outWidth
        val ih = bounds.outHeight
        if (iw <= 0 || ih <= 0) return
        // Visible part of the photo (in photo pixels): cover the box, then zoom in.
        val scale = max(w.toFloat() / iw, h.toFloat() / ih) * b.zoom
        val srcW = w / scale
        val srcH = h / scale
        var sample = 1
        // Decode at no less than the size it's drawn at (blur needs even less).
        while (srcW / (sample * 2) >= w && srcH / (sample * 2) >= h) sample *= 2
        val img = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
        val sx = img.width.toFloat() / iw
        val sy = img.height.toFloat() / ih
        val left = (iw - srcW) * b.focusX
        val top = (ih - srcH) * b.focusY
        val src = Rect((left * sx).toInt(), (top * sy).toInt(), ((left + srcW) * sx).toInt(), ((top + srcH) * sy).toInt())
        paint.shader = null
        paint.colorFilter = brightnessFilter(b.brightness)
        if (b.blur > 0.01f) {
            // Soft blur: draw small, then stretch back up with filtering (works on every Android).
            val k = 1f + b.blur * 15f
            val sw = (w / k).toInt().coerceAtLeast(2)
            val sh = (h / k).toInt().coerceAtLeast(2)
            val small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            Canvas(small).drawBitmap(img, src, Rect(0, 0, sw, sh), paint)
            paint.colorFilter = null
            c.drawBitmap(small, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
            small.recycle()
        } else {
            c.drawBitmap(img, src, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
        }
        paint.colorFilter = null
        img.recycle()
    }

    private fun brightnessFilter(v: Float): ColorMatrixColorFilter? {
        if (v == 0f) return null
        val m = if (v < 0f) {
            val s = 1f + v
            ColorMatrix(floatArrayOf(s, 0f, 0f, 0f, 0f, 0f, s, 0f, 0f, 0f, 0f, 0f, s, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        } else {
            val s = 1f - v
            val t = 255f * v
            ColorMatrix(floatArrayOf(s, 0f, 0f, 0f, t, 0f, s, 0f, 0f, t, 0f, 0f, s, 0f, t, 0f, 0f, 0f, 1f, 0f))
        }
        return ColorMatrixColorFilter(m)
    }
}

/** Behind the input view: draws the cached backdrop bitmap. */
class BackdropDrawable(private val context: Context, private val b: Backdrop) : Drawable() {
    override fun draw(canvas: Canvas) {
        val r = bounds
        val bmp = BackdropRenderer.get(context, b, r.width(), r.height())
        if (bmp != null) canvas.drawBitmap(bmp, r.left.toFloat(), r.top.toFloat(), null) else canvas.drawColor(b.baseColor)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}

/** Procedural scenes, our own drawings (no image files), in a day and a night version. */
internal object Scenes {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(c: Canvas, id: String, w: Int, h: Int, night: Boolean) {
        val W = w.toFloat()
        val H = h.toFloat()
        val rnd = Random(id.hashCode().toLong())
        when (id) {
            "beach" -> beach(c, W, H, night, rnd)
            "skyline" -> skyline(c, W, H, night, rnd)
            "yunque" -> yunque(c, W, H, night, rnd)
            "dots" -> dots(c, W, H, night)
            else -> mountains(c, W, H, night, rnd)
        }
        p.shader = null
    }

    private fun sky(c: Canvas, W: Float, H: Float, top: Int, bottom: Int) {
        p.shader = LinearGradient(0f, 0f, 0f, H, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p)
        p.shader = null
    }

    private fun stars(c: Canvas, W: Float, H: Float, rnd: Random, n: Int) {
        p.color = Color.WHITE
        repeat(n) {
            p.alpha = 90 + rnd.nextInt(150)
            c.drawCircle(rnd.nextFloat() * W, rnd.nextFloat() * H * 0.6f, 0.6f + rnd.nextFloat() * 1.6f, p)
        }
        p.alpha = 255
    }

    /** A jagged ridge across the width, [base] = average height from the top. */
    private fun ridge(c: Canvas, W: Float, H: Float, base: Float, rough: Float, color: Int, rnd: Random) {
        val path = Path()
        path.moveTo(0f, H)
        var x = 0f
        var y = base
        path.lineTo(0f, y)
        while (x < W) {
            x += W * (0.04f + rnd.nextFloat() * 0.08f)
            y = (base + (rnd.nextFloat() - 0.5f) * rough).coerceIn(H * 0.05f, H)
            path.lineTo(x, y)
        }
        path.lineTo(W, H)
        path.close()
        p.color = color
        c.drawPath(path, p)
    }

    private fun mountains(c: Canvas, W: Float, H: Float, night: Boolean, rnd: Random) {
        if (night) {
            sky(c, W, H, Color.rgb(11, 20, 48), Color.rgb(43, 58, 103))
            stars(c, W, H, rnd, 70)
            p.color = Color.rgb(236, 236, 214)
            c.drawCircle(W * 0.8f, H * 0.18f, H * 0.07f, p)
        } else {
            sky(c, W, H, Color.rgb(155, 199, 232), Color.rgb(240, 226, 210))
            p.color = Color.rgb(255, 214, 140)
            c.drawCircle(W * 0.78f, H * 0.2f, H * 0.08f, p)
        }
        val layers = if (night) {
            intArrayOf(Color.rgb(42, 53, 88), Color.rgb(30, 39, 69), Color.rgb(20, 27, 51))
        } else {
            intArrayOf(Color.rgb(124, 147, 181), Color.rgb(91, 115, 150), Color.rgb(62, 86, 119))
        }
        ridge(c, W, H, H * 0.38f, H * 0.28f, layers[0], rnd)
        ridge(c, W, H, H * 0.55f, H * 0.22f, layers[1], rnd)
        ridge(c, W, H, H * 0.72f, H * 0.14f, layers[2], rnd)
    }

    private fun beach(c: Canvas, W: Float, H: Float, night: Boolean, rnd: Random) {
        val horizon = H * 0.42f
        val shore = H * 0.7f
        if (night) {
            sky(c, W, horizon, Color.rgb(12, 18, 46), Color.rgb(48, 52, 98))
            stars(c, W, horizon, rnd, 50)
            p.color = Color.rgb(240, 236, 210)
            c.drawCircle(W * 0.25f, horizon * 0.4f, H * 0.06f, p)
        } else {
            sky(c, W, horizon, Color.rgb(120, 196, 238), Color.rgb(214, 238, 250))
            p.color = Color.rgb(255, 224, 150)
            c.drawCircle(W * 0.25f, horizon * 0.45f, H * 0.07f, p)
        }
        p.shader = LinearGradient(
            0f, horizon, 0f, shore,
            if (night) Color.rgb(22, 40, 78) else Color.rgb(37, 150, 200),
            if (night) Color.rgb(16, 58, 88) else Color.rgb(60, 196, 210),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, horizon, W, shore, p)
        p.shader = null
        // Foam line where the sea meets the sand.
        val path = Path()
        path.moveTo(0f, shore)
        var x = 0f
        while (x < W) {
            val nx = x + W * 0.08f
            path.quadTo(x + W * 0.04f, shore - H * 0.03f * (0.5f + rnd.nextFloat()), nx, shore)
            x = nx
        }
        path.lineTo(W, H)
        path.lineTo(0f, H)
        path.close()
        p.color = if (night) Color.rgb(96, 88, 76) else Color.rgb(242, 215, 160)
        c.drawPath(path, p)
    }

    private fun skyline(c: Canvas, W: Float, H: Float, night: Boolean, rnd: Random) {
        if (night) {
            sky(c, W, H, Color.rgb(10, 15, 44), Color.rgb(46, 36, 92))
            stars(c, W, H * 0.5f, rnd, 40)
        } else {
            sky(c, W, H, Color.rgb(255, 179, 124), Color.rgb(106, 76, 147))
        }
        for ((layer, base) in listOf(0.35f, 0.5f).withIndex()) {
            var x = -W * 0.02f
            while (x < W) {
                val bw = W * (0.05f + rnd.nextFloat() * 0.07f)
                val top = H * (base + rnd.nextFloat() * 0.3f)
                p.color = when {
                    night && layer == 0 -> Color.rgb(28, 30, 64)
                    night -> Color.rgb(16, 16, 40)
                    layer == 0 -> Color.rgb(120, 82, 140)
                    else -> Color.rgb(70, 46, 96)
                }
                c.drawRect(x, top, x + bw, H, p)
                if (night && layer == 1) {
                    // Lit windows.
                    p.color = Color.rgb(255, 214, 120)
                    var wy = top + H * 0.03f
                    while (wy < H - H * 0.04f) {
                        var wx = x + bw * 0.15f
                        while (wx < x + bw * 0.85f) {
                            if (rnd.nextFloat() < 0.35f) c.drawRect(wx, wy, wx + bw * 0.12f, wy + H * 0.02f, p)
                            wx += bw * 0.25f
                        }
                        wy += H * 0.05f
                    }
                }
                x += bw + W * 0.004f
            }
        }
    }

    /**
     * El Yunque rainforest (the Coquí theme): misty green peaks, ferns along the bottom; sun by
     * day, moon, stars and fireflies at night.
     */
    private fun yunque(c: Canvas, W: Float, H: Float, night: Boolean, rnd: Random) {
        if (night) {
            sky(c, W, H, Color.rgb(6, 20, 26), Color.rgb(16, 44, 38))
            stars(c, W, H * 0.6f, rnd, 45)
            p.color = Color.rgb(236, 240, 214)
            c.drawCircle(W * 0.82f, H * 0.16f, H * 0.06f, p)
        } else {
            sky(c, W, H, Color.rgb(191, 227, 242), Color.rgb(232, 244, 236))
            p.color = Color.rgb(255, 240, 190)
            p.alpha = 90
            c.drawCircle(W * 0.8f, H * 0.17f, H * 0.12f, p)
            p.alpha = 255
            c.drawCircle(W * 0.8f, H * 0.17f, H * 0.07f, p)
        }
        val layers = if (night) {
            intArrayOf(Color.rgb(22, 54, 42), Color.rgb(14, 40, 30), Color.rgb(8, 28, 20))
        } else {
            intArrayOf(Color.rgb(150, 192, 160), Color.rgb(104, 160, 112), Color.rgb(62, 122, 68))
        }
        ridge(c, W, H, H * 0.36f, H * 0.3f, layers[0], rnd)
        ridge(c, W, H, H * 0.52f, H * 0.22f, layers[1], rnd)
        ridge(c, W, H, H * 0.7f, H * 0.12f, layers[2], rnd)
        // Ferns rising from the bottom edge.
        val fernColor = if (night) Color.rgb(5, 18, 11) else Color.rgb(38, 92, 44)
        var x = W * 0.02f
        while (x < W) {
            val len = H * (0.22f + rnd.nextFloat() * 0.2f)
            val lean = (rnd.nextFloat() - 0.5f) * len * 0.9f
            fern(c, x, H, len, lean, fernColor, rnd)
            x += W * (0.06f + rnd.nextFloat() * 0.07f)
        }
        if (night) {
            // Fireflies: a soft halo and a bright centre.
            repeat(26) {
                val fx = rnd.nextFloat() * W
                val fy = H * (0.3f + rnd.nextFloat() * 0.65f)
                val r = H * (0.006f + rnd.nextFloat() * 0.006f)
                p.color = Color.rgb(214, 255, 120)
                p.alpha = 55
                c.drawCircle(fx, fy, r * 3.5f, p)
                p.alpha = 230
                c.drawCircle(fx, fy, r, p)
            }
            p.alpha = 255
        }
    }

    /** One fern frond: a curved stem from (x0, y0) with leaflets on both sides, shorter toward the tip. */
    private fun fern(c: Canvas, x0: Float, y0: Float, len: Float, lean: Float, color: Int, rnd: Random) {
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = (len * 0.025f).coerceAtLeast(1f)
        val cx = x0 + lean * 0.2f
        val cy = y0 - len * 0.75f
        val ex = x0 + lean
        val ey = y0 - len
        val stem = Path()
        stem.moveTo(x0, y0)
        stem.quadTo(cx, cy, ex, ey)
        c.drawPath(stem, p)
        val n = 9 + rnd.nextInt(4)
        p.strokeWidth = (len * 0.018f).coerceAtLeast(1f)
        for (i in 1 until n) {
            val t = i / n.toFloat()
            val u = 1f - t
            // Point and direction on the quadratic curve.
            val px = u * u * x0 + 2f * u * t * cx + t * t * ex
            val py = u * u * y0 + 2f * u * t * cy + t * t * ey
            var dx = 2f * u * (cx - x0) + 2f * t * (ex - cx)
            var dy = 2f * u * (cy - y0) + 2f * t * (ey - cy)
            val d = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            dx /= d
            dy /= d
            val leaf = len * (0.05f + 0.2f * u)
            // Leaflets point outward and a little up the stem.
            for (side in intArrayOf(-1, 1)) {
                val lx = -dy * side * 0.85f + dx * 0.5f
                val ly = dx * side * 0.85f + dy * 0.5f
                c.drawLine(px, py, px + lx * leaf, py + ly * leaf, p)
            }
        }
        p.style = Paint.Style.FILL
        p.strokeWidth = 0f
    }

    private fun dots(c: Canvas, W: Float, H: Float, night: Boolean) {
        c.drawColor(if (night) Color.rgb(14, 14, 14) else Color.rgb(242, 242, 242))
        p.color = if (night) Color.rgb(58, 58, 58) else Color.rgb(205, 205, 205)
        val step = (H / 28f).coerceAtLeast(6f)
        val r = step * 0.16f
        var y = step / 2f
        while (y < H) {
            var x = step / 2f
            while (x < W) {
                c.drawCircle(x, y, r, p)
                x += step
            }
            y += step
        }
    }
}
