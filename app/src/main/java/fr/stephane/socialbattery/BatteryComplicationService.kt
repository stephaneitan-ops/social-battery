package fr.stephane.socialbattery

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Fournit le badge au cadran :
 *  - PHOTO_IMAGE : le badge complet en couleur (écran actif)
 *  - SHORT_TEXT  : juste le mot du niveau (écran au repos / AOD)
 */
class BatteryComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, Level.HAPPY)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val level = Store(this).currentLevel()
        android.util.Log.i("SocialBattery", "cadran demande type=${request.complicationType} niveau=$level")
        return build(request.complicationType, level)
    }

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        android.util.Log.i("SocialBattery", "complication activee id=$complicationInstanceId type=$type")
    }

    private fun build(type: ComplicationType, level: Level): ComplicationData? {
        val label = PlainComplicationText.Builder(level.title).build()
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return when (type) {
            ComplicationType.PHOTO_IMAGE ->
                PhotoImageComplicationData.Builder(badgeIcon(level), label)
                    .setTapAction(tap).build()
            // Le cadran dessine le badge lui-même à partir du niveau (0 = HAPPY ... 3 = DRAINED)
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    level.ordinal.toFloat(), 0f, (Level.entries.size - 1).toFloat(), label
                ).setText(label).setTitle(PlainComplicationText.Builder(level.tagline).build())
                    .setTapAction(tap).build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(label, label).setTapAction(tap).build()
            else -> null
        }
    }

    /** Image PNG compacte (quelques dizaines de Ko) pour passer sans souci vers le cadran. */
    private fun badgeIcon(level: Level): Icon {
        val out = java.io.ByteArrayOutputStream()
        BadgeRenderer.render(level, 320).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        val bytes = out.toByteArray()
        return Icon.createWithData(bytes, 0, bytes.size)
    }
}
