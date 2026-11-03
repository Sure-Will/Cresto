package com.nevoit.cresto.feature.daily

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.nevoit.cresto.theme.GlasenseTheme
import com.nevoit.glasense.core.utility.clearBackground

class StudyPlanActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            GlasenseTheme {
                StudyPlanScreen()
            }
        }
        clearBackground()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, StudyPlanActivity::class.java)
        }
    }
}
