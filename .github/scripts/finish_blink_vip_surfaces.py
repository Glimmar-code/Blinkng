from pathlib import Path


def patch(path: str, old: str, new: str, count: int = 1) -> None:
    p = Path(path)
    text = p.read_text()
    found = text.count(old)
    if found < count:
        raise RuntimeError(f"{path}: expected at least {count} occurrence(s), found {found}: {old[:90]!r}")
    text = text.replace(old, new, count)
    p.write_text(text)


def patch_all(path: str, old: str, new: str, minimum: int = 1) -> None:
    p = Path(path)
    text = p.read_text()
    found = text.count(old)
    if found < minimum:
        raise RuntimeError(f"{path}: expected at least {minimum} occurrence(s), found {found}: {old[:90]!r}")
    p.write_text(text.replace(old, new))


# Feed / Reels: the same PostCard header is used by both content families.
patch(
    "app/src/main/java/com/example/ui/components/PostCard.kt",
    """                        if (!isAuthor && authorProfileId.isNotBlank()) {""",
    """                        BlinkVipMarkForUsername(
                            username = resolvedAuthorUsername,
                            knownVip = if (post.authorIsVip) true else null,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        if (!isAuthor && authorProfileId.isNotBlank()) {"""
)

# Comments + replies.
patch(
    "app/src/main/java/com/example/ui/components/CommentSheet.kt",
    """                                    verificationBadge = comment.verificationBadge,
                                    avatarSize = 38.dp,""",
    """                                    verificationBadge = comment.verificationBadge,
                                    isVip = comment.isVip,
                                    avatarSize = 38.dp,"""
)
patch(
    "app/src/main/java/com/example/ui/components/CommentSheet.kt",
    """                                        verificationBadge = reply.verificationBadge,
                                        avatarSize = 30.dp,""",
    """                                        verificationBadge = reply.verificationBadge,
                                        isVip = reply.isVip,
                                        avatarSize = 30.dp,"""
)
patch(
    "app/src/main/java/com/example/ui/components/CommentSheet.kt",
    """    verificationBadge: VerificationBadge,
    avatarSize: Dp,""",
    """    verificationBadge: VerificationBadge,
    isVip: Boolean,
    avatarSize: Dp,"""
)
patch(
    "app/src/main/java/com/example/ui/components/CommentSheet.kt",
    """                if (verificationBadge != VerificationBadge.NONE) {
                    VerifiedMark(badge = verificationBadge, size = 12.dp)
                }
                if (username.isNotBlank()) {""",
    """                if (verificationBadge != VerificationBadge.NONE) {
                    VerifiedMark(badge = verificationBadge, size = 12.dp)
                }
                BlinkVipMarkForUsername(
                    username = username,
                    knownVip = if (isVip) true else null
                )
                if (username.isNotBlank()) {"""
)

# Profile identity.
patch(
    "app/src/main/java/com/example/ui/screens/ProfileScreen.kt",
    """import com.example.ui.components.VerifiedMark""",
    """import com.example.ui.components.VerifiedMark
import com.example.ui.components.BlinkVipMarkForUsername"""
)
patch(
    "app/src/main/java/com/example/ui/screens/ProfileScreen.kt",
    """                                if (profile.verificationBadge != VerificationBadge.NONE) {
                                    Spacer(modifier = Modifier.width(7.dp))
                                    VerifiedMark(badge = profile.verificationBadge, size = 20.dp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))""",
    """                                if (profile.verificationBadge != VerificationBadge.NONE) {
                                    Spacer(modifier = Modifier.width(7.dp))
                                    VerifiedMark(badge = profile.verificationBadge, size = 20.dp)
                                }
                                BlinkVipMarkForUsername(
                                    username = profile.username,
                                    knownVip = if (profile.isBlinkVip) true else null,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))"""
)

