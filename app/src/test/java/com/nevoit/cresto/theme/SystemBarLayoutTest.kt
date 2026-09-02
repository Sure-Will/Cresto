package com.nevoit.cresto.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemBarLayoutTest {
    @Test
    fun `hidden status bar still reserves its physical top inset`() {
        assertEquals(
            108,
            resolveHiddenStatusBarTopInsetPx(
                isStatusBarHidden = true,
                visibleStatusBarInsetPx = 0,
                statusBarInsetIgnoringVisibilityPx = 108,
                displayCutoutInsetPx = 92
            )
        )
    }

    @Test
    fun `display cutout wins when it is taller than the status bar`() {
        assertEquals(
            132,
            resolveHiddenStatusBarTopInsetPx(
                isStatusBarHidden = true,
                visibleStatusBarInsetPx = 0,
                statusBarInsetIgnoringVisibilityPx = 108,
                displayCutoutInsetPx = 132
            )
        )
    }

    @Test
    fun `visible portion is not reserved twice during inset transitions`() {
        assertEquals(
            24,
            resolveHiddenStatusBarTopInsetPx(
                isStatusBarHidden = true,
                visibleStatusBarInsetPx = 108,
                statusBarInsetIgnoringVisibilityPx = 108,
                displayCutoutInsetPx = 132
            )
        )
    }

    @Test
    fun `visible status bar keeps the existing per-screen inset behavior`() {
        assertEquals(
            0,
            resolveHiddenStatusBarTopInsetPx(
                isStatusBarHidden = false,
                visibleStatusBarInsetPx = 96,
                statusBarInsetIgnoringVisibilityPx = 96,
                displayCutoutInsetPx = 80
            )
        )
    }
}
