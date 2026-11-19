package com.lambda.app.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Full-screen alarm screen. There is no snooze or dismiss button: the alarm only stops after
 * all [MathChallenge.COUNT] problems are answered correctly. Back is blocked, and if the user
 * leaves (Home / recents / power button) the service brings this screen back.
 */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AlarmState.isRinging(this)) {
            finish()
            return
        }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        // Back button does nothing while the alarm is ringing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        setContent { ChallengeScreen(onDone = { finishAndRemoveTask() }) }
    }

    override fun onResume() {
        super.onResume()
        visible = true
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    companion object {
        @Volatile var visible = false

        fun intent(ctx: Context): Intent = Intent(ctx, AlarmRingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

private val Bg = Color(0xFF0B0713)
private val CardBg = Color(0xFF181226)
private val Accent = Color(0xFFF0BC68)
private val Muted = Color(0xFF9B98AB)
private val Bad = Color(0xFFF87171)

@Composable
private fun ChallengeScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val problems = remember { AlarmState.problems(ctx) }
    var solved by remember { mutableIntStateOf(AlarmState.solved(ctx).coerceIn(0, problems.size)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))) }
    val focus = remember { FocusRequester() }
    val label = remember { AlarmState.label(ctx) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))
            if (!AlarmState.isRinging(ctx)) { onDone(); break }
            delay(1000)
        }
    }
    LaunchedEffect(solved) { runCatching { focus.requestFocus() } }

    fun submit() {
        if (solved >= problems.size) return
        val answer = MathChallenge.parseAnswer(input)
        if (answer != null && answer == problems[solved].answer) {
            solved += 1
            AlarmState.setSolved(ctx, solved)
            input = ""
            wrong = false
            if (solved >= problems.size) {
                AlarmService.stop(ctx)
                onDone()
            }
        } else {
            wrong = true
            input = ""
        }
    }

    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(clock, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace)
        if (label.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(label, color = Accent, fontSize = 18.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        Text("Solve all ${problems.size} problems to stop the alarm", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            problems.forEachIndexed { i, _ ->
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (i < solved) Accent else Color(0x33FFFFFF)))
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Problem ${minOf(solved + 1, problems.size)} of ${problems.size}", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                problems.getOrNull(solved)?.text ?: "",
                color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { v -> input = v.filter { it.isDigit() || it == '-' }.take(9); wrong = false },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 28.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace),
                placeholder = { Text("Your answer", color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Color(0x44FFFFFF), cursorColor = Accent,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                )
            )
            if (wrong) {
                Spacer(Modifier.height(8.dp))
                Text("Wrong answer. Try again.", color = Bad, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { submit() },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF181308))
            ) { Text("Check answer", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
