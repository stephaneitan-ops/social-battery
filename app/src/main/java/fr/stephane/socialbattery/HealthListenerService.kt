package fr.stephane.socialbattery

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig

/** Reçoit fréquence cardiaque et pas en arrière-plan, même app fermée. */
class HealthListenerService : PassiveListenerService() {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val store = Store(this)
        dataPoints.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { store.onHeartRate(it.value) }
        dataPoints.getData(DataType.STEPS_DAILY).lastOrNull()?.let { store.onSteps(it.value) }
        store.refreshWatchFace()
    }

    companion object {
        fun register(ctx: Context) {
            val config = PassiveListenerConfig.builder()
                .setDataTypes(setOf(DataType.HEART_RATE_BPM, DataType.STEPS_DAILY))
                .build()
            HealthServices.getClient(ctx).passiveMonitoringClient
                .setPassiveListenerServiceAsync(HealthListenerService::class.java, config)
        }
    }
}
