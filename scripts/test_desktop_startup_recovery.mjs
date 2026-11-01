import fs from 'node:fs';
import assert from 'node:assert/strict';

const state=fs.readFileSync(new URL('../desktopApp/src/main/kotlin/com/blinkng/desktop/DesktopAppState.kt',import.meta.url),'utf8');
const shell=fs.readFileSync(new URL('../desktopApp/src/main/kotlin/com/blinkng/desktop/DesktopShell.kt',import.meta.url),'utf8');

// Guard the complete recovery path; runtime coverage remains in the desktop gate.
const retry=state.match(/suspend fun retryProfileLoad\(\) \{([\s\S]*?)\n    \}/)?.[1];
assert.ok(retry,'Desktop must provide a profile retry action');
for(const requirement of [
  'session == null || busy', 'errorMessage = null',
  'loadAuthenticatedState()', 'profile = null',
  'errorMessage = t.message', 'busy = false',
]) assert.ok(retry.includes(requirement),`Missing retry rule: ${requirement}`);

assert.ok(shell.includes('appState.profile == null && appState.busy -> InitializingScreen()'),'Loader may only appear while a load is pending');
assert.ok(shell.includes('appState.profile == null -> Column('),'Startup failure must render recovery');
assert.ok(shell.includes('appState.errorMessage ?: "Your profile is unavailable.'),'Failure must show actionable feedback');
assert.ok(shell.includes('Button(onClick = { startupRetrySerial++ }) { Text("Retry") }'),'Retry button must invoke load');
assert.ok(shell.includes('OutlinedButton(onClick = { appState.signOut() }) { Text("Sign out") }'),'Sign-out escape required');
assert.ok(shell.includes('if (startupRetrySerial > 0) appState.retryProfileLoad()'),'Retry event must call backend load');

console.log('Windows startup recovery source contract passed; packaged runtime testing remains required.');
