from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt")
text = path.read_text(encoding="utf-8")

anchor = '''        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 10.dp, bottom = 34.dp)
                .entranceEffect(delayMillis = 60),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = reel.authorAvatar,
                error = painterResource(R.drawable.ic_default_profile),
                fallback = painterResource(R.drawable.ic_default_profile),
                contentDescription = reel.author,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable { onProfileClick(reel.author) }
            )
            Spacer(Modifier.height(16.dp))
            ReelAction(
'''
replacement = '''        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 10.dp, bottom = 34.dp)
                .entranceEffect(delayMillis = 60),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReelAction(
'''

if replacement in text:
    print("Right-rail author avatar already removed")
elif anchor not in text:
    raise RuntimeError("Could not locate the right-rail Reel author avatar")
else:
    text = text.replace(anchor, replacement, 1)
    path.write_text(text, encoding="utf-8")
    print("Removed right-rail Reel author avatar")
