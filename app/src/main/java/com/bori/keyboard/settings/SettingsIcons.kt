package com.bori.keyboard.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Small icons for Settings, built from 24×24 path data so no icon library (tens of MB to compile
 * on the phone) is needed. Filled paths are Material Icons (Apache-2.0, see ATTRIBUTION.md);
 * Swipe and Emoji are our own outline drawings. A path that fails to parse gives an empty icon
 * instead of a crash.
 */
internal object SettingsIcons {
    val Back = filled("M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val Chevron = filled("M8.59,16.59L13.17,12 8.59,7.41 10,6l6,6 -6,6 -1.41,-1.41z")
    val Info = filled(
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z",
    )
    val Palette = filled(
        "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c0.83,0 1.5,-0.67 1.5,-1.5 0,-0.39 -0.15,-0.74 -0.39,-1.01 " +
            "-0.23,-0.26 -0.38,-0.61 -0.38,-0.99 0,-0.83 0.67,-1.5 1.5,-1.5H16c2.76,0 5,-2.24 5,-5 0,-4.42 " +
            "-4.03,-8 -9,-8zM6.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,9 6.5,9 8,9.67 8,10.5 7.33,12 6.5,12zM9.5,8" +
            "C8.67,8 8,7.33 8,6.5S8.67,5 9.5,5s1.5,0.67 1.5,1.5S10.33,8 9.5,8zM14.5,8c-0.83,0 -1.5,-0.67 " +
            "-1.5,-1.5S13.67,5 14.5,5s1.5,0.67 1.5,1.5S15.33,8 14.5,8zM17.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5" +
            "S16.67,9 17.5,9s1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z",
    )
    val Keyboard = filled(
        "M20,5H4c-1.1,0 -1.99,0.9 -1.99,2L2,17c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2z" +
            "M11,8h2v2h-2V8zM11,11h2v2h-2v-2zM8,8h2v2H8V8zM8,11h2v2H8v-2zM7,13H5v-2h2v2zM7,10H5V8h2v2z" +
            "M16,17H8v-2h8v2zM16,13h-2v-2h2v2zM16,10h-2V8h2v2zM19,13h-2v-2h2v2zM19,10h-2V8h2v2z",
    )
    val Height = filled("M13,6.99h3L12,3 8,6.99h3v10.02H8L12,21l4,-3.99h-3z")
    val Spellcheck = filled(
        "M12.45,16h2.09L9.43,3H7.57L2.46,16h2.09l1.12,-3h5.64l1.14,3zM6.43,11L8.5,5.48 10.57,11H6.43z" +
            "M21.59,11.59l-8.09,8.09L9.83,16l-1.41,1.41 5.09,5.09L23,13l-1.41,-1.41z",
    )
    val Lock = filled(
        "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 " +
            "2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z" +
            "M15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z",
    )
    val Build = filled(
        "M22.7,19l-9.1,-9.1c0.9,-2.3 0.4,-5 -1.5,-6.9 -2,-2 -5,-2.4 -7.4,-1.3L9,6 6,9 1.6,4.7C0.4,7.1 0.9,10.1 " +
            "2.9,12.1c1.9,1.9 4.6,2.4 6.9,1.5l9.1,9.1c0.4,0.4 1,0.4 1.4,0l2.3,-2.3c0.5,-0.4 0.5,-1.1 0.1,-1.4z",
    )
    val Search = filled(
        "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 " +
            "9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 " +
            "5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
    )
    /** Our own: a swipe stroke across three keys. */
    val Swipe = outline("M3,17 C6,7 9,6 12,12 S17,18 21,6 M19,6 L21,6 L21,8")
    /** Our own: a smiling face. */
    val Emoji = outline(
        "M12,3 A9,9 0 1 1 11.99,3 Z M8.5,14.5 Q12,18 15.5,14.5 M9,9.5 L9,10 M15,9.5 L15,10",
    )

    private fun filled(d: String): ImageVector = build(d) { nodes ->
        addPath(pathData = nodes, fill = SolidColor(Color.Black))
    }

    private fun outline(d: String): ImageVector = build(d) { nodes ->
        addPath(
            pathData = nodes,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun build(d: String, add: ImageVector.Builder.(List<androidx.compose.ui.graphics.vector.PathNode>) -> Unit): ImageVector {
        val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        runCatching { b.add(addPathNodes(d)) }
        return b.build()
    }
}
