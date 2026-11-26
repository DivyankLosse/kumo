package com.ascend.app.ui.companion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ascend.app.domain.Fur
import com.ascend.app.domain.GearSlot
import com.ascend.app.domain.Rewards
import com.ascend.app.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class BearPose { IDLE, WALK, CHEER, SLEEP }

private data class FurColors(val base: Color, val light: Color, val inner: Color, val line: Color)

private fun furColors(f: Fur) = when (f) {
    Fur.HONEY -> FurColors(Color(0xFFE2A86B), Color(0xFFF7DDB2), Color(0xFFF2B8A0), Color(0xFFB57A44))
    Fur.COCOA -> FurColors(Color(0xFF8E5E47), Color(0xFFDDBB9E), Color(0xFFE8A896), Color(0xFF5E3A2A))
    Fur.SNOW -> FurColors(Color(0xFFF3EFE6), Color(0xFFFFFFFF), Color(0xFFF7C4C0), Color(0xFFBDB5A6))
    Fur.DUSK -> FurColors(Color(0xFF7D88B8), Color(0xFFD0D7F0), Color(0xFFF0B7C8), Color(0xFF545E8C))
}

private val EyeInk = Color(0xFF231A2C)
private val Blush = Color(0xFFF49A9A)

/**
 * Kumo, the app's original bear-cub companion. Drawn on a 200×220 grid and scaled to fit, so every
 * part can move independently (walk cycle, blink, cheer) and wear gear from the shop.
 */
@Composable
fun BearCub(
    modifier: Modifier = Modifier,
    fur: Fur = Fur.HONEY,
    equipped: Set<String> = emptySet(),
    pose: BearPose = BearPose.IDLE,
    walkSpeedMs: Int = 900,
) {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "bear")
    val cycle by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (pose == BearPose.WALK) walkSpeedMs else 3200, easing = LinearEasing)),
        label = "cycle",
    )
    val blink by t.animateFloat(
        1f, 1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                1f at 0
                1f at 3900
                0.1f at 3990
                1f at 4100
            },
            RepeatMode.Restart,
        ),
        label = "blink",
    )
    val twitch by t.animateFloat(
        0f, 0f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 6100
                0f at 0
                0f at 5200
                -9f at 5280
                3f at 5380
                0f at 5480
            },
            RepeatMode.Restart,
        ),
        label = "ear",
    )
    val phase = if (reduce) 0f else cycle
    val eyeOpen = if (reduce) 1f else blink
    val ear = if (reduce) 0f else twitch
    Canvas(modifier.semantics { contentDescription = "Kumo the bear cub" }) {
        val s = minOf(size.width / 200f, size.height / 220f)
        val dx = (size.width - 200f * s) / 2f
        val dy = size.height - 220f * s
        translate(dx, dy) {
            scale(s, s, pivot = Offset.Zero) {
                drawBear(furColors(fur), equipped, pose, phase, eyeOpen, ear)
            }
        }
    }
}

/** Soft top-left light over a fur shape: highlight → base → shaded edge. */
private fun furBrush(c: FurColors, center: Offset, radius: Float) = Brush.radialGradient(
    0f to lerp(c.base, Color.White, 0.22f),
    0.55f to c.base,
    1f to lerp(c.base, c.line, 0.35f),
    center = center, radius = radius,
)

private fun DrawScope.furOval(c: FurColors, topLeft: Offset, size: Size, outline: Boolean = true) {
    val light = Offset(topLeft.x + size.width * 0.38f, topLeft.y + size.height * 0.3f)
    drawOval(furBrush(c, light, maxOf(size.width, size.height) * 0.85f), topLeft, size)
    if (outline) drawOval(c.line.copy(alpha = 0.45f), topLeft, size, style = Stroke(2.2f))
}

