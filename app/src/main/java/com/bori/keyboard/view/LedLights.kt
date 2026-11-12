package com.bori.keyboard.view

import android.graphics.Color
import com.bori.keyboard.layout.PlacedKey
import kotlin.math.sin

/**
 * LED lighting for the keys (Stage 3.5): rainbow wave, breathing, reactive (keys light up when
 * pressed and fade), colour cycle and row gradient. KeyboardView asks [colorFor] for every key
 * while drawing; the alpha of the result is the strength (0 = no light on that key). Works on
 * top of any theme. No allocations per frame except the reactive map entry on a key press.
 */
class LedLights {
    enum class Mode(val id: String, val title: String, val animated: Boolean) {
        OFF("off", "Off", false),
        WAVE("wave", "Rainbow wave", true),
        BREATHING("breathing", "Breathing", true),
        REACTIVE("reactive", "Light up on press", false),
        CYCLE("cycle", "Colour cycle", true),
        ROWS("rows", "Row rainbow", false);

        companion object {
            fun of(id: String?) = entries.firstOrNull { it.id == id } ?: OFF
        }
    }

    enum class Target(val id: String, val title: String) {
        KEYS("keys", "Keys"),
        LETTERS("letters", "Letters"),
        OUTLINE("outline", "Outlines");

        companion object {
            fun of(id: String?) = entries.firstOrNull { it.id == id } ?: KEYS
        }
    }

    var mode = Mode.OFF
        private set
    var target = Target.KEYS
        private set
    /** Fixed light colour; null = rainbow across the keyboard. */
    private var color: Int? = null
    private var speed = 1f
    private var brightness = 0.6f
    /** False in password fields and with system animations off. */
    private var allowed = true
    private val hsv = floatArrayOf(0f, 0.85f, 1f)
    private val pressedAt = HashMap<PlacedKey, Long>()

    fun configure(mode: Mode, target: Target, color: Int?, speed: Float, brightness: Float, allowed: Boolean) {
        this.mode = mode
        this.target = target
        this.color = color
        this.speed = speed.coerceIn(0.2f, 3f)
        this.brightness = brightness.coerceIn(0.1f, 1f)
        this.allowed = allowed
        pressedAt.clear()
    }

    val active: Boolean get() = allowed && mode != Mode.OFF

    /** Another frame is needed: an animated mode, or reactive keys still fading. */
    val animating: Boolean get() = active && (mode.animated || pressedAt.isNotEmpty())

    fun onPress(pk: PlacedKey, now: Long) {
        if (active && mode == Mode.REACTIVE) pressedAt[pk] = now
    }

    /** Geometry changed: the old keys are gone. */
    fun clearPresses() = pressedAt.clear()

    /** The light on [pk] (in row [row] of [rows]); x is its centre, [width] the keyboard's. */
    fun colorFor(pk: PlacedKey, row: Int, rows: Int, x: Float, width: Float, now: Long): Int {
        if (!active) return 0
        val t = now / 1000f * speed
        var strength = 1f
        val c = when (mode) {
            Mode.OFF -> return 0
            Mode.WAVE -> hue(x / width.coerceAtLeast(1f) * 360f + t * 120f)
            Mode.CYCLE -> hue(t * 60f)
            Mode.ROWS -> hue(row * 300f / maxOf(1, rows - 1))
            Mode.BREATHING -> {
                strength = 0.2f + 0.8f * (0.5f + 0.5f * sin(t * 2.5f))
                color ?: hue(x / width.coerceAtLeast(1f) * 360f)
            }
            Mode.REACTIVE -> {
                val at = pressedAt[pk] ?: return 0
                val age = (now - at) / (700f / speed)
                if (age >= 1f) {
                    pressedAt.remove(pk)
                    return 0
                }
                strength = 1f - age
                color ?: hue(x / width.coerceAtLeast(1f) * 360f)
            }
        }
        val a = (brightness * strength * 255f).toInt().coerceIn(0, 255)
        return (c and 0x00FFFFFF) or (a shl 24)
    }

    private fun hue(h: Float): Int {
        hsv[0] = ((h % 360f) + 360f) % 360f
        return Color.HSVToColor(hsv)
    }

    companion object {
        /** [base] with [light] mixed in by the light's alpha; see-through keys get at least that alpha. */
        fun mix(base: Int, light: Int): Int {
            val a = Color.alpha(light) / 255f
            fun ch(b: Int, l: Int) = (b * (1 - a) + l * a).toInt()
            val alpha = maxOf(Color.alpha(base), Color.alpha(light))
            return Color.argb(
                alpha,
                ch(Color.red(base), Color.red(light)),
                ch(Color.green(base), Color.green(light)),
                ch(Color.blue(base), Color.blue(light)),
            )
        }
    }
}
