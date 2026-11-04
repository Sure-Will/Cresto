package com.nevoit.cresto.feature.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.nevoit.cresto.R
import com.nevoit.cresto.feature.settings.util.SettingsViewModel
import com.nevoit.cresto.theme.AppButtonColors
import com.nevoit.cresto.theme.AppColors
import com.nevoit.cresto.theme.AppSpecs
import com.nevoit.cresto.theme.isAppInDarkTheme
import com.nevoit.cresto.ui.components.glasense.GlasenseButton
import com.nevoit.cresto.ui.components.glasense.GlasenseDynamicSmallTitle
import com.nevoit.cresto.ui.components.glasense.isScrolledPast
import com.nevoit.cresto.ui.components.packed.TopBarSpacer
import com.nevoit.cresto.ui.modifier.pressIndentShaderEffect
import com.nevoit.cresto.ui.modifier.shaderRipple
import com.nevoit.cresto.ui.modifier.tiltOnPress
import com.nevoit.glasense.component.ListRowAccessory
import com.nevoit.glasense.component.ListStack
import com.nevoit.glasense.core.component.Icon
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.core.component.VGap
import com.nevoit.glasense.theme.tokens.Amber400
import com.nevoit.glasense.theme.tokens.Blue400
import com.nevoit.glasense.theme.tokens.Green400
import com.nevoit.glasense.theme.tokens.Orange400
import com.nevoit.glasense.theme.tokens.Pink400
import com.nevoit.glasense.theme.tokens.Purple400
import com.nevoit.glasense.theme.tokens.Rose400
import io.github.vinceglb.confettikit.compose.ConfettiKit
import io.github.vinceglb.confettikit.core.Angle
import io.github.vinceglb.confettikit.core.Party
import io.github.vinceglb.confettikit.core.Position
import io.github.vinceglb.confettikit.core.emitter.Emitter
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * This composable function defines the About screen.
 * It displays the app artwork and developers.
 * It uses an experimental API for Haze effects.
 */

