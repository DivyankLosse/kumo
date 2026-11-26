package com.ascend.app.domain

enum class GearSlot { HEAD, NECK, FACE, BACK, EARS }

enum class Fur(val label: String) { HONEY("Honey"), COCOA("Cocoa"), SNOW("Snow"), DUSK("Dusk") }

data class GearItem(val id: String, val name: String, val slot: GearSlot, val cost: Int, val color: Long)

object Rewards {
    const val COMPLETION_BONUS = 10
    const val MIN_MINUTES_FOR_BONUS = 10

    /** One Ascend Point per focused minute, plus a bonus for finishing a session of 10+ minutes. */
    fun pointsFor(actualSeconds: Int, plannedMinutes: Int, completed: Boolean): Int {
        val minutes = actualSeconds.coerceAtLeast(0) / 60
        val bonus = if (completed && plannedMinutes >= MIN_MINUTES_FOR_BONUS) COMPLETION_BONUS else 0
        return minutes + bonus
    }

    val catalog = listOf(
        GearItem("scarf_sky", "Sky scarf", GearSlot.NECK, 0, 0xFFA8C6E7),
        GearItem("scarf_butter", "Butter scarf", GearSlot.NECK, 60, 0xFFFFE08A),
        GearItem("scarf_blush", "Blush scarf", GearSlot.NECK, 60, 0xFFF4B6A6),
        GearItem("beanie_ink", "Night beanie", GearSlot.HEAD, 120, 0xFF2A3150),
        GearItem("cap_sky", "Summit cap", GearSlot.HEAD, 150, 0xFF7FA8D6),
        GearItem("leaf", "Lucky leaf", GearSlot.HEAD, 90, 0xFFB5D8B0),
        GearItem("glasses", "Study glasses", GearSlot.FACE, 200, 0xFF3A3F55),
        GearItem("backpack", "Trail backpack", GearSlot.BACK, 250, 0xFFF4B6A6),
        GearItem("cape", "Dawn cape", GearSlot.BACK, 400, 0xFFC9B8F0),
        GearItem("headphones", "Focus headphones", GearSlot.EARS, 300, 0xFFFFF7D6),
    )

    fun item(id: String): GearItem? = catalog.firstOrNull { it.id == id }

    val plantKinds = listOf("sprout", "tulip", "pine", "daisy", "mushroom")
}
