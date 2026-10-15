package fr.stephane.socialbattery

import android.graphics.Bitmap
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream

/** Carte (tuile) : le badge en plein écran, un toucher ouvre l'appli. */
class BatteryTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val level = Store(this).currentLevel()

        val open = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            ).build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(ModifiersBuilders.Clickable.Builder().setId("open").setOnClick(open).build())
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(IMAGE_ID)
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS)
                    .build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("level-${level.ordinal}")   // change de version = nouvelle image
            .setFreshnessIntervalMillis(5 * 60_000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val size = 320
        val bmp = BadgeRenderer.render(Store(this).currentLevel(), size)
        val png = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        val image = ResourceBuilders.ImageResource.Builder()
            .setInlineResource(
                ResourceBuilders.InlineImageResource.Builder()
                    .setData(png)
                    .setWidthPx(size)
                    .setHeightPx(size)
                    .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                    .build()
            ).build()

        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(requestParams.version)
                .addIdToImageMapping(IMAGE_ID, image)
                .build()
        )
    }

    companion object {
        private const val IMAGE_ID = "badge"
    }
}