private fun DrawScope.drawBear(c: FurColors, gear: Set<String>, pose: BearPose, phase: Float, eyeOpen: Float, earTwitch: Float) {
    val wave = sin(phase * 2 * PI).toFloat()
    val bob = when (pose) {
        BearPose.WALK -> -abs(wave) * 6f
        BearPose.IDLE -> wave * 1.6f
        BearPose.SLEEP -> wave * 1.2f
        BearPose.CHEER -> 0f
    }
    val tilt = if (pose == BearPose.WALK) wave * 3f else 0f
    // Head moves a little on its own: a slow idle tilt, a counter-sway while walking.
    val headTilt = when (pose) {
        BearPose.IDLE -> sin(phase * 2 * PI + 0.8).toFloat() * 2.5f
        BearPose.WALK -> -wave * 2.5f
        BearPose.SLEEP -> 6f
        BearPose.CHEER -> 0f
    }
    val item = { slot: GearSlot -> gear.mapNotNull { Rewards.item(it) }.firstOrNull { it.slot == slot } }

    // Ground shadow stays put while the body bobs.
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(58f, 202f), Size(84f, 12f))

    translate(0f, bob) {
        rotate(tilt, pivot = Offset(100f, 200f)) {
            item(GearSlot.BACK)?.let { if (it.id == "cape") drawCape(Color(it.color)) }

            // Legs: alternate forward/back while walking; light paw pads underneath.
            val step = if (pose == BearPose.WALK) wave * 7f else 0f
            furOval(c, Offset(66f - step, 176f + if (step > 0) -2f else 0f), Size(28f, 26f))
            furOval(c, Offset(106f + step, 176f + if (step < 0) -2f else 0f), Size(28f, 26f))
            drawOval(c.light, Offset(71f - step, 188f), Size(18f, 10f))
            drawOval(c.light, Offset(111f + step, 188f), Size(18f, 10f))

            // Body and belly.
            furOval(c, Offset(48f, 108f), Size(104f, 86f))
            drawOval(c.light, Offset(72f, 128f), Size(56f, 52f))
            drawOval(c.line.copy(alpha = 0.12f), Offset(72f, 128f), Size(56f, 52f), style = Stroke(1.5f))

            item(GearSlot.BACK)?.let { if (it.id == "backpack") drawBackpackStraps(Color(it.color)) }

            val armSwing = if (pose == BearPose.WALK) wave * 14f else 0f
            if (pose != BearPose.CHEER) {
                rotate(18f + armSwing, pivot = Offset(58f, 124f)) { furOval(c, Offset(46f, 122f), Size(24f, 40f)) }
                rotate(-18f - armSwing, pivot = Offset(142f, 124f)) { furOval(c, Offset(130f, 122f), Size(24f, 40f)) }
            }

            // Soft contact shadow where the head meets the body.
            drawOval(c.line.copy(alpha = 0.18f), Offset(62f, 118f), Size(76f, 18f))

            rotate(headTilt, pivot = Offset(100f, 128f)) {
                drawHead(c, pose, eyeOpen, earTwitch, item)
                // Scarf sits under the chin, so it is drawn over the head's lower edge.
                item(GearSlot.NECK)?.let { drawScarf(Color(it.color), wave) }
            }

            // Raised arms go in front of the head so the cheer reads clearly.
            if (pose == BearPose.CHEER) {
                rotate(-28f, pivot = Offset(40f, 120f)) { furOval(c, Offset(26f, 64f), Size(24f, 60f)) }
                rotate(28f, pivot = Offset(160f, 120f)) { furOval(c, Offset(150f, 64f), Size(24f, 60f)) }
                drawCircle(c.light, 9f, Offset(24f, 70f))
                drawCircle(c.light, 9f, Offset(176f, 70f))
            }
        }
    }
}