# Notifications. Server priority is used immediately when present; cached lookup covers older activity payloads.
patch(
    "app/src/main/java/com/example/ui/screens/ActivityScreen.kt",
    """import com.example.ui.components.VerifiedMark""",
    """import com.example.ui.components.VerifiedMark
import com.example.ui.components.BlinkVipMarkForUsername"""
)
patch(
    "app/src/main/java/com/example/ui/screens/ActivityScreen.kt",
    """    val accent = when (category) {
        NotificationFilter.LIKES -> BlinkPink
        NotificationFilter.COMMENTS -> BlinkPurple
        NotificationFilter.MARKET -> Color(0xFF22C55E)
        NotificationFilter.ALL -> BlinkPink
    }""",
    """    val accent = if (item.vipPriority) {
        Color(0xFFF59E0B)
    } else when (category) {
        NotificationFilter.LIKES -> BlinkPink
        NotificationFilter.COMMENTS -> BlinkPurple
        NotificationFilter.MARKET -> Color(0xFF22C55E)
        NotificationFilter.ALL -> BlinkPink
    }"""
)
patch(
    "app/src/main/java/com/example/ui/screens/ActivityScreen.kt",
    """                    if (verificationBadge != VerificationBadge.NONE) {
                        Spacer(Modifier.width(4.dp))
                        VerifiedMark(verificationBadge, size = 12.dp)
                    }
                }""",
    """                    if (verificationBadge != VerificationBadge.NONE) {
                        Spacer(Modifier.width(4.dp))
                        VerifiedMark(verificationBadge, size = 12.dp)
                    }
                    BlinkVipMarkForUsername(
                        username = username,
                        knownVip = if (item.actorIsVip || profile?.isBlinkVip == true) true else null,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }"""
)

# Message list + active chat header.
patch(
    "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt",
    """import com.example.ui.theme.BlinkMessageTheme""",
    """import com.example.ui.components.BlinkVipMarkForUsername
import com.example.ui.theme.BlinkMessageTheme"""
)
patch_all(
    "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt",
    """                    VerificationDot(conversation.verificationBadge, conversation.isVerified)""",
    """                    VerificationDot(conversation.verificationBadge, conversation.isVerified)
                    BlinkVipMarkForUsername(
                        username = conversation.partnerUsername,
                        knownVip = if (conversation.partnerIsVip) true else null,
                        modifier = Modifier.padding(start = 4.dp)
                    )""",
    minimum=2
)

# Marketplace seller rows.
patch(
    "app/src/main/java/com/example/ui/screens/MarketScreen.kt",
    """import com.example.ui.components.VerifiedMark""",
    """import com.example.ui.components.VerifiedMark
import com.example.ui.components.BlinkVipMarkForUsername"""
)
patch(
    "app/src/main/java/com/example/ui/screens/MarketScreen.kt",
    """                    if (item.verificationBadge != VerificationBadge.NONE) {
                        VerifiedMark(badge = item.verificationBadge, size = 12.dp)
                    } else if (item.sellerIsVerified) {
                        VerifiedMark(badge = VerificationBadge.BLUE, size = 12.dp)
                    }
                }""",
    """                    if (item.verificationBadge != VerificationBadge.NONE) {
                        VerifiedMark(badge = item.verificationBadge, size = 12.dp)
                    } else if (item.sellerIsVerified) {
                        VerifiedMark(badge = VerificationBadge.BLUE, size = 12.dp)
                    }
                    BlinkVipMarkForUsername(
                        username = item.sellerUsername,
                        knownVip = if (item.sellerIsVip) true else null,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }"""
)

# Leaderboard rows.
patch(
    "app/src/main/java/com/example/ui/screens/LeaderboardScreen.kt",
    """import com.example.ui.theme.BlinkGold""",
    """import com.example.ui.components.BlinkVipMarkForUsername
import com.example.ui.theme.BlinkGold"""
)
patch(
    "app/src/main/java/com/example/ui/screens/LeaderboardScreen.kt",
    """                                if (user.verificationBadge != VerificationBadge.NONE) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Verified,
                                        null,
                                        tint = BlinkPink,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }""",
    """                                if (user.verificationBadge != VerificationBadge.NONE) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Verified,
                                        null,
                                        tint = BlinkPink,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                BlinkVipMarkForUsername(
                                    username = user.username,
                                    knownVip = if (user.isVip) true else null,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }"""
)

# Story rail labels. Same-package shared badge requires no import.
patch(
    "app/src/main/java/com/example/ui/components/StoryBar.kt",
    """        Text(
            text = story.username,
            fontSize = 10.5.sp,""",
    """        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = story.username,
                fontSize = 10.5.sp,"""
)
patch(
    "app/src/main/java/com/example/ui/components/StoryBar.kt",
    """            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = story.faculty,""",
    """            overflow = TextOverflow.Ellipsis
            )
            BlinkVipMarkForUsername(
                username = story.username,
                knownVip = if (story.isVip) true else null,
                modifier = Modifier.padding(start = 3.dp)
            )
        }

        Text(
            text = story.faculty,"""
)

print("Blink VIP identity surfaces patched successfully.")
