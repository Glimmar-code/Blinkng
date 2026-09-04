from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"Expected snippet not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# Home/feed: right swipe opens the app three-dot menu.
replace_once(
    "app/src/main/java/com/example/ui/screens/FeedScreen.kt",
    """                                    onDragEnd = {\n                                        if (horizontalDrag <= -openMenuThreshold) {\n                                            onOpenMenu()\n                                        }\n                                        horizontalDrag = 0f\n                                    },""",
    """                                    onDragEnd = {\n                                        if (horizontalDrag >= openMenuThreshold) {\n                                            onOpenMenu()\n                                        }\n                                        horizontalDrag = 0f\n                                    },""",
)

# Reels: right swipe goes back to feed, left swipe opens the reel author's profile.
replace_once(
    "app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt",
    """                        when {\n                            horizontalDrag <= -swipeThreshold -> onSwipeToHome()\n                            horizontalDrag >= swipeThreshold -> onSwipeToProfile()\n                        }""",
    """                        when {\n                            horizontalDrag >= swipeThreshold -> onSwipeToHome()\n                            horizontalDrag <= -swipeThreshold -> onSwipeToProfile()\n                        }""",
)

# Preserve the uploaded video's original aspect ratio instead of zoom-cropping it.
replace_once(
    "app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt",
    "resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM",
    "resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT",
)

# Non-active reel preview should match the full, uncropped playback composition too.
replace_once(
    "app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt",
    """            contentScale = ContentScale.Crop,\n            modifier = Modifier.fillMaxSize()\n        )\n    }\n}\n\n@Composable\nprivate fun ReelVideo(""",
    """            contentScale = ContentScale.Fit,\n            modifier = Modifier.fillMaxSize()\n        )\n    }\n}\n\n@Composable\nprivate fun ReelVideo(""",
)
