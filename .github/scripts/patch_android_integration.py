from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# Wire Switch Account into the existing menu without disturbing unrelated menu features.
menu = ROOT / "app/src/main/java/com/example/ui/components/AppMenuSheet.kt"
s = menu.read_text()
old = '''    onShowToast: (String) -> Unit,\n    onSimulateNotification: () -> Unit,'''
new = '''    onShowToast: (String) -> Unit,\n    onSimulateNotification: () -> Unit,\n    onSwitchAccount: () -> Unit = {},'''
if old in s and 'onSwitchAccount: () -> Unit' not in s:
    s = s.replace(old, new, 1)
old = '''                    onClick = {\n                        onDismiss()\n                        onShowToast("Account switch triggered")\n                    }'''
new = '''                    onClick = {\n                        onDismiss()\n                        onSwitchAccount()\n                    }'''
if old in s:
    s = s.replace(old, new, 1)
menu.write_text(s)

# Wire the switch dialog and disable the old fake notification simulation path.
activity = ROOT / "app/src/main/java/com/example/MainActivity.kt"
s = activity.read_text()
if 'import com.example.auth.AccountSessionStore' not in s:
    s = s.replace('import com.example.viewmodel.MainTab\n', 'import com.example.viewmodel.MainTab\nimport com.example.auth.AccountSessionStore\n', 1)
if 'var showAccountSwitcher by rememberSaveable' not in s:
    marker = '    var isBottomBarVisibleByScroll by rememberSaveable { mutableStateOf(true) }\n'
    s = s.replace(marker, marker + '    var showAccountSwitcher by rememberSaveable { mutableStateOf(false) }\n', 1)
old = '''                onShowToast = { viewModel.showToast(it) },\n                onSimulateNotification = { viewModel.simulateBackgroundNotification(context) }'''
new = '''                onShowToast = {\n                    if (it == "Account switch triggered") showAccountSwitcher = true\n                    else viewModel.showToast(it)\n                },\n                onSimulateNotification = { viewModel.showToast("Test notifications are disabled. Real notifications are now delivered from Supabase events.") },\n                onSwitchAccount = { showAccountSwitcher = true }'''
if old in s:
    s = s.replace(old, new, 1)
marker = '''        // Modals: Interactive Fullscreen Story Viewer\n'''
insert = '''        if (showAccountSwitcher) {\n            AccountSwitcherDialog(\n                accounts = AccountSessionStore.list(this@MainActivity),\n                onDismiss = { showAccountSwitcher = false },\n                onSelect = { account ->\n                    AccountSessionStore.switchTo(this@MainActivity, account)\n                    showAccountSwitcher = false\n                    recreate()\n                },\n                onAddAccount = {\n                    showAccountSwitcher = false\n                    viewModel.logout()\n                }\n            )\n        }\n\n'''
if marker in s and 'AccountSwitcherDialog(' not in s:
    s = s.replace(marker, insert + marker, 1)
activity.write_text(s)

# Remove the fake test notification generator from the ViewModel so it cannot be invoked accidentally.
vm = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
s = vm.read_text()
start = s.find('    // ============================================================\n    // TEST NOTIFICATION\n    // ============================================================\n')
end = s.find('    // ============================================================\n    // STORY INTERACTIONS & SUPABASE PERSISTENCE\n', start)
if start >= 0 and end > start:
    s = s[:start] + s[end:]
vm.write_text(s)
