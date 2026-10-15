package fr.stephane.socialbattery

import androidx.compose.ui.graphics.Color

/** Les 4 niveaux de batterie sociale. Modifie textes / couleurs / seuils ici. */
enum class Level(val title: String, val tagline: String, val emoji: String, val color: Color) {
    HAPPY("HAPPY", "TALK TO ME!", "😊", Color(0xFF2ECC9A)),
    OK("OK", "SMALL TALK ONLY", "🙂", Color(0xFF3B8EEA)),
    BORED("BORED", "I'M SO DONE", "😒", Color(0xFFF2A21B)),
    DRAINED("DRAINED", "LEAVE ME ALONE", "😩", Color(0xFFE5484D));

    companion object {
        fun fromPercent(p: Int): Level = when {
            p >= 75 -> HAPPY
            p >= 50 -> OK
            p >= 25 -> BORED
            else -> DRAINED
        }
    }
}
