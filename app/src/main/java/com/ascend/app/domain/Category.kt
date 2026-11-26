package com.ascend.app.domain

enum class Category(val label: String, val focusable: Boolean) {
    CAREER("Career", true),
    ACADEMICS("Academics", true),
    FITNESS("Fitness", false),
    HEALTH("Health", false),
    POSTURE("Posture", true),
    GROOMING("Grooming", false),
    COMMUNICATION("Communication", true),
    SOCIAL("Social", false),
    COLLEGE("College", false),
    COMMUTE("Commute", false),
    PERSONAL("Personal", true);

    companion object {
        fun of(name: String): Category = entries.firstOrNull { it.name == name } ?: PERSONAL
    }
}