private fun DrawScope.drawHead(c: FurColors, pose: BearPose, eyeOpen: Float, earTwitch: Float, item: (GearSlot) -> com.ascend.app.domain.GearItem?) {
    // Ears; the right one flicks now and then.
    furOval(c, Offset(33f, 19f), Size(42f, 42f))
    drawCircle(c.inner, 11f, Offset(55f, 42f))
    rotate(earTwitch, pivot = Offset(140f, 52f)) {
        furOval(c, Offset(125f, 19f), Size(42f, 42f))
        drawCircle(c.inner, 11f, Offset(145f, 42f))
    }
    // Head.
    furOval(c, Offset(40f, 26f), Size(120f, 110f))
    // Tuft: three soft strokes of fur on the crown.
    val tuft = Path().apply {
        moveTo(90f, 30f); quadraticTo(92f, 18f, 99f, 16f); quadraticTo(96f, 24f, 100f, 28f)
        quadraticTo(104f, 17f, 112f, 18f); quadraticTo(105f, 23f, 108f, 31f); close()
    }
    drawPath(tuft, c.base)
    drawPath(tuft, c.line.copy(alpha = 0.45f), style = Stroke(2f))

    if (pose == BearPose.SLEEP) {
        listOf(78f, 122f).forEach { x ->
            drawArc(EyeInk, 10f, 160f, false, Offset(x - 11f, 78f), Size(22f, 16f), style = Stroke(3.5f, cap = StrokeCap.Round))
        }
    } else if (pose == BearPose.CHEER) {
        listOf(78f, 122f).forEach { x ->
            drawArc(EyeInk, 200f, 140f, false, Offset(x - 11f, 82f), Size(22f, 18f), style = Stroke(4f, cap = StrokeCap.Round))
        }
    } else {
        // Anime eyes: warm iris gradient, dark rim, two catchlights and a sparkle.
        listOf(78f, 122f).forEach { x ->
            val h = 27f * eyeOpen
            val top = 88f - h / 2f
            if (eyeOpen > 0.4f) {
                drawOval(
                    Brush.verticalGradient(listOf(EyeInk, Color(0xFF5A3A33), Color(0xFF8A5A40)), startY = top, endY = top + h),
                    Offset(x - 11f, top), Size(22f, h),
                )
                drawOval(EyeInk, Offset(x - 11f, top), Size(22f, h), style = Stroke(2f))
                drawOval(EyeInk.copy(alpha = 0.8f), Offset(x - 5f, top + h * 0.28f), Size(10f, h * 0.5f))
                drawOval(Color.White, Offset(x + 0.5f, top + 3f), Size(8f, 9f))
                drawCircle(Color.White, 2.4f, Offset(x - 5f, top + h - 6f))
                drawCircle(Color.White.copy(alpha = 0.7f), 1.2f, Offset(x - 1f, top + h - 3.5f))
            } else {
                drawLine(EyeInk, Offset(x - 9f, 88f), Offset(x + 9f, 88f), 3f, cap = StrokeCap.Round)
            }
            // Lash flick at the outer corner.
            val outer = if (x < 100f) -1f else 1f
            drawLine(EyeInk, Offset(x + outer * 10f, top + 4f), Offset(x + outer * 14f, top + 1f), 2.2f, cap = StrokeCap.Round)
        }
    }

    // Cheeks, muzzle, nose, mouth.
    listOf(62f, 138f).forEach { cx ->
        drawOval(Brush.radialGradient(listOf(Blush.copy(alpha = 0.7f), Blush.copy(alpha = 0f)), Offset(cx, 105.5f), 13f), Offset(cx - 13f, 98f), Size(26f, 15f))
    }
    drawOval(c.light, Offset(82f, 97f), Size(36f, 26f))
    val nose = Path().apply {
        moveTo(93f, 102f); quadraticTo(100f, 98f, 107f, 102f); quadraticTo(104f, 109f, 100f, 109f); quadraticTo(96f, 109f, 93f, 102f); close()
    }
    drawPath(nose, EyeInk)
    drawCircle(Color.White.copy(alpha = 0.5f), 1.6f, Offset(98f, 102.5f))
    val mouth = Path().apply {
        moveTo(93f, 112f); quadraticTo(96.5f, 117f, 100f, 112f); quadraticTo(103.5f, 117f, 107f, 112f)
    }
    drawPath(mouth, EyeInk, style = Stroke(2.2f, cap = StrokeCap.Round))
    if (pose == BearPose.CHEER) drawOval(Color(0xFFE0707A), Offset(95f, 113f), Size(10f, 8f))

    item(GearSlot.FACE)?.let { drawGlasses(Color(it.color)) }
    item(GearSlot.EARS)?.let { drawHeadphones(Color(it.color)) }
    item(GearSlot.HEAD)?.let {
        when (it.id) {
            "beanie_ink" -> drawBeanie(Color(it.color))
            "cap_sky" -> drawCap(Color(it.color))
            "leaf" -> drawLeaf(Color(it.color))
        }
    }
}

