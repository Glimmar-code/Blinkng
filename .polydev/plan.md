# UI Review Plan for Blinkng Android (and Desktop) App

## Goal
Systematically scan every composable UI screen in the Android (`app/`) and Desktop (`desktopApp/`) codebases, identify layout mis‑alignments, hard‑coded dimensions, missing accessibility attributes, and any other UI‑related issues, then propose or apply concrete improvements.

## Steps

1. **Gather UI Files**
   - List all Kotlin files under `app/src/main/java/com/example/ui/screens/` and `app/src/main/java/com/example/ui/components/`.
   - List all Desktop UI files under `desktopApp/src/main/kotlin/com/blinkng/desktop/ui/`.

2. **Static Analysis for Red‑Flags**
   - For each file, run `search_grep` for patterns that often indicate UI problems:
     - Fixed `dp` sizes (`width = 200.dp`, `height = 48.dp`).
     - Missing `Modifier.fillMaxWidth()` or `fillMaxSize()` on top‑level containers.
     - Nested scrolling containers (`LazyColumn` inside another scroll).
     - Hard‑coded `Spacer` sizes that could be replaced with `Modifier.weight`.
     - Missing `contentDescription` on images.
     - Use of `AbsolutePadding` instead of `Modifier.padding`.
   - Record any matches with file path and line numbers.

3. **Collect Accessibility Checks**
   - Search for `AsyncImage` or `Image` composables lacking a `contentDescription` argument.
   - Search for `Clickable` elements without an accessible label.

4. **Generate Report**
   - Summarise findings per screen/component.
   - Highlight the most critical mis‑alignments (e.g., overlapping UI, truncated text, non‑responsive sizing).

5. **Propose Fixes**
   - For each issue, suggest a concrete code change (e.g., replace `Modifier.width(200.dp)` with `Modifier.fillMaxWidth()` or use `Modifier.weight(1f)`).
   - Where possible, apply the fix directly in the code, respecting the shared‑first rule (move reusable changes to `shared/` if appropriate).

6. **Parity for Desktop**
   - Mirror any Android UI changes that affect shared logic to the Desktop UI files.
   - If a change is Android‑specific, note it in `platform-parity/changes.md`.

7. **Testing**
   - Run the Android dev server (`npm run dev` or appropriate Gradle task) and use `check_preview` to ensure no new UI crashes.
   - For Desktop, start the desktop app and verify the UI renders without errors.

8. **Documentation**
   - Update `platform-parity/changes.md` with any platform‑specific deviations.
   - Add comments in the code where changes were made.

## Acceptance
- I will provide a concise report of all identified UI issues and the corresponding code modifications.
- If you want me to apply the fixes automatically, confirm and I will proceed.

Please approve this plan or let me know if you’d like any adjustments.