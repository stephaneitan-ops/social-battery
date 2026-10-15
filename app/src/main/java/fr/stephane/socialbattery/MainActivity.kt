package fr.stephane.socialbattery

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.SideEffect
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material.curvedText
import androidx.wear.compose.foundation.padding
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private val bgPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startSensors() }

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { afterMainPermissions() }

    private fun missing(vararg perms: String) =
        perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate sdk=${Build.VERSION.SDK_INT}")
        // Garde l'écran allumé tant que le badge est affiché
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val store = Store(this)
        setContent { BadgeScreen(store) }
        Log.i(TAG, "setContent ok")

        // Ne demande les autorisations que si elles manquent
        val heartPerm = if (Build.VERSION.SDK_INT >= 36) PERM_HEART_RATE else Manifest.permission.BODY_SENSORS
        val needed = missing(heartPerm, Manifest.permission.ACTIVITY_RECOGNITION)
        Log.i(TAG, "permissions manquantes=$needed")
        if (needed.isEmpty()) afterMainPermissions() else permissions.launch(needed.toTypedArray())
    }

    /** Wear OS 6 : autorisation séparée pour lire le cœur en arrière-plan. */
    private fun afterMainPermissions() {
        if (Build.VERSION.SDK_INT >= 36 && missing(PERM_BACKGROUND).isNotEmpty()) {
            bgPermission.launch(PERM_BACKGROUND)
        } else startSensors()
    }

    private fun startSensors() {
        try {
            HealthListenerService.register(this)
            Log.i(TAG, "capteurs enregistres")
        } catch (e: Exception) {
            Log.e(TAG, "capteurs KO", e)
        }
    }

    companion object {
        const val TAG = "SocialBattery"
        const val PERM_HEART_RATE = "android.permission.health.READ_HEART_RATE"
        const val PERM_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    }
}

@Composable
fun BadgeScreen(store: Store) {
    var manual by remember { mutableIntStateOf(store.manualIndex) }   // -1 = auto
    var percent by remember { mutableIntStateOf(store.autoPercent()) }
    var editing by remember { mutableStateOf(false) }                  // mode reglage deverrouille
    var lastInput by remember { mutableIntStateOf(0) }                 // relance le minuteur de verrouillage
    var rotaryAcc by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    // Recalcule le niveau auto toutes les 30 s
    LaunchedEffect(Unit) {
        while (true) {
            percent = store.autoPercent()
            delay(30_000)
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // Verrouillage automatique apres 4 s sans toucher
    LaunchedEffect(editing, lastInput) {
        if (editing) {
            delay(4_000)
            editing = false
        }
    }

    val level = if (manual >= 0) Level.entries[manual] else Level.fromPercent(percent)
    SideEffect { Log.i(MainActivity.TAG, "dessin badge niveau=$level pct=$percent manuel=$manual reglage=$editing") }
    val bg by animateColorAsState(level.color, label = "bg")

    // Choix en mode reglage : 0 = AUTO, 1..4 = niveaux
    val choices = Level.entries.size + 1
    fun step(delta: Int) {
        val next = ((manual + 1 + delta) % choices + choices) % choices
        manual = next - 1
        store.manualIndex = manual
        if (manual < 0) percent = store.autoPercent()
        lastInput++
    }
    val onTap by rememberUpdatedState { if (editing) step(1) }
    val onUnlock by rememberUpdatedState {
        editing = true
        lastInput++
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val bigStyle = CurvedTextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
    val smallStyle = CurvedTextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            // Lunette : change le niveau, seulement en mode reglage
            .onRotaryScrollEvent {
                if (editing) {
                    rotaryAcc += it.verticalScrollPixels
                    if (abs(rotaryAcc) > 60f) {
                        step(if (rotaryAcc > 0) 1 else -1)
                        rotaryAcc = 0f
                    }
                }
                true
            }
            .focusRequester(focus)
            .focusable()
            // Appui de 1,5 s = deverrouille ; tap = choix suivant (si deverrouille)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    var finished = false
                    var tapped = false
                    withTimeoutOrNull(1_500L) {
                        tapped = waitForUpOrCancellation() != null
                        finished = true
                    }
                    if (!finished) {
                        onUnlock()
                        waitForUpOrCancellation()
                    } else if (tapped) {
                        onTap()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Mot en arc, en haut
        CurvedLayout(modifier = Modifier.fillMaxSize(), anchor = 270f) {
            curvedText(level.title, style = bigStyle, modifier = CurvedModifier.padding(14.dp))
        }
        // Phrase en arc, en bas (lisible de gauche a droite)
        CurvedLayout(
            modifier = Modifier.fillMaxSize(),
            anchor = 90f,
            angularDirection = CurvedDirection.Angular.CounterClockwise
        ) {
            curvedText(level.tagline, style = smallStyle, modifier = CurvedModifier.padding(14.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(level.emoji, fontSize = 84.sp)
            Text(
                text = when {
                    editing && manual < 0 -> "REGLAGE · AUTO"
                    editing -> "REGLAGE"
                    manual >= 0 -> "MANUEL"
                    else -> "AUTO · $percent %"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = if (editing) 1f else 0.75f)
            )
        }
        // Anneau blanc = mode reglage deverrouille
        if (editing) {
            Box(Modifier.fillMaxSize().border(5.dp, Color.White, CircleShape))
        }
    }
}
