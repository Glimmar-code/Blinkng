from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


android_path = Path("app/src/main/java/com/example/ui/screens/ConnectHubPremiumPanel.kt")
android = android_path.read_text()

if "openCategoryIndex" in android:
    raise RuntimeError("Android Connect slide-over patch already appears to be applied")

android = replace_once(
    android,
    "import androidx.compose.animation.slideInVertically\n",
    "import androidx.compose.animation.slideInHorizontally\nimport androidx.compose.animation.slideInVertically\nimport androidx.compose.animation.slideOutHorizontally\n",
    "Android horizontal animation imports",
)
android = replace_once(
    android,
    "import androidx.compose.foundation.layout.fillMaxSize\n",
    "import androidx.compose.foundation.layout.fillMaxHeight\nimport androidx.compose.foundation.layout.fillMaxSize\n",
    "Android fillMaxHeight import",
)
android = replace_once(
    android,
    "import androidx.compose.material3.HorizontalDivider\nimport androidx.compose.material3.Icon\n",
    "import androidx.compose.material3.HorizontalDivider\nimport androidx.compose.material3.Icon\nimport androidx.compose.material3.IconButton\n",
    "Android IconButton import",
)
android = replace_once(
    android,
    "import androidx.compose.ui.util.lerp\n",
    "import androidx.compose.ui.util.lerp\nimport androidx.compose.ui.window.Dialog\nimport androidx.compose.ui.window.DialogProperties\n",
    "Android Dialog imports",
)
android = replace_once(
    android,
    "    val pagerState = rememberPagerState(pageCount = { categories.size })\n",
    "    val pagerState = rememberPagerState(pageCount = { categories.size })\n    var openCategoryIndex by rememberSaveable { mutableStateOf<Int?>(null) }\n",
    "Android open category state",
)

old_category_call = '''        CategoryTabBar(
            categories = categories,
            directoryCategories = directoryCategories,
            badgeCounts = badgeCounts,
            pagerState = pagerState,
            coroutineScope = coroutineScope
        )
'''
new_category_call = '''        CategoryTabBar(
            categories = categories,
            directoryCategories = directoryCategories,
            badgeCounts = badgeCounts,
            onOpenCategory = { targetIndex ->
                coroutineScope.launch {
                    pagerState.scrollToPage(targetIndex)
                    openCategoryIndex = targetIndex
                }
            }
        )
'''
android = replace_once(android, old_category_call, new_category_call, "Android category list callback")

pager_start_marker = "\n        HorizontalPager(\n            state = pagerState,"
pager_end_marker = "\n\n        Spacer(Modifier.height(4.dp))\n        TextButton(onClick = actions.refresh"
pager_start = android.find(pager_start_marker)
if pager_start < 0:
    raise RuntimeError("Android category pager start marker not found")
pager_end = android.find(pager_end_marker, pager_start)
if pager_end < 0:
    raise RuntimeError("Android category pager end marker not found")
pager_block = android[pager_start:pager_end]
android = android[:pager_start] + android[pager_end:]
pager_block = replace_once(
    pager_block,
    "            modifier = Modifier\n                .fillMaxWidth()\n                .height(PagerHeight),",
    "            modifier = Modifier\n                .fillMaxWidth()\n                .weight(1f),",
    "Android overlay pager sizing",
)

panel = '''

    openCategoryIndex?.let { targetIndex ->
        val openCategory = categories[targetIndex]
        var panelVisible by remember(targetIndex) { mutableStateOf(false) }

        LaunchedEffect(targetIndex) {
            panelVisible = true
        }

        fun closeCategoryPanel() {
            if (!panelVisible) return
            panelVisible = false
            coroutineScope.launch {
                delay(230)
                if (openCategoryIndex == targetIndex) {
                    openCategoryIndex = null
                }
            }
        }

        Dialog(
            onDismissRequest = { closeCategoryPanel() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .28f)),
                contentAlignment = Alignment.CenterStart
            ) {
                AnimatedVisibility(
                    visible = panelVisible,
                    modifier = Modifier.align(Alignment.CenterStart),
                    enter = slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(290, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(180)),
                    exit = slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(180))
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(.95f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
                        color = MaterialTheme.colorScheme.background,
                        shadowElevation = 18.dp
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 10.dp, top = 18.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(15.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .65f)
                                ) {
                                    Icon(
                                        openCategory.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(10.dp).size(21.dp)
                                    )
                                }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(openCategory.label, fontSize = 19.sp, fontWeight = FontWeight.Black)
                                    Text(
                                        "Connect workflow",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { closeCategoryPanel() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close ${openCategory.label}")
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))
'''
panel += pager_block
panel += '''
                        }
                    }
                }
            }
        }
    }
'''

android = replace_once(
    android,
    "\n    if (form != HubForm.NONE) {",
    panel + "\n    if (form != HubForm.NONE) {",
    "Android slide-over insertion",
)

old_category_signature = '''private fun CategoryTabBar(
    categories: List<ConnectCategory>,
    directoryCategories: List<ConnectDirectoryCategory>,
    badgeCounts: List<Int>,
    pagerState: PagerState,
    coroutineScope: CoroutineScope
) {'''
new_category_signature = '''private fun CategoryTabBar(
    categories: List<ConnectCategory>,
    directoryCategories: List<ConnectDirectoryCategory>,
    badgeCounts: List<Int>,
    onOpenCategory: (Int) -> Unit
) {'''
android = replace_once(android, old_category_signature, new_category_signature, "Android CategoryTabBar signature")

