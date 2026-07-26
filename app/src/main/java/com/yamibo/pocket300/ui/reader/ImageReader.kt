package com.yamibo.pocket300.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.yamibo.pocket300.R
import com.yamibo.pocket300.ui.rememberPostImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val MINIMUM_ZOOM = 1f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val MAXIMUM_ZOOM = 5f

@Composable
internal fun ImageReader(
    images: List<String>,
    threadId: Int,
    currentPage: Int,
    preferences: ImageReaderPreferences,
    controlsVisible: Boolean,
    onCurrentPageChange: (Int) -> Unit,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return

    val page = currentPage.coerceIn(images.indices)
    val background = preferences.background.color
    CompositionLocalProvider(LocalContentColor provides preferences.background.contentColor) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(background),
        ) {
            key(preferences.mode) {
                when (preferences.mode) {
                    ImageReaderMode.LEFT_TO_RIGHT,
                    ImageReaderMode.RIGHT_TO_LEFT,
                    -> HorizontalImagePager(
                        images = images,
                        threadId = threadId,
                        currentPage = page,
                        preferences = preferences,
                        onCurrentPageChange = onCurrentPageChange,
                        onToggleControls = onToggleControls,
                    )

                    ImageReaderMode.VERTICAL -> VerticalImagePager(
                        images = images,
                        threadId = threadId,
                        currentPage = page,
                        preferences = preferences,
                        onCurrentPageChange = onCurrentPageChange,
                        onToggleControls = onToggleControls,
                    )

                    ImageReaderMode.WEBTOON -> WebtoonImageReader(
                        images = images,
                        threadId = threadId,
                        currentPage = page,
                        background = background,
                        tapNavigation = preferences.tapNavigation,
                        onCurrentPageChange = onCurrentPageChange,
                        onToggleControls = onToggleControls,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            ) {
                AnimatedVisibility(
                    visible = !controlsVisible && preferences.showPageNumber,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.68f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.reader_image_compact_progress, page + 1, images.size),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalImagePager(
    images: List<String>,
    threadId: Int,
    currentPage: Int,
    preferences: ImageReaderPreferences,
    onCurrentPageChange: (Int) -> Unit,
    onToggleControls: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = currentPage) { images.size }
    var currentPageZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState, onCurrentPageChange) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect(onCurrentPageChange)
    }
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage) pagerState.scrollToPage(currentPage)
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = preferences.mode == ImageReaderMode.RIGHT_TO_LEFT,
        userScrollEnabled = !currentPageZoomed,
        beyondViewportPageCount = 1,
        key = { images[it] },
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        PagedReaderImage(
            imageUrl = images[page],
            threadId = threadId,
            page = page,
            pageCount = images.size,
            mode = preferences.mode,
            scaleType = preferences.scaleType,
            tapNavigation = preferences.tapNavigation,
            isCurrentPage = page == pagerState.currentPage,
            onZoomChanged = { zoomed ->
                if (page == pagerState.currentPage) currentPageZoomed = zoomed
            },
            onTapAction = { action ->
                handleTapAction(
                    action = action,
                    currentPage = page,
                    pageCount = images.size,
                    onCurrentPageChange = onCurrentPageChange,
                    onToggleControls = onToggleControls,
                )
            },
        )
    }
}

@Composable
private fun VerticalImagePager(
    images: List<String>,
    threadId: Int,
    currentPage: Int,
    preferences: ImageReaderPreferences,
    onCurrentPageChange: (Int) -> Unit,
    onToggleControls: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = currentPage) { images.size }
    var currentPageZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState, onCurrentPageChange) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect(onCurrentPageChange)
    }
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage) pagerState.scrollToPage(currentPage)
    }

    VerticalPager(
        state = pagerState,
        userScrollEnabled = !currentPageZoomed,
        beyondViewportPageCount = 1,
        key = { images[it] },
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        PagedReaderImage(
            imageUrl = images[page],
            threadId = threadId,
            page = page,
            pageCount = images.size,
            mode = ImageReaderMode.VERTICAL,
            scaleType = preferences.scaleType,
            tapNavigation = preferences.tapNavigation,
            isCurrentPage = page == pagerState.currentPage,
            onZoomChanged = { zoomed ->
                if (page == pagerState.currentPage) currentPageZoomed = zoomed
            },
            onTapAction = { action ->
                handleTapAction(
                    action = action,
                    currentPage = page,
                    pageCount = images.size,
                    onCurrentPageChange = onCurrentPageChange,
                    onToggleControls = onToggleControls,
                )
            },
        )
    }
}

