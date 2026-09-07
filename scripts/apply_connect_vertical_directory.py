from pathlib import Path

TARGET = Path("app/src/main/java/com/example/ui/screens/ConnectHubPremiumPanel.kt")
WORKFLOW = Path(".github/workflows/apply-connect-vertical-directory.yml")
SELF = Path("scripts/apply_connect_vertical_directory.py")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one {label} match, found {count}")
    return text.replace(old, new, 1)


text = TARGET.read_text(encoding="utf-8")

text = replace_once(
    text,
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n",
    "Compose runtime import",
)

text = replace_once(
    text,
    "import com.example.data.models.UserProfile\n",
    "import com.example.data.models.UserProfile\nimport com.example.data.repository.ConnectCategoryCatalogRepository\nimport com.example.data.repository.ConnectDirectoryCategory\n",
    "Connect catalog imports",
)

text = replace_once(
    text,
    "    val coroutineScope = rememberCoroutineScope()\n",
    "    val coroutineScope = rememberCoroutineScope()\n"
    "    val categoryCatalogRepository = remember { ConnectCategoryCatalogRepository() }\n"
    "    var directoryCategories by remember {\n"
    "        mutableStateOf(ConnectCategoryCatalogRepository.defaultCategories())\n"
    "    }\n\n"
    "    LaunchedEffect(Unit) {\n"
    "        directoryCategories = categoryCatalogRepository.fetchCategories()\n"
    "    }\n",
    "catalog state",
)

text = replace_once(
    text,
    '            "Swipe between roommates, mentors, reading mates, housing and challenges.",\n',
    '            "Choose from 20 professional ways to connect. Every option opens a live Connect workflow.",\n',
    "Connect Hub subtitle",
)

text = replace_once(
    text,
    "        CategoryTabBar(\n            categories = categories,\n            badgeCounts = badgeCounts,\n",
    "        CategoryTabBar(\n            categories = categories,\n            directoryCategories = directoryCategories,\n            badgeCounts = badgeCounts,\n",
    "category directory call",
)

text = replace_once(
    text,
    "        HorizontalPager(\n            state = pagerState,\n",
    "        HorizontalPager(\n            state = pagerState,\n            userScrollEnabled = false,\n",
    "horizontal pager lock",
)

start_marker = "@Composable\nprivate fun CategoryTabBar("
end_marker = "@Composable\nprivate fun SegmentedModeSwitch("
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise RuntimeError("Could not locate CategoryTabBar block")

replacement = r'''@Composable
private fun CategoryTabBar(
    categories: List<ConnectCategory>,
    directoryCategories: List<ConnectDirectoryCategory>,
    badgeCounts: List<Int>,
    pagerState: PagerState,
    coroutineScope: CoroutineScope
) {
    val visibleDirectory = remember(directoryCategories) {
        directoryCategories.sortedBy { it.displayOrder }.take(20)
    }
    var selectedSlug by rememberSaveable(visibleDirectory) {
        mutableStateOf(visibleDirectory.firstOrNull()?.slug.orEmpty())
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visibleDirectory.forEachIndexed { directoryIndex, item ->
            val targetCategory = when (item.routeKind.lowercase()) {
                "roommate" -> ConnectCategory.ROOMMATE
                "mentor" -> ConnectCategory.MENTOR
                "reading" -> ConnectCategory.READING
                "agents" -> ConnectCategory.AGENTS
                "housing" -> ConnectCategory.HOUSING
                "challenges" -> ConnectCategory.CHALLENGES
                else -> ConnectCategory.MENTOR
            }
            val targetIndex = categories.indexOf(targetCategory).coerceAtLeast(0)
            val selected = selectedSlug == item.slug
            val pendingCount = badgeCounts.getOrElse(targetIndex) { 0 }

            val background by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                animationSpec = tween(220),
                label = "directoryRowBackground"
            )
            val foreground by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                animationSpec = tween(220),
                label = "directoryRowForeground"
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedSlug = item.slug
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetIndex)
                        }
                    },
                shape = RoundedCornerShape(18.dp),
                color = background,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = .35f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = .14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)
                        }
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                targetCategory.icon,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else foreground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(11.dp))

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${directoryIndex + 1}. ${item.title}",
                                color = foreground,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (pendingCount > 0) {
                                Spacer(Modifier.width(7.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = .14f)
                                ) {
                                    Text(
                                        text = pendingCount.toString(),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .78f)
                    ) {
                        Text(
                            text = targetCategory.shortLabel,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

'''

text = text[:start] + replacement + text[end:]
TARGET.write_text(text, encoding="utf-8")

# This is a one-shot source patch. Remove the temporary automation from the branch
# so the resulting PR contains only the production app/database changes.
for path in (WORKFLOW, SELF):
    if path.exists():
        path.unlink()