private fun DrawScope.drawScarf(color: Color, wave: Float) {
    drawRoundRect(color, Offset(62f, 126f), Size(76f, 15f), CornerRadius(7.5f))
    val tail = Path().apply {
        moveTo(114f, 134f); lineTo(129f, 134f); lineTo(132f + wave * 2f, 164f); lineTo(117f + wave * 2f, 166f); close()
    }
    drawPath(tail, color)
    drawLine(Color.White.copy(alpha = 0.35f), Offset(68f, 133f), Offset(132f, 133f), 2f)
}

private fun DrawScope.drawCape(color: Color) {
    val p = Path().apply {
        moveTo(62f, 122f); quadraticTo(30f, 170f, 40f, 196f); lineTo(160f, 196f); quadraticTo(170f, 170f, 138f, 122f); close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawBackpackStraps(color: Color) {
    drawRoundRect(color, Offset(66f, 122f), Size(9f, 44f), CornerRadius(4f))
    drawRoundRect(color, Offset(125f, 122f), Size(9f, 44f), CornerRadius(4f))
    drawRoundRect(color, Offset(150f, 128f), Size(14f, 40f), CornerRadius(6f))
}

private fun DrawScope.drawGlasses(color: Color) {
    val st = Stroke(3f)
    drawCircle(color, 15f, Offset(78f, 88f), style = st)
    drawCircle(color, 15f, Offset(122f, 88f), style = st)
    drawLine(color, Offset(93f, 86f), Offset(107f, 86f), 3f)
}

private fun DrawScope.drawHeadphones(color: Color) {
    drawArc(color, 180f, 180f, false, Offset(34f, 22f), Size(132f, 120f), style = Stroke(8f, cap = StrokeCap.Round))
    drawRoundRect(color, Offset(28f, 70f), Size(18f, 34f), CornerRadius(8f))
    drawRoundRect(color, Offset(154f, 70f), Size(18f, 34f), CornerRadius(8f))
}

private fun DrawScope.drawBeanie(color: Color) {
    drawArc(color, 180f, 180f, true, Offset(46f, 18f), Size(108f, 70f))
    drawRoundRect(color, Offset(44f, 46f), Size(112f, 14f), CornerRadius(7f))
    drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(44f, 46f), Size(112f, 14f), CornerRadius(7f))
    drawCircle(Color(0xFFFFF7D6), 9f, Offset(100f, 18f))
}

private fun DrawScope.drawCap(color: Color) {
    drawArc(color, 180f, 180f, true, Offset(50f, 22f), Size(100f, 60f))
    drawRoundRect(color, Offset(92f, 46f), Size(66f, 10f), CornerRadius(5f))
    drawCircle(Color.White.copy(alpha = 0.6f), 4f, Offset(100f, 24f))
}

private fun DrawScope.drawLeaf(color: Color) {
    val p = Path().apply { moveTo(100f, 30f); quadraticTo(84f, 6f, 104f, 0f); quadraticTo(116f, 16f, 100f, 30f); close() }
    drawPath(p, color)
    drawLine(Color(0xFF3F6B3A), Offset(100f, 30f), Offset(103f, 6f), 2f)
}