@Composable
fun AboutScreen(settingsViewModel: SettingsViewModel = viewModel()) {
    // Get the current activity instance to allow finishing the screen
    val activity = LocalActivity.current

    // Calculate the height of the status bar to adjust layout
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current

    val backgroundColor = AppColors.pageBackground

    // Remember the state for the lazy list to control scrolling
    val lazyListState = rememberLazyListState()

    val isSmallTitleVisible by lazyListState.isScrolledPast(statusBarHeight + 24.dp)

    val uriHandler = LocalUriHandler.current
    val darkMode = isAppInDarkTheme()
    var aboutCardTapCount by remember { mutableIntStateOf(0) }
    var confettiBurstKey by remember { mutableIntStateOf(0) }
    val hapticController = LocalHapticFeedback.current

    val isSuperGraphicUltraModernGirlEnabled by settingsViewModel.isSuperGraphicUltraModernGirlEnabled

    val backdrop = rememberLayerBackdrop {
        drawRect(
            color = backgroundColor,
            size = Size(this.size.width * 3, this.size.height * 3),
            topLeft = Offset(-this.size.width, -this.size.height)
        )
        drawContent()
    }

    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Root container for the screen, filling the entire available space
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        ListStack(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
            cornerRadius = AppSpecs.cardCorner,
            contentPadding = PaddingValues(bottom = navigationBarHeight)
        ) {
            TopBarSpacer()
            // An item that displays a background image for the About screen
            item {
                val shape = AppSpecs.cardShape
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .aspectRatio(3f / 4f)
                        .fillMaxWidth()
                        .tiltOnPress(maxTilt = 10f) {
                            aboutCardTapCount += 1
                            if (aboutCardTapCount >= 10) {
                                aboutCardTapCount = 0
                                confettiBurstKey += 1
                                settingsViewModel.unlockEasterEgg()
                            }
                        }
                        .then(
                            if (isSuperGraphicUltraModernGirlEnabled) {
                                Modifier
                                    .pressIndentShaderEffect()
                            } else {
                                Modifier
                            }
                        )
                        .clip(shape)
                        .shaderRipple()
                        .drawWithContent {
                            val outline = shape.createOutline(
                                size = size,
                                layoutDirection = LayoutDirection.Ltr,
                                density = density
                            )
                            drawContent()
                            // Draw a white glowing border around the image
                            drawOutline(
                                outline = outline,
                                style = Stroke(4.dp.toPx()),
                                color = if (darkMode) Color.White.copy(.2f) else Color.Black.copy(
                                    .05f
                                ),
                                blendMode = BlendMode.Luminosity
                            )
                        }
                ) {
                    AsyncImage(
                        model = if (darkMode) R.drawable.about_background else R.drawable.about_background_light,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            // Item container for displaying developer information
            Section(header = { stringResource(R.string.developer) }, topSpacing = 24.dp) {
                Row(
                    onClick = { uriHandler.openUri("https://github.com/nevodev") },
                    leading = {
                        Image(
                            painter = painterResource(R.drawable.avatar),
                            contentDescription = stringResource(R.string.developer_avatar),
                            modifier = Modifier
                                .clip(CircleShape)
                                .size(48.dp)
                        )
                    },
                    accessory = ListRowAccessory.Chevron
                ) {
                    DeveloperContent(
                        name = "Nevoit",
                        tagline = "Create awesome."
                    ) {
                        DeveloperBadge(stringResource(R.string.main_developer))
                        DeveloperBadge(stringResource(R.string.designer))
                    }
                }
                Row(
                    onClick = { uriHandler.openUri("https://github.com/kyant0") },
                    leading = {
                        Image(
                            painter = painterResource(R.drawable.kyant_avatar),
                            contentDescription = stringResource(R.string.developer_avatar),
                            modifier = Modifier
                                .clip(CircleShape)
                                .size(48.dp)
                        )
                    },
                    accessory = ListRowAccessory.Chevron
                ) {
                    DeveloperContent(
                        name = "Kyant0",
                        tagline = "Create rubbish."
                    ) {
                        DeveloperBadge(stringResource(R.string.overscroll_animation_developer))
                    }
                }
                Row(
                    onClick = { uriHandler.openUri("https://github.com/Sure-Will") },
                    // Sure's public GitHub avatar.
                    leading = {
                        Image(
                            painter = painterResource(R.drawable.sure_avatar),
                            contentDescription = stringResource(R.string.developer_avatar),
                            modifier = Modifier
                                .clip(CircleShape)
                                .size(48.dp)
                        )
                    },
                    accessory = ListRowAccessory.Chevron
                ) {
                    DeveloperContent(
                        name = "Sure",
                        tagline = "Make it mine."
                    ) {
                        DeveloperBadge(stringResource(R.string.fork_maintainer))
                    }
                }
            }
            item { VGap() }
        }
        // A small title that dynamically appears at the top when the user scrolls down
        GlasenseDynamicSmallTitle(
            modifier = Modifier.align(Alignment.TopCenter),
            title = stringResource(R.string.about),
            statusBarHeight = statusBarHeight,
            isVisible = isSmallTitleVisible,
            backdrop = backdrop,
            surfaceColor = backgroundColor
        ) {
        }
        // Back button positioned at the top-start of the screen
        GlasenseButton(
            enabled = true,
            shape = CircleShape,
            onClick = { activity?.finish() }, // Closes the current activity, navigating back
            modifier = Modifier
                .padding(top = statusBarHeight, start = 12.dp)
                .size(48.dp)
                .align(Alignment.TopStart),
            colors = AppButtonColors.action()
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_forward_nav),
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.width(32.dp)
            )
        }
        if (confettiBurstKey > 0) {
            // Force a fresh composition so each 10-tap milestone retriggers the burst.
            key(confettiBurstKey) {
                LaunchedEffect(Unit) {
                    repeat(10) {
                        hapticController.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        delay(Random.nextLong(50, 100).milliseconds)
                    }
                }
                ConfettiKit(
                    modifier = Modifier.fillMaxSize(),
                    parties = parade()
                )
            }
        }
    }
}

@Composable
private fun DeveloperContent(
    name: String,
    tagline: String,
    badges: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = name,
            fontSize = 20.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.W500,
            color = AppColors.content,
        )
        Text(
            text = tagline,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.W400,
            color = AppColors.contentVariant,
        )
        Row(
            modifier = Modifier
                .offset((-2).dp)
                .padding(top = 4.dp)
        ) {
            badges()
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun DeveloperBadge(text: String) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .background(
                AppColors.scrimMedium,
                Capsule()
            )
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.W400,
            color = AppColors.contentVariant,
            modifier = Modifier.padding(vertical = 2.dp, horizontal = 8.dp)
        )
    }
}

fun parade(): List<Party> {
    val party = Party(
        speed = 10f,
        maxSpeed = 30f,
        damping = 0.91f,
        angle = Angle.RIGHT - 45,
        spread = 270,
        colors = listOf(
            Amber400.toArgb(),
            Purple400.toArgb(),
            Rose400.toArgb(),
            Pink400.toArgb(),
            Blue400.toArgb(),
            Orange400.toArgb(),
            Green400.toArgb()
        ),
        timeToLive = 4000L,
        emitter = Emitter(duration = 0.8.seconds).perSecond(150),
        position = Position.Relative(-0.1, 0.1)
    )

    return listOf(
        party,
        party.copy(
            angle = party.angle - 90, // flip angle from right to left
            position = Position.Relative(1.1, 0.1)
        ),
    )
}