old_click = '''                    .clickable {
                        selectedSlug = item.slug
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetIndex)
                        }
                    },'''
new_click = '''                    .clickable {
                        selectedSlug = item.slug
                        onOpenCategory(targetIndex)
                    },'''
android = replace_once(android, old_click, new_click, "Android category click behavior")

if "HorizontalPager(\n            state = pagerState" not in android:
    raise RuntimeError("Android overlay lost its category pager")
if android.count("openCategoryIndex") < 4:
    raise RuntimeError("Android slide-over state was not wired completely")
android_path.write_text(android)


desktop_path = Path("desktopApp/src/main/kotlin/com/blinkng/desktop/ui/ContentScreens.kt")
desktop = desktop_path.read_text()
if "desktopConnectSlidePanel" in desktop:
    raise RuntimeError("Desktop Connect slide-over patch already appears to be applied")

desktop = replace_once(
    desktop,
    "package com.blinkng.desktop.ui\n\n",
    "package com.blinkng.desktop.ui\n\nimport androidx.compose.animation.AnimatedVisibility\nimport androidx.compose.animation.core.tween\nimport androidx.compose.animation.fadeIn\nimport androidx.compose.animation.fadeOut\nimport androidx.compose.animation.slideInHorizontally\nimport androidx.compose.animation.slideOutHorizontally\n",
    "Desktop animation imports",
)
desktop = replace_once(
    desktop,
    "import androidx.compose.foundation.layout.fillMaxSize\n",
    "import androidx.compose.foundation.layout.fillMaxHeight\nimport androidx.compose.foundation.layout.fillMaxSize\n",
    "Desktop fillMaxHeight import",
)
desktop = replace_once(
    desktop,
    "import androidx.compose.material.icons.rounded.ChatBubbleOutline\n",
    "import androidx.compose.material.icons.rounded.ChatBubbleOutline\nimport androidx.compose.material.icons.rounded.Close\n",
    "Desktop Close icon import",
)

connect_start = desktop.find("@Composable\nfun ConnectScreen(state: DesktopAppState) {")
connect_end = desktop.find("\n@Composable\nfun StoreScreen(state: DesktopAppState) {", connect_start)
if connect_start < 0 or connect_end < 0:
    raise RuntimeError("Desktop ConnectScreen boundaries not found")

new_connect = '''@Composable
fun ConnectScreen(state: DesktopAppState) {
    var listings by remember { mutableStateOf<List<DesktopConnectListing>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("community") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        listings = runCatching { state.client.fetchConnectListings() }.getOrDefault(emptyList())
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ScreenHeader("Connect", "Study circles, mentors, communities and campus connections")
                    Button(onClick = { showCreate = true }) { Text("Create") }
                }
            }
            if (loading) item { LoadingRow() }
            items(listings, key = { it.id }) { listing ->
                Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(listing.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(listing.description)
                        Text(
                            listOfNotNull(listing.listingType, listing.university, listing.department, listing.academicLevel, listing.location).joinToString(" • "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        if (listing.tags.isNotEmpty()) Text(listing.tags.joinToString("  ") { "#$it" }, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
            }
        }

        if (showCreate) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = .24f)),
            )
        }

        AnimatedVisibility(
            visible = showCreate,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(290)) + fadeIn(tween(180)),
            exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(220)) + fadeOut(tween(160)),
            label = "desktopConnectSlidePanel",
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(.95f).fillMaxHeight(),
                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Create Connect listing", fontWeight = FontWeight.Black, fontSize = 24.sp)
                            Text(
                                "Create without pushing the form to the end of the Connect list.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { showCreate = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Connect create panel")
                        }
                    }
                    HorizontalDivider()
                    OutlinedTextField(type, { type = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Type") }, singleLine = true)
                    OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") })
                    OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 4)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching { state.client.createConnectListing(type, title, description) }
                                        .onSuccess {
                                            title = ""
                                            description = ""
                                            showCreate = false
                                            reload()
                                        }
                                }
                            },
                            enabled = title.isNotBlank(),
                        ) { Text("Publish") }
                        OutlinedButton(onClick = { showCreate = false }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}
'''

desktop = desktop[:connect_start] + new_connect + desktop[connect_end:]
desktop_path.write_text(desktop)


parity_path = Path("platform-parity/changes.md")
parity = parity_path.read_text()
row = "| 2026-09-08 | Connect slide-over workflows | Connect directory choices now open a left-entering 95%-width animated workflow page instead of rendering the selected workflow after the full directory list; existing listing, request, form, messaging and Supabase actions are preserved | Desktop Connect creation now opens in the equivalent left-entering 95%-width animated slide-over instead of injecting inputs into the scrolling list | Presentation/navigation-only change; existing Connect backend contracts and server-authoritative behavior are unchanged |\n"
separator = "|---|---|---|---|---|\n"
if row not in parity:
    parity = replace_once(parity, separator, separator + row, "Parity ledger row")
parity_path.write_text(parity)

print("Applied focused Android + Windows Connect slide-over UX patch")
