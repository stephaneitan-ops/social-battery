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

    // --- Sommeil et sport (etat d'activite detecte par la montre) ---
    fun onActivityState(state: String, at: Long) {
        val prev = p.getString("actState", "") ?: ""
        val prevAt = p.getLong("actAt", at)
        val e = p.edit()
        if (prev == "ASLEEP" && state != "ASLEEP") {
            val segment = at - prevAt
            val lastWake = p.getLong("segWakeAt", 0)
            // Meme nuit si on se rendort moins de 3 h apres le dernier reveil
            val sameNight = lastWake > 0 && prevAt - lastWake < 3 * 3_600_000L
            val total = (if (sameNight) p.getLong("nightMs", 0) else 0L) + segment
            val wakeups = if (sameNight) p.getInt("nightWakeups", 0) + 1 else 0
            e.putLong("segWakeAt", at).putLong("nightMs", total).putInt("nightWakeups", wakeups)
            if (total > 2 * 3_600_000L) {
                // Nuit consideree : reveil = dernier reveil, sommeil = total cumule
                e.putLong("wakeAt", at).putLong("sleepMs", total).putInt("wakeups", wakeups)
            }
        }
        if (prev == "EXERCISE" && state != "EXERCISE") {
            e.putInt("sportMin", sportMinutesDone() + ((at - prevAt) / 60_000L).toInt())
                .putString("sportDay", LocalDate.now().toString())
        }
        e.putString("actState", state).putLong("actAt", at).apply()
    }

    private fun sportMinutesDone() =
        if (p.getString("sportDay", "") == LocalDate.now().toString()) p.getInt("sportMin", 0) else 0

    private fun sportMinutesToday(): Int {
        val live = if (p.getString("actState", "") == "EXERCISE")
            ((System.currentTimeMillis() - p.getLong("actAt", 0)) / 60_000L).toInt().coerceIn(0, 300) else 0
        return sportMinutesDone() + live
    }

    // --- Agenda : minutes de rendez-vous ecoulees aujourd'hui ---
    private fun meetingMinutesToday(): Int {
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return 0
        val start = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, start)
            android.content.ContentUris.appendId(it, now)
        }.build()
        return try {
            ctx.contentResolver.query(
                uri,
                arrayOf(
                    android.provider.CalendarContract.Instances.BEGIN,
                    android.provider.CalendarContract.Instances.END,
                    android.provider.CalendarContract.Instances.ALL_DAY,
                    android.provider.CalendarContract.Instances.AVAILABILITY
                ), null, null, null
            )?.use { c ->
                var ms = 0L
                while (c.moveToNext()) {
                    if (c.getInt(2) == 1) continue                                     // journee entiere
                    if (c.getInt(3) == android.provider.CalendarContract.Instances.AVAILABILITY_FREE) continue
                    val b = maxOf(c.getLong(0), start); val e = minOf(c.getLong(1), now)
                    if (e > b) ms += e - b
                }
                (ms / 60_000L).toInt()
            } ?: 0
        } catch (e: Exception) { 0 }
    }

    /**
     * Calcul de la batterie sociale (0-100 %) :
     *  - Sommeil  : -5 % par heure en dessous de 7 h (max -25)
 *               -3 % par reveil nocturne au-dela du premier (max -10), total max -30
     *  - Eveil    : -3 % par heure depuis ton reveil reel (7 h si inconnu) (max -45)
     *  - Pas      : -2 % par tranche de 1000 pas (max -25)
     *  - Stress   : -1,2 % par battement au-dessus du repos, hors sport (max -30)
     *  - Agenda   : -6 % par heure de rendez-vous ecoulee (max -30)
     *  - Sport    : +1 % par 5 minutes de sport (max +15), ca defoule
     */
    fun autoPercent(): Int = breakdown().second

    fun breakdown(): Pair<String, Int> {
        val now = System.currentTimeMillis()
        val dayStart = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val wakeAt = p.getLong("wakeAt", 0).takeIf { it >= dayStart - 3 * 3_600_000L }
        val wake = wakeAt ?: (dayStart + 7 * 3_600_000L)

        // Sommeil : duree totale de la nuit + reveils nocturnes (au-dela du premier)
        val sleepDrain = if (wakeAt != null) {
            val shortNight = ((7f - p.getLong("sleepMs", 0) / 3_600_000f) * 5f).coerceIn(0f, 25f)
            val restless = ((p.getInt("wakeups", 0) - 1).coerceAtLeast(0) * 3f).coerceAtMost(10f)
            (shortNight + restless).coerceAtMost(30f)
        } else 0f
        val timeDrain = ((now - wake).coerceAtLeast(0) / 3_600_000f * 3f).coerceAtMost(45f)

        val steps = if (p.getString("stepsDay", "") == LocalDate.now().toString()) p.getLong("steps", 0) else 0
        val stepDrain = (steps / 1000f * 2f).coerceAtMost(25f)

        val exercising = p.getString("actState", "") == "EXERCISE"
        val hrFresh = now - p.getLong("hrAt", 0) < 30 * 60_000
        val hrDrain = if (hrFresh && !exercising) {
            ((p.getFloat("hr", 0f) - p.getFloat("baseline", 65f)) * 1.2f).coerceIn(0f, 30f)
        } else 0f

        val meetMin = meetingMinutesToday()
        val meetDrain = (meetMin / 60f * 6f).coerceAtMost(30f)
        val sportMin = sportMinutesToday()
        val sportBonus = (sportMin / 5f).coerceAtMost(15f)

        val pct = (100f - sleepDrain - timeDrain - stepDrain - hrDrain - meetDrain + sportBonus)
            .roundToInt().coerceIn(0, 100)
        val detail = "sommeil=-${sleepDrain.roundToInt()}(${p.getLong("sleepMs", 0) / 60_000}min,${p.getInt("wakeups", 0)}rev) eveil=-${timeDrain.roundToInt()} " +
            "pas=-${stepDrain.roundToInt()} stress=-${hrDrain.roundToInt()} " +
            "agenda=-${meetDrain.roundToInt()}(${meetMin}min) sport=+${sportBonus.roundToInt()}(${sportMin}min) => $pct%"
        return detail to pct
    }

    /** Demande au cadran de se redessiner si le niveau a changé (ou toujours si force). */
    fun refreshWatchFace(force: Boolean = false) {
        val level = currentLevel().ordinal
        if (!force && level == p.getInt("lastLevel", -1)) return
        p.edit().putInt("lastLevel", level).apply()
        ComplicationDataSourceUpdateRequester
            .create(ctx, ComponentName(ctx, BatteryComplicationService::class.java))
            .requestUpdateAll()
        // Met aussi a jour la carte (tuile)
        androidx.wear.tiles.TileService.getUpdater(ctx).requestUpdate(BatteryTileService::class.java)
    }
}