@Composable
private fun PagedReaderImage(
    imageUrl: String,
    threadId: Int,
    page: Int,
    pageCount: Int,
    mode: ImageReaderMode,
    scaleType: ImageReaderScaleType,
    tapNavigation: Boolean,
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onTapAction: (ImageReaderTapAction) -> Unit,
) {
    val contentDescription = stringResource(R.string.reader_image_description, page + 1, pageCount)
    when (scaleType) {
        ImageReaderScaleType.FIT_SCREEN -> ZoomableReaderImage(
            imageUrl = imageUrl,
            threadId = threadId,
            contentDescription = contentDescription,
            mode = mode,
            tapNavigation = tapNavigation,
            isCurrentPage = isCurrentPage,
            onZoomChanged = onZoomChanged,
            onTapAction = onTapAction,
        )

        ImageReaderScaleType.FIT_WIDTH -> FitWidthReaderImage(
            imageUrl = imageUrl,
            threadId = threadId,
            contentDescription = contentDescription,
            mode = mode,
            tapNavigation = tapNavigation,
            onZoomChanged = onZoomChanged,
            onTapAction = onTapAction,
        )
    }
}

@Composable
private fun ZoomableReaderImage(
    imageUrl: String,
    threadId: Int,
    contentDescription: String,
    mode: ImageReaderMode,
    tapNavigation: Boolean,
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onTapAction: (ImageReaderTapAction) -> Unit,
) {
    var scale by remember(imageUrl) { mutableFloatStateOf(MINIMUM_ZOOM) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val transformableState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val updatedScale = (scale * zoomChange).coerceIn(MINIMUM_ZOOM, MAXIMUM_ZOOM)
        val appliedZoom = updatedScale / scale
        val viewportCenter = Offset(viewport.width / 2f, viewport.height / 2f)
        val gestureCenter = if (centroid.x.isFinite() && centroid.y.isFinite()) {
            centroid
        } else {
            viewportCenter
        }
        offset = if (updatedScale == MINIMUM_ZOOM) {
            Offset.Zero
        } else {
            clampZoomOffset(
                offset = offset * appliedZoom +
                    panChange +
                    (gestureCenter - viewportCenter) * (1f - appliedZoom),
                scale = updatedScale,
                viewport = viewport,
            )
        }
        scale = updatedScale
    }

    LaunchedEffect(isCurrentPage, scale) {
        if (isCurrentPage) onZoomChanged(scale > MINIMUM_ZOOM)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(imageUrl, mode, tapNavigation) {
                viewport = size
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > MINIMUM_ZOOM) {
                            scale = MINIMUM_ZOOM
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_ZOOM
                            offset = clampZoomOffset(
                                offset = Offset(
                                    x = (size.width / 2f - tap.x) * (scale - 1f),
                                    y = (size.height / 2f - tap.y) * (scale - 1f),
                                ),
                                scale = scale,
                                viewport = size,
                            )
                        }
                    },
                    onTap = { tap ->
                        onTapAction(
                            imageReaderTapAction(
                                x = tap.x,
                                y = tap.y,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                mode = mode,
                                tapNavigation = tapNavigation,
                            ),
                        )
                    },
                )
            }
            .transformable(
                state = transformableState,
                lockRotationOnZoomPan = true,
                canPan = { scale > MINIMUM_ZOOM },
            ),
        contentAlignment = Alignment.Center,
    ) {
        ReaderImage(
            imageUrl = imageUrl,
            threadId = threadId,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    transformOrigin = TransformOrigin.Center
                },
        )
    }
}

