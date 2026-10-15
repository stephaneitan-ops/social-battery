package fr.stephane.socialbattery

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { HealthListenerService.register(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Garde l'écran allumé tant que le badge est affiché
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val heartPerm = if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE"
        else Manifest.permission.BODY_SENSORS
        permissions.launch(arrayOf(heartPerm, Manifest.permission.ACTIVITY_RECOGNITION))

        val store = Store(this)
        setContent { BadgeScreen(store) }
    }
}

@Composable
fun BadgeScreen(store: Store) {
    var manual by remember { mutableIntStateOf(store.manualIndex) }
    var percent by remember { mutableIntStateOf(store.autoPercent()) }
    var rotaryAcc by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }

    // Recalcule le niveau auto toutes les 30 s
    LaunchedEffect(Unit) {
        while (true) {
            percent = store.autoPercent()
            delay(30_000)
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val level = if (manual >= 0) Level.entries[manual] else Level.fromPercent(percent)
    val bg by animateColorAsState(level.color, label = "bg")
    val max = Level.entries.size - 1

    fun setManual(i: Int) { manual = i; store.manualIndex = i }

    val bigStyle = CurvedTextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
    val smallStyle = CurvedTextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            // Lunette / couronne : change de niveau à la main
            .onRotaryScrollEvent {
                rotaryAcc += it.verticalScrollPixels
                if (abs(rotaryAcc) > 60f) {
                    val step = if (rotaryAcc > 0) 1 else -1
                    setManual((level.ordinal + step).coerceIn(0, max))
                    rotaryAcc = 0f
                }
                true
            }
            .focusRequester(focus)
            .focusable()
            .pointerInput(level, manual) {
                detectTapGestures(
                    // Tap : niveau suivant (mode manuel)
                    onTap = { setManual((level.ordinal + 1) % Level.entries.size) },
                    // Appui long : retour au mode auto
                    onLongPress = { setManual(-1); percent = store.autoPercent() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Mot en arc, en haut
        CurvedLayout(modifier = Modifier.fillMaxSize(), anchor = 270f) {
            curvedText(level.title, style = bigStyle, modifier = CurvedModifier.padding(14.dp))
        }
        // Phrase en arc, en bas (lisible de gauche à droite)
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
                text = if (manual >= 0) "MANUEL" else "AUTO · $percent %",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}
