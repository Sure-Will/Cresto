package com.nevoit.cresto.feature.home

import android.graphics.BlurMaskFilter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.shapes.Capsule
import com.nevoit.cresto.R
import com.nevoit.cresto.data.todo.TodoViewModel
import com.nevoit.cresto.theme.AppButtonColors
import com.nevoit.cresto.theme.AppColors
import com.nevoit.cresto.theme.LocalGlasenseSettings
import com.nevoit.cresto.theme.isAppInDarkTheme
import com.nevoit.cresto.ui.components.glasense.GlasenseButtonAdaptable
import com.nevoit.cresto.ui.components.glasense.GlasenseButtonToolBar
import com.nevoit.cresto.ui.components.glasense.GlasenseDynamicSmallTitle
import com.nevoit.cresto.ui.components.glasense.GlasenseMenuItem
import com.nevoit.cresto.ui.components.glasense.MainPageActionButtonSize
import com.nevoit.cresto.ui.components.glasense.MainPageActionIcon
import com.nevoit.cresto.ui.components.glasense.MainPageActionIconOptics
import com.nevoit.cresto.ui.components.glasense.MainPageActionTouchTargetSize
import com.nevoit.cresto.ui.components.glasense.MainPageActionVisualInset
import com.nevoit.cresto.ui.components.glasense.MainPageTitle
import com.nevoit.cresto.ui.components.glasense.glasenseHighlight
import com.nevoit.cresto.ui.components.glasense.mainPageTopBarFrame
import com.nevoit.cresto.ui.components.glasense.mainPageTopBarPlacement
import com.nevoit.cresto.ui.components.glasense.material.MaterialRecipes
import com.nevoit.cresto.ui.components.glasense.material.rememberMaterialRenderEffectOrNull
import com.nevoit.cresto.util.supportsRuntimeShaderEffect
import com.nevoit.glasense.core.component.Icon
import com.nevoit.glasense.core.component.Text
import kotlinx.coroutines.launch

