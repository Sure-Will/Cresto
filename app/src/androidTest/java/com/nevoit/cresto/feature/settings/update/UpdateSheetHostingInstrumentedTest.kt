package com.nevoit.cresto.feature.settings.update

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nevoit.cresto.R
import com.nevoit.cresto.feature.settings.SettingsScreen
import com.nevoit.cresto.theme.GlasenseTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateSheetHostingInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun availableUpdate_opensSheetAndKeepsStatusAfterDismissal() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeInfo = UpdateInfo(
            manifest = UpdateManifest(
                versionCode = 1093,
                versionName = "1.0",
                downloadUrl = "https://example.invalid/Cresto-1.0-1093.apk",
                releaseNotes = mapOf(
                    "en" to listOf(
                        ReleaseNoteSection(
                            type = "improvement",
                            title = null,
                            items = listOf("Regression test note")
                        )
                    )
                )
            ),
            isRequired = false
        )

        compose.setContent {
            GlasenseTheme {
                SettingsScreen(
                    updateEntryState = rememberUpdateEntryState(
                        check = { UpdateCheckResult.HasUpdate(fakeInfo) }
                    )
                )
            }
        }

        compose.onNodeWithText(targetContext.getString(R.string.check_for_updates)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(targetContext.getString(R.string.update_available)).assertIsDisplayed()
        compose.onNodeWithText("Regression test note").assertIsDisplayed()

        // Both the page and the overlaid sheet use "Back"; the sheet is hosted last.
        compose.onAllNodesWithContentDescription(targetContext.getString(R.string.back))
            .onLast()
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText(
            targetContext.getString(R.string.update_status_available_format, "1.0 (1093)")
        ).assertIsDisplayed()

        val screenshot = File(targetContext.getExternalFilesDir(null), "update-status-available.png")
        screenshot.outputStream().use { output ->
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(
                Bitmap.CompressFormat.PNG, 100, output
            ))
        }
    }
}
