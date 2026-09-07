#!/usr/bin/env bash
set -euo pipefail

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git fetch origin '+refs/heads/*:refs/remotes/origin/*'
git checkout -B integration-sept7 origin/main

report_conflict() {
  local branch="$1"
  echo "::error::Conflict while merging $branch"
  git diff --name-only --diff-filter=U || true
  git status --short || true
  while IFS= read -r file; do
    [ -n "$file" ] || continue
    echo "----- $file -----"
    git diff --cc -- "$file" | sed -n '1,600p' || true
  done < <(git diff --name-only --diff-filter=U)
}

merge_one() {
  local branch="$1"
  echo "===== MERGE $branch ====="
  if git merge-base --is-ancestor "origin/$branch" HEAD; then return 0; fi
  if git merge --no-ff --no-edit "origin/$branch"; then return 0; fi
  report_conflict "$branch"; exit 10
}

merge_auth() {
  local branch="fix/auth-google-reset-account-switch"
  echo "===== MERGE $branch ====="
  if git merge --no-ff --no-edit "origin/$branch"; then return 0; fi
  mapfile -t c < <(git diff --name-only --diff-filter=U)
  expected=("app/src/main/AndroidManifest.xml" "app/src/main/java/com/example/auth/GoogleAuthCallbackActivity.kt" "app/src/main/java/com/example/auth/GoogleNonce.kt")
  [ "${#c[@]}" -eq 3 ] || { report_conflict "$branch"; exit 10; }
  for f in "${expected[@]}"; do printf '%s\n' "${c[@]}" | grep -Fxq "$f" || { report_conflict "$branch"; exit 10; }; done
  git checkout --ours -- app/src/main/AndroidManifest.xml app/src/main/java/com/example/auth/GoogleAuthCallbackActivity.kt app/src/main/java/com/example/auth/GoogleNonce.kt
  python - <<'PY'
from pathlib import Path
p=Path('app/src/main/AndroidManifest.xml'); s=p.read_text()
if '.auth.PasswordResetActivity' not in s:
    block='''        <activity
            android:name=".auth.PasswordResetActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:theme="@style/Theme.MyApplication">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="blink" android:host="auth" android:path="/reset-password" />
            </intent-filter>
        </activity>
'''
    anchor='        <service\n            android:name=".notification.BlinkFirebaseMessagingService"'
    if anchor not in s: raise SystemExit('Firebase service anchor missing')
    p.write_text(s.replace(anchor,block+anchor,1))
PY
  git add app/src/main/AndroidManifest.xml app/src/main/java/com/example/auth/GoogleAuthCallbackActivity.kt app/src/main/java/com/example/auth/GoogleNonce.kt
  git commit --no-edit
}

merge_admin() {
  local branch="feature/admin-control-center-v3"
  local file="app/src/main/java/com/example/AdminControlCenterActivity.kt"
  echo "===== MERGE $branch ====="
  if git merge --no-ff --no-edit "origin/$branch"; then return 0; fi
  mapfile -t c < <(git diff --name-only --diff-filter=U)
  [ "${#c[@]}" -eq 1 ] && [ "${c[0]}" = "$file" ] || { report_conflict "$branch"; exit 10; }
  git checkout --theirs -- "$file"
  git add "$file"
  git commit --no-edit
}

merge_connect() {
  local branch="feature/connect-vertical-directory-20"
  local file="app/src/main/java/com/example/ui/screens/ConnectHubPremiumPanel.kt"
  echo "===== MERGE $branch ====="
  if git merge --no-ff --no-edit "origin/$branch"; then return 0; fi
  mapfile -t c < <(git diff --name-only --diff-filter=U)
  [ "${#c[@]}" -eq 1 ] && [ "${c[0]}" = "$file" ] || { report_conflict "$branch"; exit 10; }
  git checkout --ours -- "$file"
  python - <<'PY'
from pathlib import Path
import re, subprocess
path=Path('app/src/main/java/com/example/ui/screens/ConnectHubPremiumPanel.kt')
s=path.read_text()
theirs=subprocess.check_output(['git','show','origin/feature/connect-vertical-directory-20:app/src/main/java/com/example/ui/screens/ConnectHubPremiumPanel.kt'], text=True)

def add_after(text, anchor, addition):
    if addition.strip() in text: return text
    if anchor not in text: raise SystemExit(f'Connect anchor missing: {anchor!r}')
    return text.replace(anchor, anchor+addition, 1)

s=add_after(s,'import androidx.compose.runtime.Composable\n','import androidx.compose.runtime.LaunchedEffect\n')
s=add_after(s,'import com.example.data.repository.ConnectHubRepository\n','import com.example.data.repository.ConnectCategoryCatalogRepository\nimport com.example.data.repository.ConnectDirectoryCategory\n')
if 'val categoryCatalogRepository' not in s:
    anchor='    val matchRepository = remember { ConnectHubRepository() }\n'
    state='''    val categoryCatalogRepository = remember { ConnectCategoryCatalogRepository() }
    var directoryCategories by remember { mutableStateOf(ConnectCategoryCatalogRepository.defaultCategories()) }

    LaunchedEffect(Unit) {
        directoryCategories = categoryCatalogRepository.fetchCategories()
    }
'''
    if anchor not in s: raise SystemExit('matchRepository anchor missing')
    s=s.replace(anchor,anchor+state,1)
s=s.replace('"Swipe between roommates, mentors, reading mates, housing and challenges."','"Choose from 20 professional ways to connect. Every option opens a live Connect workflow."',1)
if 'directoryCategories = directoryCategories' not in s:
    s,n=re.subn(r'(CategoryTabBar\(\s*\n\s*categories\s*=\s*categories,\s*\n)(\s*badgeCounts\s*=)',r'\1            directoryCategories = directoryCategories,\n\2',s,count=1)
    if n != 1: raise SystemExit('CategoryTabBar call anchor missing')
if 'userScrollEnabled = false' not in s:
    s,n=re.subn(r'(HorizontalPager\(\s*\n\s*state\s*=\s*pagerState,\s*\n)(\s*modifier\s*=)',r'\1            userScrollEnabled = false,\n\2',s,count=1)
    if n != 1: raise SystemExit('HorizontalPager anchor missing')
marker='@Composable\nprivate fun CategoryTabBar('
def function_span(text):
    start=text.index(marker); brace=text.index('{',start); depth=0
    for i in range(brace,len(text)):
        if text[i]=='{': depth+=1
        elif text[i]=='}':
            depth-=1
            if depth==0: return start,i+1
    raise SystemExit('Unclosed CategoryTabBar')
a,b=function_span(s); ta,tb=function_span(theirs)
s=s[:a]+theirs[ta:tb]+s[b:]
path.write_text(s)
PY
  git add "$file"
  git commit --no-edit
}