@Composable
fun BoxScope.HomeTopAppBar(
    menuController: (anchorBounds: Rect, items: List<GlasenseMenuItem>) -> Unit,
    menuItems: List<GlasenseMenuItem>,
    title: String,
    backdrop: Backdrop,
    viewModel: TodoViewModel,
    newTodoGroupId: Int?
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val selectedItemCount by viewModel.selectedItemCount.collectAsState()
    val isSelectionModeActive by viewModel.isSelectionModeActive.collectAsState()
    val isSearchBoxOpen by viewModel.isSearchBoxOpen.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var lastNonZeroSelected by remember { mutableIntStateOf(1) }

    if (selectedItemCount != 0) {
        lastNonZeroSelected = selectedItemCount
    }
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var isComposed by remember { mutableStateOf(isSelectionModeActive) }
    var isGone by remember { mutableStateOf(isSelectionModeActive) }
    val targetBlurRadius = with(density) {
        16.dp.toPx()
    }
    val topBarAlphaAnimation = remember { Animatable(if (isSelectionModeActive) 1f else 0f) }

    val topBarBlurAnimation =
        remember { Animatable(if (isSelectionModeActive) 0f else targetBlurRadius) }

    LaunchedEffect(isSelectionModeActive) {
        if (isSelectionModeActive) {
            isComposed = true
            scope.launch { topBarAlphaAnimation.animateTo(1f, tween(300)) }
            topBarBlurAnimation.animateTo(0f, tween(300))
            isGone = true
        } else {
            isGone = false
            scope.launch { topBarAlphaAnimation.animateTo(0f, tween(300)) }
            topBarBlurAnimation.animateTo(targetBlurRadius, tween(300))
            isComposed = false
        }
    }
    val resolvedSelectionTitle = stringResource(
        R.string.selected_todos,
        lastNonZeroSelected
    )

    val darkTheme = isAppInDarkTheme()

    val shadowRadiusPx = with(LocalDensity.current) { 32.dp.toPx() }
    val shadowDyPx = with(LocalDensity.current) { 16.dp.toPx() }

    val shadowPaint = remember {
        Paint().nativePaint.apply {
            isAntiAlias = true
            maskFilter = BlurMaskFilter(shadowRadiusPx, BlurMaskFilter.Blur.NORMAL)
        }
    }
    val shadowBaseColor = if (darkTheme) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(
        alpha = 0.1f
    )

    val hapticController = LocalHapticFeedback.current
    var isSearchBoxComposed by remember { mutableStateOf(false) }
    val searchBoxAlphaAnimation = remember { Animatable(if (isSearchBoxOpen) 1f else 0f) }
    val searchIconWidthAnimation = remember { Animatable(if (isSearchBoxOpen) 0f else 1f) }

    val searchBoxBlurAnimation =
        remember { Animatable(if (isSearchBoxOpen) 0f else targetBlurRadius) }

    val materialEffect = rememberMaterialRenderEffectOrNull(MaterialRecipes.appBar())

    val glass = LocalGlasenseSettings.current.liquidGlass

    val cardBackground = AppColors.cardBackground

    LaunchedEffect(isSearchBoxOpen) {
        if (isSearchBoxOpen) {
            isSearchBoxComposed = true
            hapticController.performHapticFeedback(HapticFeedbackType.ContextClick)
            scope.launch { searchBoxAlphaAnimation.animateTo(1f, tween(300)) }
            scope.launch { searchIconWidthAnimation.animateTo(0f, tween(300)) }
            searchBoxBlurAnimation.animateTo(0f, tween(300))
        } else {
            scope.launch { searchBoxAlphaAnimation.animateTo(0f, tween(300)) }
            scope.launch { searchIconWidthAnimation.animateTo(1f, spring(0.8f, 400f)) }
            searchBoxBlurAnimation.animateTo(targetBlurRadius, tween(300))
            isSearchBoxComposed = false
        }
    }

    GlasenseDynamicSmallTitle(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .mainPageTopBarPlacement(),
        title = if (isComposed) resolvedSelectionTitle else null,
        textStyle = TextStyle(fontFeatureSettings = "tnum"),
        statusBarHeight = statusBarHeight,
        isVisible = true,
        backdrop = backdrop,
        surfaceColor = AppColors.pageBackground
    ) {
        var coordinatesCaptured by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val sharedInteractionSource = remember { MutableInteractionSource() }
        val searchDescription = stringResource(R.string.search_all_todos)
        val sortDescription = stringResource(R.string.sort)
        val addDescription = stringResource(R.string.add_new_todo)

        Box(modifier = Modifier.fillMaxWidth()) {
            if (!isGone) {
                MainPageTitle(
                    title = title,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(end = 164.dp)
                        .graphicsLayer {
                            alpha = (1 - topBarAlphaAnimation.value) *
                                    (1 - searchBoxAlphaAnimation.value)
                            val blurRadius = targetBlurRadius - topBarBlurAnimation.value
                            renderEffect = if (blurRadius > 0f) {
                                BlurEffect(
                                    radiusX = blurRadius,
                                    radiusY = blurRadius,
                                    edgeTreatment = TileMode.Decal
                                )
                            } else {
                                null
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = 1 - topBarAlphaAnimation.value
                            alpha *= 1 - searchBoxAlphaAnimation.value
                            val blurRadius = targetBlurRadius - topBarBlurAnimation.value
                            renderEffect = if (blurRadius > 0f) {
                                BlurEffect(
                                    radiusX = blurRadius,
                                    radiusY = blurRadius,
                                    edgeTreatment = TileMode.Decal
                                )
                            } else {
                                null
                            }
                        }
                        .align(Alignment.CenterEnd)
                        .height(MainPageActionTouchTargetSize)
                        .width(
                            MainPageActionButtonSize * searchIconWidthAnimation.value + 100.dp
                        )
                ) {
                    GlasenseButtonToolBar(
                        enabled = true,
                        interactionSource = sharedInteractionSource,
                        shape = Capsule(),
                        onClick = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clearAndSetSemantics { },
                        colors = AppButtonColors.action(),
                        visualInset = MainPageActionVisualInset
                    ) {
                        Row(
                            modifier = Modifier.height(MainPageActionButtonSize)
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(MainPageActionButtonSize)
                                    .width(
                                        MainPageActionButtonSize *
                                                searchIconWidthAnimation.value
                                    )
                                    .wrapContentSize(
                                        unbounded = true,
                                        align = Alignment.CenterEnd
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                MainPageActionIcon(
                                    painter = painterResource(id = R.drawable.ic_magnifying_glass),
                                    contentDescription = null,
                                    optics = MainPageActionIconOptics.Search,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            alpha = 1 - searchBoxAlphaAnimation.value
                                            val blurRadius =
                                                targetBlurRadius - searchBoxBlurAnimation.value
                                            renderEffect = if (blurRadius > 0f) {
                                                BlurEffect(
                                                    radiusX = blurRadius,
                                                    radiusY = blurRadius,
                                                    edgeTreatment = TileMode.Decal
                                                )
                                            } else {
                                                null
                                            }
                                        },
                                    tint = AppColors.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Box(
                                modifier = Modifier.size(MainPageActionButtonSize),
                                contentAlignment = Alignment.Center
                            ) {
                                MainPageActionIcon(
                                    painter = painterResource(id = R.drawable.ic_sort),
                                    contentDescription = null,
                                    optics = MainPageActionIconOptics.Sort,
                                    tint = AppColors.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Box(
                                modifier = Modifier.size(MainPageActionButtonSize),
                                contentAlignment = Alignment.Center
                            ) {
                                MainPageActionIcon(
                                    painter = painterResource(id = R.drawable.ic_add_large),
                                    contentDescription = null,
                                    optics = MainPageActionIconOptics.Add,
                                    tint = AppColors.primary
                                )
                            }
                        }
                    }

                    if (!isSearchBoxComposed && !isSelectionModeActive) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(MainPageActionTouchTargetSize)
                                    .width(
                                        MainPageActionTouchTargetSize *
                                                searchIconWidthAnimation.value
                                    )
                                    .semantics {
                                        contentDescription = searchDescription
                                    }
                                    .clickable(
                                        interactionSource = sharedInteractionSource,
                                        indication = null,
                                        role = Role.Button
                                    ) {
                                        viewModel.openSearchBox()
                                    }
                            )
                            Box(
                                modifier = Modifier
                                    .size(MainPageActionTouchTargetSize)
                                    .onGloballyPositioned { coordinates ->
                                        coordinatesCaptured = coordinates
                                    }
                                    .semantics {
                                        contentDescription = sortDescription
                                    }
                                    .clickable(
                                        interactionSource = sharedInteractionSource,
                                        indication = null,
                                        role = Role.Button
                                    ) {
                                        coordinatesCaptured?.let {
                                            menuController(it.boundsInWindow(), menuItems)
                                        }
                                    }
                            )
                            Box(
                                modifier = Modifier
                                    .size(MainPageActionTouchTargetSize)
                                    .semantics {
                                        contentDescription = addDescription
                                    }
                                    .clickable(
                                        interactionSource = sharedInteractionSource,
                                        indication = null,
                                        role = Role.Button
                                    ) {
                                        viewModel.showBottomSheet(groupId = newTodoGroupId)
                                    }
                            )
                        }
                    }
                }
            }
            if (isComposed) {
                GlasenseButtonAdaptable(
                    width = { 48.dp },
                    height = { 48.dp },
                    enabled = true,
                    shape = CircleShape,
                    onClick = { viewModel.clearSelections() },
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = topBarAlphaAnimation.value
                            renderEffect = if (topBarBlurAnimation.value > 0f) {
                                BlurEffect(
                                    radiusX = topBarBlurAnimation.value,
                                    radiusY = topBarBlurAnimation.value,
                                    edgeTreatment = TileMode.Decal
                                )
                            } else {
                                null
                            }
                        }
                        .align(Alignment.TopStart),
                    colors = AppButtonColors.action()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_cross),
                        contentDescription = stringResource(R.string.exit_selection_mode),
                        modifier = Modifier.width(32.dp)
                    )
                }
                GlasenseButtonAdaptable(
                    width = { 48.dp },
                    height = { 48.dp },
                    enabled = true,
                    shape = CircleShape,
                    onClick = { viewModel.toggleSelectAllItems() },
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = topBarAlphaAnimation.value
                            renderEffect = if (topBarBlurAnimation.value > 0f) {
                                BlurEffect(
                                    radiusX = topBarBlurAnimation.value,
                                    radiusY = topBarBlurAnimation.value,
                                    edgeTreatment = TileMode.Decal
                                )
                            } else {
                                null
                            }
                        }
                        .align(Alignment.TopEnd),
                    colors = AppButtonColors.action()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_square_dashed),
                        contentDescription = stringResource(R.string.select_all),
                        modifier = Modifier.width(32.dp)
                    )
                }
            }
        }
    }

    val searchFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    if (isSearchBoxComposed) {
        BackHandler { viewModel.onSearchCloseIconClick() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .mainPageTopBarFrame()
                .drawBehind {
                    val finalAlpha = searchBoxAlphaAnimation.value
                    if (finalAlpha > 0f) {
                        val paintColor =
                            shadowBaseColor.copy(alpha = shadowBaseColor.alpha * finalAlpha)
                        shadowPaint.color = paintColor.toArgb()

                        drawIntoCanvas { canvas ->
                            canvas.save()
                            canvas.translate(0f, shadowDyPx)
                            canvas.nativeCanvas.drawRoundRect(
                                0f,
                                0f,
                                size.width,
                                size.height,
                                size.height / 2,
                                size.height / 2,
                                shadowPaint
                            )
                            canvas.restore()
                        }
                    }
                }
                .graphicsLayer {
                    if (searchBoxBlurAnimation.value > 0f) {
                        renderEffect = BlurEffect(
                            radiusX = searchBoxBlurAnimation.value,
                            radiusY = searchBoxBlurAnimation.value,
                            edgeTreatment = TileMode.Decal
                        )
                    }
                    alpha = searchBoxAlphaAnimation.value
                }
                .then(
                    if (supportsRuntimeShaderEffect()) Modifier.drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            padding = 32.dp.toPx() * 2
                            materialEffect?.let { effect(it) }
                            blur(
                                radius = if (glass) 8.dp.toPx() else 32.dp.toPx(),
                                edgeTreatment = TileMode.Decal
                            )
                            if (glass) lens(16f.dp.toPx(), 48f.dp.toPx())
                        },
                        onDrawSurface = {
                            drawRect(
                                cardBackground, alpha = 0.3f
                            )
                        }
                    ) else Modifier.clip(Capsule()))
        ) {
            if (!glass) {
                Box(
                    modifier = Modifier
                        .glasenseHighlight(cornerRadius = 100.dp)
                        .fillMaxSize()
                )
            }
            if (glass) {
                Box(
                    modifier = Modifier
                        .drawBackdrop(
                            backdrop = rememberLayerBackdrop { },
                            shape = { Capsule() },
                            shadow = null,
                            innerShadow = null,
                            highlight = {
                                Highlight.Default.copy(
                                    style = HighlightStyle.Default(
                                        angle = 90f
                                    )
                                )
                            },
                            effects = {

                            })
                        .fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                .graphicsLayer {
                    alpha = if (supportsRuntimeShaderEffect()) 0f else 1f
                }
                    .background(color = AppColors.scrimNormal.compositeOver(AppColors.pageBackground))
                    .fillMaxSize()
            )
            Icon(
                painter = painterResource(R.drawable.ic_magnifying_glass),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(10.dp)
                    .size(28.dp),
                tint = AppColors.contentVariant
            )
            LaunchedEffect(Unit) {
                searchFocusRequester.requestFocus()
            }
            BasicTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .focusRequester(searchFocusRequester)
                    .height(48.dp)
                    .padding(start = 44.dp, end = 42.dp)
                    .fillMaxWidth(),
                cursorBrush = SolidColor(AppColors.primary),
                textStyle = TextStyle(
                    color = AppColors.content,
                    fontSize = 16.sp
                ),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_all_todos),
                                color = AppColors.contentVariant,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            Icon(
                painter = painterResource(R.drawable.ic_xmark_bold_circle_fill),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(
                        enabled = true,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isSearchBoxOpen) {
                            keyboard?.hide()
                        }
                        viewModel.onSearchCloseIconClick()
                    }
                    .padding(14.dp)
                    .size(20.dp),
                tint = AppColors.content.copy(alpha = 0.4f)
            )
        }
    }
}
