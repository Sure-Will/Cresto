package com.nevoit.cresto.theme

internal fun resolveHiddenStatusBarTopInsetPx(
    isStatusBarHidden: Boolean,
    visibleStatusBarInsetPx: Int,
    statusBarInsetIgnoringVisibilityPx: Int,
    displayCutoutInsetPx: Int
): Int {
    if (!isStatusBarHidden) return 0

    val physicalSafeTopInsetPx = maxOf(
        statusBarInsetIgnoringVisibilityPx,
        displayCutoutInsetPx
    )

    return (physicalSafeTopInsetPx - visibleStatusBarInsetPx).coerceAtLeast(0)
}
