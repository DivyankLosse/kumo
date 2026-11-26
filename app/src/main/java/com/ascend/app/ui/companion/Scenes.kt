package com.ascend.app.ui.companion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.ascend.app.data.local.ParkPlantEntity
import com.ascend.app.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Layered mountain ridge. [offset] scrolls the layers at different speeds (parallax) so the bear
 * appears to walk while staying centred.
 */
@Composable
fun RidgeBackdrop(modifier: Modifier, dark: Boolean, offset: Float) {
    val far = if (dark) Color(0xFF2C3760) else Color(0xFFB9CFEA)
    val mid = if (dark) Color(0xFF232C4E) else Color(0xFF9DBBE0)
    val near = if (dark) Color(0xFF1A2140) else Color(0xFFF3E3A6)
    val sun = if (dark) Color(0xFFFFE08A) else Color(0xFFFFD36A)
    Canvas(modifier.clipToBounds()) {
        val c = Offset(size.width * 0.78f, size.height * 0.32f)
        val r = size.height * 0.16f
        drawCircle(Brush.radialGradient(listOf(sun.copy(alpha = 0.35f), sun.copy(alpha = 0f)), c, r * 2.2f), r * 2.2f, c)
        drawCircle(sun, r, c)
        drawHills(far, offset * 0.15f, size.height * 0.45f, 0.9f, 3f)
        drawHills(mid, offset * 0.35f, size.height * 0.62f, 0.6f, 2.2f)
        drawHills(near, offset, size.height * 0.82f, 0.25f, 1.6f)
    }
}

private fun DrawScope.drawHills(color: Color, offset: Float, baseY: Float, amp: Float, waves: Float) {
    val w = size.width
    val p = Path()
    p.moveTo(0f, size.height)
    val steps = 48
    for (i in 0..steps) {
        val x = w * i / steps
        val phase = ((x + offset) / w) * waves * 2 * PI
        val y = baseY - (sin(phase) * 0.5 + sin(phase * 2.3 + 1.1) * 0.25).toFloat() * size.height * 0.12f * amp
        p.lineTo(x, y)
    }
    p.lineTo(w, size.height)
    p.close()
    drawPath(p, color)
}

private data class Particle(val angle: Float, val speed: Float, val color: Color, val size: Float, val spin: Float, val rect: Boolean)

/** One-shot confetti burst from the centre top; skipped entirely under reduce-motion. */
@Composable
fun ConfettiBurst(modifier: Modifier, trigger: Any) {
    if (LocalReduceMotion.current) return
    val colors = listOf(Color(0xFFFFE08A), Color(0xFFA8C6E7), Color(0xFFF4B6A6), Color(0xFFB5D8B0), Color(0xFFC9B8F0), Color(0xFFFFF7D6))
    val particles = remember(trigger) {
        val r = Random(trigger.hashCode())
        List(90) {
            Particle(
                angle = (-PI / 2 + (r.nextFloat() - 0.5f) * PI * 1.1).toFloat(),
                speed = 0.55f + r.nextFloat() * 0.9f,
                color = colors[r.nextInt(colors.size)],
                size = 6f + r.nextFloat() * 8f,
                spin = (r.nextFloat() - 0.5f) * 900f,
                rect = r.nextBoolean(),
            )
        }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(2200, easing = LinearEasing)) }
    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val origin = Offset(size.width / 2, size.height * 0.42f)
        particles.forEach { pt ->
            val v = pt.speed * size.height * 0.9f
            val x = origin.x + cos(pt.angle) * v * t
            val y = origin.y + sin(pt.angle) * v * t + 0.5f * size.height * 1.6f * t * t
            val alpha = (1f - t).coerceIn(0f, 1f)
            rotate(pt.spin * t, Offset(x, y)) {
                if (pt.rect) drawRect(pt.color.copy(alpha = alpha), Offset(x - pt.size / 2, y - pt.size / 4), Size(pt.size, pt.size / 2))
                else drawCircle(pt.color.copy(alpha = alpha), pt.size / 2.6f, Offset(x, y))
            }
        }
    }
}

/** Park scene: one plant per completed focus session, placed where it was planted. */
@Composable
fun ParkScene(modifier: Modifier, plants: List<ParkPlantEntity>, dark: Boolean) {
    val grass = if (dark) Color(0xFF1F3A35) else Color(0xFFCFE6C4)
    val grass2 = if (dark) Color(0xFF274740) else Color(0xFFBBDDAE)
    val skyTop = if (dark) Color(0xFF1A2140) else Color(0xFFDCEAF8)
    Canvas(modifier.clipToBounds()) {
        drawRect(skyTop, size = Size(size.width, size.height * 0.3f))
        drawRect(grass, Offset(0f, size.height * 0.25f), Size(size.width, size.height * 0.75f))
        drawOval(grass2, Offset(-size.width * 0.2f, size.height * 0.18f), Size(size.width * 0.8f, size.height * 0.2f))
        drawOval(grass2, Offset(size.width * 0.45f, size.height * 0.2f), Size(size.width * 0.8f, size.height * 0.16f))
        plants.sortedBy { it.y }.forEach { p ->
            val x = 24f + p.x * (size.width - 48f)
            val y = size.height * 0.34f + p.y * (size.height * 0.6f)
            drawPlant(p.kind, Offset(x, y), (1.6f + p.y * 1.2f) * density / 2.6f)
        }
    }
}

private fun DrawScope.drawPlant(kind: String, at: Offset, s: Float) {
    val stem = Color(0xFF4F8A4A)
    when (kind) {
        "pine" -> {
            val p = Path().apply { moveTo(at.x, at.y - 34 * s); lineTo(at.x + 14 * s, at.y - 4 * s); lineTo(at.x - 14 * s, at.y - 4 * s); close() }
            drawRect(Color(0xFF8E5E47), Offset(at.x - 2.5f * s, at.y - 6 * s), Size(5 * s, 8 * s))
            drawPath(p, Color(0xFF3F7A57))
        }
        "tulip" -> {
            drawLine(stem, at, Offset(at.x, at.y - 22 * s), 3 * s)
            drawOval(Color(0xFFF4B6A6), Offset(at.x - 7 * s, at.y - 34 * s), Size(14 * s, 15 * s))
        }
        "daisy" -> {
            drawLine(stem, at, Offset(at.x, at.y - 18 * s), 3 * s)
            for (i in 0 until 6) {
                val a = i * PI / 3
                drawCircle(Color(0xFFFFF7D6), 4 * s, Offset(at.x + cos(a).toFloat() * 6 * s, at.y - 22 * s + sin(a).toFloat() * 6 * s))
            }
            drawCircle(Color(0xFFFFE08A), 3.5f * s, Offset(at.x, at.y - 22 * s))
        }
        "mushroom" -> {
            drawRect(Color(0xFFFFF7D6), Offset(at.x - 3 * s, at.y - 10 * s), Size(6 * s, 10 * s))
            drawArc(Color(0xFFE0707A), 180f, 180f, true, Offset(at.x - 10 * s, at.y - 18 * s), Size(20 * s, 16 * s))
        }
        else -> {
            drawLine(stem, at, Offset(at.x, at.y - 14 * s), 3 * s)
            drawOval(Color(0xFF7DC07A), Offset(at.x - 11 * s, at.y - 20 * s), Size(11 * s, 7 * s))
            drawOval(Color(0xFF7DC07A), Offset(at.x, at.y - 18 * s), Size(11 * s, 7 * s))
        }
    }
}
