package com.nevoit.cresto.ui.components.glasense

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nevoit.glasense.core.component.Icon
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.theme.GlasenseTheme
import com.nevoit.glasense.theme.LocalGlasenseContentColor

private val MainPageTopSpacing = 12.dp
private val MainPageTopBarHeight = 48.dp
private val MainPageTopBarBottomSpacing = 20.dp
internal val MainPageActionButtonSize = 44.dp
internal val MainPageActionTouchTargetSize = 48.dp
internal val MainPageActionVisualInset = 2.dp
private val MainPageActionIconFrameSize = 24.dp

enum class MainPageActionIconOptics(internal val scale: Float) {
    Search(1f),
    Sort(0.86f),
    Add(0.96f),
    Settings(1f)
}

fun Modifier.mainPageTopBarPlacement(): Modifier =
    padding(top = MainPageTopSpacing, start = 12.dp, end = 12.dp)

fun Modifier.mainPageTopBarFrame(): Modifier =
    statusBarsPadding()
        .mainPageTopBarPlacement()
        .height(MainPageTopBarHeight)

fun Modifier.mainPageTopContentSpacer(): Modifier =
    statusBarsPadding()
        .height(MainPageTopSpacing + MainPageTopBarHeight + MainPageTopBarBottomSpacing)

@Composable
fun MainPageTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = GlasenseTheme.type.largeTitleEmphasized,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun MainPageActionIcon(
    painter: Painter,
    contentDescription: String?,
    optics: MainPageActionIconOptics,
    modifier: Modifier = Modifier,
    tint: Color = LocalGlasenseContentColor.current
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = Modifier
            .size(MainPageActionIconFrameSize)
            .then(modifier)
            .graphicsLayer {
                scaleX = optics.scale
                scaleY = optics.scale
            },
        tint = tint
    )
}