merge_notifications() {
  local branch="fix/professional-notifications-20260907"
  local file="app/src/main/java/com/example/ui/screens/ActivityScreen.kt"
  echo "===== MERGE $branch ====="
  if git merge --no-ff --no-edit "origin/$branch"; then return 0; fi
  mapfile -t c < <(git diff --name-only --diff-filter=U)
  [ "${#c[@]}" -eq 1 ] && [ "${c[0]}" = "$file" ] || { report_conflict "$branch"; exit 10; }
  python - <<'PY'
from pathlib import Path
import re
p=Path('app/src/main/java/com/example/ui/screens/ActivityScreen.kt')
s=p.read_text()
if s.count('<<<<<<< HEAD') != 2: raise SystemExit('Unexpected notification conflicts')
pattern=r'''<<<<<<< HEAD\n\s*val accent = if \(item\.vipPriority\) \{\n\s*Color\(0xFFF59E0B\)\n\s*\} else when \(category\) \{\n\s*NotificationFilter\.LIKES -> BlinkPink\n\s*NotificationFilter\.COMMENTS -> BlinkPurple\n\s*NotificationFilter\.MARKET -> Color\(0xFF22C55E\)\n\s*NotificationFilter\.ALL -> BlinkPink\n\s*\}\n=======\n\s*val isOfficial = item\.targetType\.equals\("notification", ignoreCase = true\)\n\s*val accent = notificationAccent\(category, isOfficial\)\n>>>>>>> origin/fix/professional-notifications-20260907'''
repl='''    val isOfficial = item.targetType.equals("notification", ignoreCase = true)
    val accent = if (item.vipPriority) Color(0xFFF59E0B) else notificationAccent(category, isOfficial)'''
s,n=re.subn(pattern,repl,s,count=1)
if n != 1: raise SystemExit('Could not resolve notification accent')
start=s.index('<<<<<<< HEAD'); marker='>>>>>>> origin/fix/professional-notifications-20260907'; end=s.index(marker,start)+len(marker)
block=s[start:end]
ours=block.split('=======',1)[0].split('<<<<<<< HEAD\n',1)[1]
theirs=block.split('=======\n',1)[1].rsplit('\n'+marker,1)[0]
s=s[:start]+ours+theirs+'\n'+s[end:]
if any(x in s for x in ('<<<<<<<','>>>>>>>','=======')): raise SystemExit('Notification markers remain')
p.write_text(s)
PY
  git add "$file"
  git commit --no-edit
}

merge_search() {
  local branch="feature/professional-search-discover"
  local file="app/src/main/java/com/example/ui/screens/SearchScreen.kt"
  echo "===== MERGE $branch ====="
  if git merge --no-ff --no-edit "origin/$branch"; then return 0; fi
  mapfile -t c < <(git diff --name-only --diff-filter=U)
  [ "${#c[@]}" -eq 1 ] && [ "${c[0]}" = "$file" ] || { report_conflict "$branch"; exit 10; }
  git checkout --theirs -- "$file"
  git add "$file"
  git commit --no-edit
}

