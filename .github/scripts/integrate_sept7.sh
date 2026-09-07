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
    git diff --cc -- "$file" | sed -n '1,480p' || true
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

# Already merged to main: fix/post-like-speed-reliability, game-professional-overhaul.
merge_auth
merge_admin
merge_connect
merge_one fix/professional-notifications-20260907
merge_one fix/reel-route-target-20260907
merge_one feature/professional-search-discover
merge_one leaderboard-top10-professional
merge_one fix/verified-name-consistency
merge_one feature/windows-desktop-foundation
merge_one supabase-recovery-20260907

git --no-pager log --oneline --decorate -25
