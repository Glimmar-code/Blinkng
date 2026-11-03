from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
main = ROOT / "app/src/main/java/com/example/MainActivity.kt"
s = main.read_text()

if 'import com.example.auth.AccountSessionStore' not in s:
    s = s.replace(
        'import com.example.ui.components.*\n',
        'import com.example.ui.components.*\nimport com.example.auth.AccountSessionStore\n',
        1,
    )

anchor = '''            val snackbarHostState = remember { SnackbarHostState() }\n\n            // Listen for snackbar events'''
replacement = '''            val snackbarHostState = remember { SnackbarHostState() }\n\n            // Persist the account that has actually reached the authenticated main app.\n            // Keying by destination + user id prevents repeated writes during recomposition.\n            LaunchedEffect(uiState.destination, uiState.myProfile.id) {\n                if (uiState.destination == AppDestination.MAIN && uiState.myProfile.id.isNotBlank()) {\n                    AccountSessionStore.recordCurrentSession(\n                        context = this@MainActivity,\n                        userId = uiState.myProfile.id,\n                        username = uiState.myProfile.username,\n                        fullName = uiState.myProfile.fullName,\n                        email = uiState.myProfile.email.value,\n                        avatarUrl = uiState.myProfile.avatarUrl\n                    )\n                }\n            }\n\n            // Listen for snackbar events'''

if anchor in s and 'AccountSessionStore.recordCurrentSession(' not in s:
    s = s.replace(anchor, replacement, 1)
    main.write_text(s)
else:
    print('Account recent-login hook already present or anchor not found')
