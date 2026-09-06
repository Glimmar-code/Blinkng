from pathlib import Path
import runpy

# Reapply the original feature patch, then correct the LazyRow page-width scope.
runpy.run_path(".github/scripts/apply_natural_post_image_zoom.py", run_name="__main__")

path = Path("app/src/main/java/com/example/ui/components/PostCard.kt")
text = path.read_text()

old_call = '''                            ZoomableFullscreenImage(
                                imageUrl = image,
                                contentDescription = "Fullscreen image ${index + 1} of ${images.size}"
                            )'''
new_call = '''                            ZoomableFullscreenImage(
                                imageUrl = image,
                                contentDescription = "Fullscreen image ${index + 1} of ${images.size}",
                                modifier = Modifier.fillParentMaxWidth()
                            )'''

old_signature = '''private fun ZoomableFullscreenImage(
    imageUrl: String,
    contentDescription: String
) {'''
new_signature = '''private fun ZoomableFullscreenImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {'''

old_box = '''    Box(
        modifier = Modifier
            .fillParentMaxWidth()
            .fillMaxHeight(),'''
new_box = '''    Box(
        modifier = modifier
            .fillMaxHeight(),'''

for old, new, label in (
    (old_call, new_call, "fullscreen item call"),
    (old_signature, new_signature, "zoom image signature"),
    (old_box, new_box, "zoom image modifier"),
):
    if old not in text:
        raise SystemExit(f"{label} not found; refusing partial scope fix")
    text = text.replace(old, new, 1)

path.write_text(text)
print("Corrected fullscreen image width modifier scope")
