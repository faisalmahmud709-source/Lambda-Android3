package com.lambda.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import com.lambda.app.alarm.AlarmScheduler
import com.lambda.app.ui.LambdaApp
import com.lambda.app.ui.LambdaTheme

class MainActivity : ComponentActivity() {
    private val vm: LambdaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { AlarmScheduler.rescheduleAll(this) } // keep alarms armed
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val light = com.lambda.app.ui.LambdaPalettes.get(data.settings.themeId).isLight
            DisposableEffect(light) {
                enableEdgeToEdge(
                    statusBarStyle = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
                )
                onDispose { }
            }
            LambdaTheme(data.settings.themeId) { LambdaApp(vm) }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.flush() // make sure everything is written to the phone's storage
    }
}
