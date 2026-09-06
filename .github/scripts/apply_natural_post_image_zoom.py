from pathlib import Path

path = Path("app/src/main/java/com/example/ui/components/PostCard.kt")
text = path.read_text()

# Gesture/import updates for pinch zoom and explicit fullscreen close control.
text = text.replace(
    "import androidx.compose.foundation.gestures.detectVerticalDragGestures\n",
    "import androidx.compose.foundation.gestures.detectTransformGestures\n",
)
text = text.replace(
    "import androidx.compose.material.icons.filled.Campaign\n",
    "import androidx.compose.material.icons.filled.Campaign\nimport androidx.compose.material.icons.filled.Close\n",
)
text = text.replace(
    "import androidx.compose.ui.geometry.Size\n",
    "import androidx.compose.ui.geometry.Offset\nimport androidx.compose.ui.geometry.Size\n",
)

old_media = '''            if (displayImages.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    if (displayImages.size == 1) {
                        AsyncImage(
                            model = displayImages.first(),
                            contentDescription = post.altText?.takeIf(String::isNotBlank) ?: "Post image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.45f)
                                .clickable {
                                    imagePage = 0
                                    showImageFullscreen = true
                                }
                        )
                    } else {
                        val mediaState = rememberLazyListState()
                        LazyRow(
                            state = mediaState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(
                                items = displayImages,
                                key = { _, image -> image }
                            ) { index, image ->
                                AsyncImage(
                                    model = image,
                                    contentDescription = "Post image ${index + 1} of ${displayImages.size}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillParentMaxWidth()
                                        .aspectRatio(1.45f)
                                        .clickable {
                                            imagePage = index
                                            showImageFullscreen = true
                                        }
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.62f)
                        ) {
                            Text(
                                text = "${mediaState.firstVisibleItemIndex + 1}/${displayImages.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
'''

new_media = '''            if (displayImages.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    if (displayImages.size == 1) {
                        NaturalAspectPostImage(
                            imageUrl = displayImages.first(),
                            contentDescription = post.altText?.takeIf(String::isNotBlank) ?: "Post image",
                            onClick = {
                                imagePage = 0
                                showImageFullscreen = true
                            }
                        )
                    } else {
                        val mediaState = rememberLazyListState()
                        LazyRow(
                            state = mediaState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(
                                items = displayImages,
                                key = { _, image -> image }
                            ) { index, image ->
                                NaturalAspectPostImage(
                                    imageUrl = image,
                                    contentDescription = "Post image ${index + 1} of ${displayImages.size}",
                                    modifier = Modifier.fillParentMaxWidth(),
                                    onClick = {
                                        imagePage = index
                                        showImageFullscreen = true
                                    }
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.62f)
                        ) {
                            Text(
                                text = "${mediaState.firstVisibleItemIndex + 1}/${displayImages.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
'''

if old_media not in text:
    raise SystemExit("Post media block not found; refusing an unsafe partial patch")
text = text.replace(old_media, new_media)

fullscreen_start = text.index("/** Tap a feed image to expand it. Drag downward to dismiss. */")
new_tail = r'''/**
 * Renders feed media at the uploaded image's own aspect ratio instead of forcing
 * every post into one crop. The full feed width is preserved while portrait,
 * square, and landscape images are allowed to use the vertical space they need.
 */
@Composable
private fun NaturalAspectPostImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var imageAspectRatio by remember(imageUrl) { mutableFloatStateOf(1f) }

    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        onSuccess = { state ->
            val drawable = state.result.drawable
            val width = drawable.intrinsicWidth
            val height = drawable.intrinsicHeight
            if (width > 0 && height > 0) {
                imageAspectRatio = width.toFloat() / height.toFloat()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(imageAspectRatio.coerceAtLeast(0.01f))
            .clickable(onClick = onClick)
    )
}

/**
 * Tap a feed image to open it full-screen. Pinch with two fingers to zoom up to
 * 5x and pan around the enlarged image. Back or the close button exits safely.
 */
@Composable
private fun ImageFullscreenDialog(
    images: List<String>,
    initialPage: Int,
    onDismiss: () -> Unit
) {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    )
    var entered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun dismissAnimated() {
        if (!entered) return
        entered = false
        scope.launch {
            delay(170)
            onDismiss()
        }
    }

    LaunchedEffect(Unit) { entered = true }

    Dialog(
        onDismissRequest = ::dismissAnimated,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = entered,
            enter = fadeIn(tween(280)) + scaleIn(
                initialScale = 0.94f,
                animationSpec = tween(280)
            ),
            exit = fadeOut(tween(160)) + scaleOut(
                targetScale = 0.97f,
                animationSpec = tween(160)
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.97f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyRow(
                        state = state,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(images, key = { _, image -> image }) { index, image ->
                            ZoomableFullscreenImage(
                                imageUrl = image,
                                contentDescription = "Fullscreen image ${index + 1} of ${images.size}"
                            )
                        }
                    }

                    IconButton(
                        onClick = ::dismissAnimated,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 18.dp, end = 14.dp)
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.48f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close image",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableFullscreenImage(
    imageUrl: String,
    contentDescription: String
) {
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillParentMaxWidth()
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imageUrl) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        val maxX = (size.width * (newScale - 1f)) / 2f
                        val maxY = (size.height * (newScale - 1f)) / 2f

                        scale = newScale
                        offset = if (newScale <= 1.01f) {
                            Offset.Zero
                        } else {
                            Offset(
                                x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
'''
text = text[:fullscreen_start] + new_tail

path.write_text(text)
print("Updated PostCard.kt with natural aspect-ratio media and pinch zoom")