merge_leaderboard() {
  local branch="leaderboard-top10-professional"
  local file="app/src/main/java/com/example/ui/screens/LeaderboardScreen.kt"
  echo "===== MERGE $branch ====="
  if git merge --no-ff --no-edit "origin/$branch"; then return 0; fi
  mapfile -t c < <(git diff --name-only --diff-filter=U)
  [ "${#c[@]}" -eq 1 ] && [ "${c[0]}" = "$file" ] || { report_conflict "$branch"; exit 10; }
  git checkout --theirs -- "$file"
  python - <<'PY'
from pathlib import Path
p=Path('app/src/main/java/com/example/ui/screens/LeaderboardScreen.kt'); s=p.read_text()
anchor='import com.example.data.models.VerificationBadge\n'; addition='import com.example.ui.components.BlinkVipMarkForUsername\nimport com.example.ui.components.VerifiedMark\n'
if 'import com.example.ui.components.VerifiedMark' not in s:
    if anchor not in s: raise SystemExit('Leaderboard import anchor missing')
    s=s.replace(anchor,anchor+addition,1)
old1='''                                if (user.verificationBadge != VerificationBadge.NONE) {
                                    Spacer(Modifier.width(3.dp))
                                    Icon(
                                        Icons.Default.Verified,
                                        null,
                                        tint = BlinkPink,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
'''
new1='''                                if (user.verificationBadge != VerificationBadge.NONE) {
                                    Spacer(Modifier.width(3.dp))
                                    VerifiedMark(user.verificationBadge, size = 13.dp)
                                }
                                BlinkVipMarkForUsername(
                                    username = user.username,
                                    knownVip = if (user.isVip) true else null,
                                    modifier = Modifier.padding(start = 3.dp)
                                )
'''
old2='''                    if (user.verificationBadge != VerificationBadge.NONE) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            null,
                            tint = BlinkPink,
                            modifier = Modifier.size(14.dp)
                        )
                    }
'''
new2='''                    if (user.verificationBadge != VerificationBadge.NONE) {
                        Spacer(Modifier.width(4.dp))
                        VerifiedMark(user.verificationBadge, size = 14.dp)
                    }
                    BlinkVipMarkForUsername(
                        username = user.username,
                        knownVip = if (user.isVip) true else null,
                        modifier = Modifier.padding(start = 4.dp)
                    )
'''
if old1 not in s or old2 not in s: raise SystemExit('Leaderboard identity anchors missing')
s=s.replace(old1,new1,1).replace(old2,new2,1)
p.write_text(s)
PY
  git add "$file"
  git commit --no-edit
}

merge_verified() {
  local branch="fix/verified-name-consistency"
  local leaderboard="app/src/main/java/com/example/ui/screens/LeaderboardScreen.kt"
  local search="app/src/main/java/com/example/ui/screens/SearchScreen.kt"
  local professional="app/src/main/java/com/example/ui/screens/ProfessionalSearchScreen.kt"
  echo "===== MERGE $branch ====="
  if git merge --no-ff --no-edit "origin/$branch"; then return 0; fi
  mapfile -t c < <(git diff --name-only --diff-filter=U)
  [ "${#c[@]}" -eq 2 ] || { report_conflict "$branch"; exit 10; }
  printf '%s\n' "${c[@]}" | grep -Fxq "$leaderboard" || { report_conflict "$branch"; exit 10; }
  printf '%s\n' "${c[@]}" | grep -Fxq "$search" || { report_conflict "$branch"; exit 10; }
  # Keep the newer professional Search wrapper and Top-10 leaderboard. Their verified
  # name behavior supersedes the older copies from this branch. Reel changes auto-merge.
  git checkout --ours -- "$leaderboard" "$search"
  python - <<'PY'
from pathlib import Path
p=Path('app/src/main/java/com/example/ui/screens/ProfessionalSearchScreen.kt'); s=p.read_text()
anchor='import com.example.ui.components.PostCard\n'
if 'import com.example.ui.components.VerifiedMark' not in s:
    if anchor not in s: raise SystemExit('Professional Search component import anchor missing')
    s=s.replace(anchor,anchor+'import com.example.ui.components.VerifiedMark\n',1)
old='''        if (person.verificationBadge != VerificationBadge.NONE) {
            Spacer(Modifier.width(3.dp))
            Icon(
                Icons.Default.Verified,
                contentDescription = when (person.verificationBadge) {
                    VerificationBadge.GOLD -> "Gold verified account"
                    VerificationBadge.BLUE -> "Verified account"
                    VerificationBadge.NONE -> null
                },
                tint = BlinkPink,
                modifier = Modifier.size((fontSize + 2).dp)
            )
        }
'''
new='''        if (person.verificationBadge != VerificationBadge.NONE) {
            Spacer(Modifier.width(3.dp))
            VerifiedMark(person.verificationBadge, size = (fontSize + 2).dp)
        }
'''
if old not in s: raise SystemExit('Professional Search verified-name anchor missing')
s=s.replace(old,new,1)
p.write_text(s)
PY
  git add "$leaderboard" "$search" "$professional"
  git commit --no-edit
}

# Already merged to main: fix/post-like-speed-reliability, game-professional-overhaul.
merge_auth
merge_admin
merge_connect
merge_notifications
merge_one fix/reel-route-target-20260907
merge_search
merge_leaderboard
merge_verified
merge_one feature/windows-desktop-foundation
merge_one supabase-recovery-20260907

git --no-pager log --oneline --decorate -25
