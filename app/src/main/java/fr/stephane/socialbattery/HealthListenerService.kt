package fr.stephane.socialbattery

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.UserActivityInfo
import androidx.health.services.client.data.UserActivityState

/** Reçoit fréquence cardiaque et pas en arrière-plan, même app fermée. */
class HealthListenerService : PassiveListenerService() {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val store = Store(this)
        dataPoints.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { store.onHeartRate(it.value) }
        dataPoints.getData(DataType.STEPS_DAILY).lastOrNull()?.let { store.onSteps(it.value) }
        store.refreshWatchFace()
    }

    /** Sommeil / sport / eveil detectes par la montre. */
    override fun onUserActivityInfoReceived(info: UserActivityInfo) {
        val state = when (info.userActivityState) {
            UserActivityState.USER_ACTIVITY_ASLEEP -> "ASLEEP"
            UserActivityState.USER_ACTIVITY_EXERCISE -> "EXERCISE"
            UserActivityState.USER_ACTIVITY_PASSIVE -> "PASSIVE"
            else -> "UNKNOWN"
        }
        val store = Store(this)
        store.onActivityState(state, info.stateChangeTime.toEpochMilli())
        android.util.Log.i("SocialBattery", "etat=$state ${store.breakdown().first}")
        store.refreshWatchFace()
    }

    companion object {
        fun register(ctx: Context) {
            val config = PassiveListenerConfig.builder()
                .setDataTypes(setOf(DataType.HEART_RATE_BPM, DataType.STEPS_DAILY))
                .setShouldUserActivityInfoBeRequested(true)
                .build()
            HealthServices.getClient(ctx).passiveMonitoringClient
                .setPassiveListenerServiceAsync(HealthListenerService::class.java, config)
        }
    }
}
