package com.ascend.app.ui.companion

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstanceResult
import com.ascend.app.AscendApp
import com.ascend.app.R
import com.ascend.app.domain.Fur
import com.ascend.app.domain.Rewards
import com.ascend.app.ui.theme.LocalReduceMotion

private const val TAG = "KumoRive"

/** Fur palette as ARGB ints for the Rive view model (base, light, inner, line). */
private fun furArgb(f: Fur): IntArray = when (f) {
    Fur.HONEY -> intArrayOf(0xFFE2A86B.toInt(), 0xFFF7DDB2.toInt(), 0xFFF2B8A0.toInt(), 0xFFB57A44.toInt())
    Fur.COCOA -> intArrayOf(0xFF8E5E47.toInt(), 0xFFDDBB9E.toInt(), 0xFFE8A896.toInt(), 0xFF5E3A2A.toInt())
    Fur.SNOW -> intArrayOf(0xFFF3EFE6.toInt(), 0xFFFFFFFF.toInt(), 0xFFF7C4C0.toInt(), 0xFFBDB5A6.toInt())
    Fur.DUSK -> intArrayOf(0xFF7D88B8.toInt(), 0xFFD0D7F0.toInt(), 0xFFF0B7C8.toInt(), 0xFF545E8C.toInt())
}

/** Gear item id -> boolean property on the "Kumo" view model in res/raw/kumo.riv. */
private val gearProperty = mapOf(
    "beanie_ink" to "beanie", "cap_sky" to "cap", "leaf" to "leaf", "glasses" to "glasses",
    "backpack" to "backpack", "cape" to "cape", "headphones" to "headphones",
)

/**
 * Kumo rendered by the Rive runtime (state machine "Kumo"). Falls back to the Canvas [BearCub]
 * while loading, if the file fails, and whenever the system asks for reduced motion.
 */
@Composable
fun Kumo(
    modifier: Modifier = Modifier,
    fur: Fur = Fur.HONEY,
    equipped: Set<String> = emptySet(),
    pose: BearPose = BearPose.IDLE,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    // The Rive view consumes touches, so taps go to a transparent layer drawn on top of it.
    val tapLayer = if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier
    val fallback = @Composable { BearCub(modifier.then(tapLayer), fur, equipped, pose) }
    if (LocalReduceMotion.current || !AscendApp.riveReady || !AscendApp.riveEnabled) return fallback()
    val worker = rememberRiveWorker()
    when (val file = rememberRiveFile(RiveFileSource.RawRes.from(R.raw.kumo), worker)) {
        is Result.Success -> {
            val vmResult = rememberViewModelInstanceResult(file.value)
            if (vmResult is Result.Error) Log.w(TAG, "Kumo view model unavailable", vmResult.throwable)
            val vmi = (vmResult as? Result.Success)?.value ?: return fallback()
            LaunchedEffect(vmi, fur, equipped, pose) {
                runCatching {
                    Log.d(TAG, "apply pose=$pose fur=$fur gear=$equipped")
                    vmi.setNumber("pose", pose.ordinal.toFloat())
                    val c = furArgb(fur)
                    vmi.setColor("furBase", c[0]); vmi.setColor("furLight", c[1])
                    vmi.setColor("furInner", c[2]); vmi.setColor("furLine", c[3])
                    val scarf = equipped.mapNotNull { Rewards.item(it) }.firstOrNull { it.id.startsWith("scarf") }
                    vmi.setBoolean("scarf", scarf != null)
                    scarf?.let { vmi.setColor("scarfColor", it.color.toInt()) }
                    gearProperty.forEach { (id, prop) -> vmi.setBoolean(prop, id in equipped) }
                }.onFailure { Log.w(TAG, "Kumo property update failed", it) }
            }
            Box(modifier.semantics { contentDescription = "Kumo the bear cub" }) {
                Rive(file = file.value, viewModelInstance = vmi, fit = Fit.Contain())
                if (onClick != null) Box(Modifier.matchParentSize().then(tapLayer))
            }
        }
        is Result.Error -> {
            Log.w(TAG, "kumo.riv failed to load", file.throwable)
            fallback()
        }
        else -> fallback()
    }
}
