package fr.stephane.socialbattery

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
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

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, Store(this).currentLevel())

    private fun build(type: ComplicationType, level: Level): ComplicationData? {
        val label = PlainComplicationText.Builder(level.title).build()
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return when (type) {
            ComplicationType.PHOTO_IMAGE ->
                PhotoImageComplicationData.Builder(Icon.createWithBitmap(BadgeRenderer.render(level)), label)
                    .setTapAction(tap).build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(label, label).setTapAction(tap).build()
            else -> null
        }
    }
}