@Composable
private fun FitWidthReaderImage(
    imageUrl: String,
    threadId: Int,
    contentDescription: String,
    mode: ImageReaderMode,
    tapNavigation: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onTapAction: (ImageReaderTapAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { onZoomChanged(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageUrl, mode, tapNavigation) {
                detectTapGestures(
                    onDoubleTap = {
                        coroutineScope.launch { scrollState.animateScrollTo(0) }
                    },
                    onTap = { tap ->
                        onTapAction(
                            imageReaderTapAction(
                                x = tap.x,
                                y = tap.y,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                mode = mode,
                                tapNavigation = tapNavigation,
                            ),
                        )
                    },
                )
            },
    ) {
        val viewportHeight = constraints.maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            Layout(
                content = {
                    ReaderImage(
                        imageUrl = imageUrl,
                        threadId = threadId,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { measurables, layoutConstraints ->
                val image = measurables.single().measure(
                    layoutConstraints.copy(
                        minHeight = 0,
                        maxHeight = Constraints.Infinity,
                    ),
                )
                val contentHeight = maxOf(viewportHeight, image.height)
                layout(layoutConstraints.maxWidth, contentHeight) {
                    image.placeRelative(
                        x = (layoutConstraints.maxWidth - image.width) / 2,
                        y = ((viewportHeight - image.height).coerceAtLeast(0)) / 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun WebtoonImageReader(
    images: List<String>,
    threadId: Int,
    currentPage: Int,
    background: Color,
    tapNavigation: Boolean,
    onCurrentPageChange: (Int) -> Unit,
    onToggleControls: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)

    LaunchedEffect(listState, onCurrentPageChange) {
        snapshotFlow { mostVisibleItemIndex(listState.layoutInfo) }
            .distinctUntilChanged()
            .collect { visiblePage ->
                if (visiblePage >= 0) onCurrentPageChange(visiblePage)
            }
    }
    LaunchedEffect(currentPage) {
        if (currentPage != mostVisibleItemIndex(listState.layoutInfo)) {
            listState.animateScrollToItem(currentPage)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(images.size, tapNavigation, currentPage) {
                detectTapGestures { tap ->
                    val action = imageReaderTapAction(
                        x = tap.x,
                        y = tap.y,
                        width = size.width.toFloat(),
                        height = size.height.toFloat(),
                        mode = ImageReaderMode.WEBTOON,
                        tapNavigation = tapNavigation,
                    )
                    handleTapAction(
                        action = action,
                        currentPage = currentPage,
                        pageCount = images.size,
                        onCurrentPageChange = onCurrentPageChange,
                        onToggleControls = onToggleControls,
                    )
                }
            },
    ) {
        items(
            count = images.size,
            key = { images[it] },
        ) { page ->
            ReaderImage(
                imageUrl = images[page],
                threadId = threadId,
                contentDescription = stringResource(
                    R.string.reader_image_description,
                    page + 1,
                    images.size,
                ),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .heightIn(min = 96.dp),
            )
        }
    }
}

@Composable
private fun ReaderImage(
    imageUrl: String,
    threadId: Int,
    contentDescription: String,
    contentScale: ContentScale,
    modifier: Modifier,
) {
    SubcomposeAsyncImage(
        model = rememberPostImageRequest(imageUrl, threadId),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            Box(
                Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        },
        error = {
            Box(
                Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.BrokenImage, contentDescription = null)
                    Text(
                        text = stringResource(R.string.reader_image_load_error),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        success = { SubcomposeAsyncImageContent() },
    )
}

private fun handleTapAction(
    action: ImageReaderTapAction,
    currentPage: Int,
    pageCount: Int,
    onCurrentPageChange: (Int) -> Unit,
    onToggleControls: () -> Unit,
) {
    when (action) {
        ImageReaderTapAction.PREVIOUS,
        ImageReaderTapAction.NEXT,
        -> onCurrentPageChange(imageReaderPageAfterAction(currentPage, pageCount, action))

        ImageReaderTapAction.TOGGLE_CONTROLS -> onToggleControls()
        ImageReaderTapAction.NONE -> Unit
    }
}

private fun clampZoomOffset(offset: Offset, scale: Float, viewport: IntSize): Offset {
    val horizontalLimit = viewport.width * (scale - 1f) / 2f
    val verticalLimit = viewport.height * (scale - 1f) / 2f
    return Offset(
        x = offset.x.coerceIn(-horizontalLimit, horizontalLimit),
        y = offset.y.coerceIn(-verticalLimit, verticalLimit),
    )
}

private fun mostVisibleItemIndex(layoutInfo: androidx.compose.foundation.lazy.LazyListLayoutInfo): Int {
    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    return layoutInfo.visibleItemsInfo.maxByOrNull { item ->
        val visibleStart = maxOf(item.offset, viewportStart)
        val visibleEnd = minOf(item.offset + item.size, viewportEnd)
        (visibleEnd - visibleStart).coerceAtLeast(0)
    }?.index ?: -1
}

private val ImageReaderBackground.color: Color
    get() = when (this) {
        ImageReaderBackground.BLACK -> Color.Black
        ImageReaderBackground.GRAY -> Color(0xFF202124)
        ImageReaderBackground.WHITE -> Color.White
    }

private val ImageReaderBackground.contentColor: Color
    get() = when (this) {
        ImageReaderBackground.WHITE -> Color.Black
        ImageReaderBackground.BLACK,
        ImageReaderBackground.GRAY,
        -> Color.White
    }
