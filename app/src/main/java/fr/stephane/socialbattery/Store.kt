package fr.stephane.socialbattery

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Stocke les dernières mesures et calcule la "batterie sociale" (0-100 %).
 *
 * Estimation simple, PAS une mesure médicale :
 *  - Temps éveillé : -3 % par heure après 7h (max -45)
 *  - Activité      : -2 % par tranche de 1000 pas (max -25)
 *  - Stress        : fréquence cardiaque au-dessus de ta base de repos
 *                    -1,2 % par battement en trop (max -30)
 */
class Store(context: Context) {
    private val ctx = context.applicationContext
    private val p = ctx.getSharedPreferences("social_battery", Context.MODE_PRIVATE)

    /** -1 = mode auto, sinon index du niveau forcé à la main. */
    var manualIndex: Int
        get() = p.getInt("manual", -1)
        set(v) {
            p.edit().putInt("manual", v).apply()
            refreshWatchFace(force = true)
        }

    fun currentLevel(): Level {
        val m = manualIndex
        return if (m >= 0) Level.entries[m] else Level.fromPercent(autoPercent())
    }

    fun onHeartRate(bpm: Double) {
        if (bpm < 30 || bpm > 220) return
        val hr = bpm.toFloat()
        val base = p.getFloat("baseline", 65f)
        // La base descend vite vers les valeurs basses (repos), remonte très lentement.
        val newBase = if (hr < base) base * 0.7f + hr * 0.3f else base * 0.995f + hr * 0.005f
        p.edit()
            .putFloat("hr", hr)
            .putFloat("baseline", newBase)
            .putLong("hrAt", System.currentTimeMillis())
            .apply()
    }

    fun onSteps(steps: Long) {
        p.edit().putLong("steps", steps).putString("stepsDay", LocalDate.now().toString()).apply()
    }

    fun autoPercent(): Int {
        val now = LocalTime.now()
        val minutesAwake = (now.hour * 60 + now.minute - 7 * 60).coerceAtLeast(0)
        val timeDrain = (minutesAwake / 60f * 3f).coerceAtMost(45f)

        val steps = if (p.getString("stepsDay", "") == LocalDate.now().toString()) p.getLong("steps", 0) else 0
        val stepDrain = (steps / 1000f * 2f).coerceAtMost(25f)

        val hrFresh = System.currentTimeMillis() - p.getLong("hrAt", 0) < 30 * 60_000
        val hrDrain = if (hrFresh) {
            ((p.getFloat("hr", 0f) - p.getFloat("baseline", 65f)) * 1.2f).coerceIn(0f, 30f)
        } else 0f

        return (100f - timeDrain - stepDrain - hrDrain).roundToInt().coerceIn(0, 100)
    }

    /** Demande au cadran de se redessiner si le niveau a changé (ou toujours si force). */
    fun refreshWatchFace(force: Boolean = false) {
        val level = currentLevel().ordinal
        if (!force && level == p.getInt("lastLevel", -1)) return
        p.edit().putInt("lastLevel", level).apply()
        ComplicationDataSourceUpdateRequester
            .create(ctx, ComponentName(ctx, BatteryComplicationService::class.java))
            .requestUpdateAll()
    }
}
