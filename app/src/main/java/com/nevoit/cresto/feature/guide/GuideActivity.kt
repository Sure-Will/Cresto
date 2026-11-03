package com.nevoit.cresto.feature.guide

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.nevoit.cresto.MainActivity
import com.nevoit.cresto.feature.settings.util.SettingsManager
import com.nevoit.cresto.theme.GlasenseTheme
import com.nevoit.glasense.core.utility.clearBackground

class GuideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent {
            GlasenseTheme {
                GuideScreen(onFinish = {
                    SettingsManager.isFirstRun = false

                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)

                    finish()
                })
            }
        }
        clearBackground()
    }
}
