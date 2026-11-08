package com.thomas.passiveeartraining

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thomas.passiveeartraining.app.AppRoot
import com.thomas.passiveeartraining.app.PassiveEarTrainingApp

/** The single activity. All destinations live in the app package's typed navigation graph. */
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        val app = newBase.applicationContext as? PassiveEarTrainingApp
        super.attachBaseContext(app?.activityBaseContext(newBase) ?: newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot(applicationContext as PassiveEarTrainingApp)
        }
    }
}
