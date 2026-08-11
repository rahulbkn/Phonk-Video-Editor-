# BUG_TRACKER.md — PHONK VIDEO EDITOR

This file tracks verified bugs and their fix status.

RULES:
- Do not add a bug without evidence.
- Do not mark a bug FIXED without verification.
- Do not delete historical bugs; move them to FIXED/VERIFIED.
- Consolidate duplicate findings under one BUG-ID.

STATUS:
OPEN / INVESTATING / FIXING / READY_FOR_TEST / FIXED / VERIFIED / WONT_FIX / DUPLICATE

SEVERITY:
CRITICAL / HIGH / MEDIUM / LOW

BUG FORMAT

BUG-ID:
CATEGORY:
SEVERITY:
STATUS:

TITLE:

SYMPTOM:

REPRODUCTION:
1.
2.
3.

EXPECTED:

ACTUAL:

ROOT CAUSE:

AFFECTED FEATURE:

FILES:

LINE/RANGE:

PREVIEW IMPACT:

EXPORT IMPACT:

PERSISTENCE IMPACT:

UNDO/REDO IMPACT:

RECOMMENDED FIX:

REGRESSION TEST:

DEVICE TEST:

OWNER:

COMMIT:

NOTES:

============================================================
OPEN BUGS
============================================================

No open bugs. All known issues have been fixed and verified.

============================================================
VERIFIED FIXES (this session)
============================================================

BUG-ID: HOME-001
CATEGORY: Navigation / Dead Button
SEVERITY: HIGH
STATUS: VERIFIED

TITLE: Export Video quick action button does nothing

SYMPTOM: Tapping "Export Video" quick action on Home screen had no effect.

REPRODUCTION:
1. Launch app
2. On Home screen, tap "Export Video" in Quick Actions
3. Nothing happens

EXPECTED: Should open the most recent project in editor for export
ACTUAL: Empty lambda `{ }` — no implementation

ROOT CAUSE: QuickActionsCard was called with empty lambdas for onExportVideo, onBeatAnalyzer, onHelp.

AFFECTED FEATURE: Home Screen Quick Actions

FILES: app/src/main/java/dev/phonk/editor/ui/HomeScreen.kt

LINE/RANGE: Lines 212-214

PREVIEW IMPACT: N/A
EXPORT IMPACT: N/A
PERSISTENCE IMPACT: N/A
UNDO/REDO IMPACT: N/A

RECOMMENDED FIX: Wire onExportVideo to open most recent project in editor.

REGRESSION TEST: Tap Export Video → editor opens with recent project

DEVICE TEST: Verified — opens editor or shows toast if no projects

OWNER: integration

COMMIT: Pending

NOTES: Fixed by wiring all three quick actions to real functionality.

---

BUG-ID: HOME-002
CATEGORY: Navigation / Dead Button
SEVERITY: HIGH
STATUS: VERIFIED

TITLE: Beat Analyzer quick action button does nothing

SYMPTOM: Tapping "Beat Analyzer" quick action on Home screen had no effect.

REPRODUCTION:
1. Launch app
2. On Home screen, tap "Beat Analyzer" in Quick Actions
3. Nothing happens

EXPECTED: Should navigate to beat analysis screen
ACTUAL: Empty lambda `{ }` — no implementation

ROOT CAUSE: QuickActionsCard was called with empty lambda for onBeatAnalyzer.

AFFECTED FEATURE: Home Screen Quick Actions

FILES: app/src/main/java/dev/phonk/editor/ui/HomeScreen.kt

LINE/RANGE: Line 213

PREVIEW IMPACT: N/A
EXPORT IMPACT: N/A
PERSISTENCE IMPACT: N/A
UNDO/REDO IMPACT: N/A

RECOMMENDED FIX: Create BeatAnalyzerScreen and wire navigation to it.

REGRESSION TEST: Tap Beat Analyzer → BeatAnalyzerScreen opens with project list

DEVICE TEST: Verified — screen opens, shows project list

OWNER: integration

COMMIT: Pending

NOTES: Fixed by creating BeatAnalyzerScreen.kt and wiring navigation.

---

BUG-ID: HOME-003
CATEGORY: Navigation / Dead Button
SEVERITY: MEDIUM
STATUS: VERIFIED

TITLE: Help quick action button does nothing

SYMPTOM: Tapping "Help" quick action on Home screen had no effect.

REPRODUCTION:
1. Launch app
2. On Home screen, tap "Help" in Quick Actions
3. Nothing happens

EXPECTED: Should show help dialog with usage instructions
ACTUAL: Empty lambda `{ }` — no implementation

ROOT CAUSE: QuickActionsCard was called with empty lambda for onHelp.

AFFECTED FEATURE: Home Screen Quick Actions

FILES: app/src/main/java/dev/phonk/editor/ui/HomeScreen.kt

LINE/RANGE: Line 214

PREVIEW IMPACT: N/A
EXPORT IMPACT: N/A
PERSISTENCE IMPACT: N/A
UNDO/REDO IMPACT: N/A

RECOMMENDED FIX: Create HelpDialog and wire showHelpDialog state.

REGRESSION TEST: Tap Help → HelpDialog appears with usage instructions

DEVICE TEST: Verified — dialog shows with correct content

OWNER: integration

COMMIT: Pending

NOTES: Fixed by creating HelpDialog.kt and wiring state.

---

BUG-ID: HOME-004
CATEGORY: Project Management
SEVERITY: HIGH
STATUS: VERIFIED

TITLE: Rename/Duplicate/Share project menu items do nothing

SYMPTOM: Tapping Rename, Duplicate, or Share in project menu only closes the menu.

REPRODUCTION:
1. Launch app
2. Open Home screen with at least one project
3. Tap 3-dot menu on a project card
4. Tap Rename, Duplicate, or Share
5. Menu closes but nothing else happens

EXPECTED: Rename shows dialog, Duplicate creates copy, Share launches share chooser
ACTUAL: Only `menuExpanded = false` — no implementation

ROOT CAUSE: DropdownMenuItem onClick handlers only closed the menu without calling any logic.

AFFECTED FEATURE: Project management

FILES: app/src/main/java/dev/phonk/editor/ui/HomeScreen.kt

LINE/RANGE: Lines 694-748

PREVIEW IMPACT: N/A
EXPORT IMPACT: N/A
PERSISTENCE IMPACT: N/A
UNDO/REDO IMPACT: N/A

RECOMMENDED FIX: Implement all three operations with proper state management.

REGRESSION TEST:
- Rename → dialog → confirm → project name changes
- Duplicate → new project appears with "(copy)" suffix
- Share → Android share chooser appears

DEVICE TEST: Verified — all three work correctly

OWNER: integration

COMMIT: Pending

NOTES: Fixed by adding local functions and wiring to dropdown items.

---

BUG-ID: NAV-001
CATEGORY: Navigation
SEVERITY: CRITICAL
STATUS: VERIFIED

TITLE: System Back button closes app instead of navigating to previous screen

SYMPTOM: Pressing Android system Back from any screen closes the entire app.

REPRODUCTION:
1. Launch app (Home)
2. Navigate to Settings
3. Press system Back
4. App closes instead of returning to Home

EXPECTED: Back should navigate to previous screen in stack
ACTUAL: Activity finishes, app closes

ROOT CAUSE: No BackHandler at top level; NavigationViewModel.goBack() always went to Home instead of maintaining back stack.

AFFECTED FEATURE: Navigation

FILES: app/src/main/java/dev/phonk/editor/ui/MainActivity.kt

LINE/RANGE: Lines 29-47 (old NavigationViewModel)

PREVIEW IMPACT: N/A
EXPORT IMPACT: N/A
PERSISTENCE IMPACT: N/A
UNDO/REDO IMPACT: N/A

RECOMMENDED FIX: Replace goBack() with proper back stack (MutableList<Route>) and add BackHandler.

REGRESSION TEST:
- Home → Settings → Back → Home
- Home → Settings → Debug → Back → Settings → Back → Home
- Home → Back → app closes (root)

DEVICE TEST: Verified — all navigation flows work correctly

OWNER: integration

COMMIT: Pending

NOTES: Fixed by rewriting NavigationViewModel with MutableList back stack and pop() method.

---

BUG-ID: NAV-002
CATEGORY: Navigation / Placeholder
SEVERITY: HIGH
STATUS: VERIFIED

TITLE: Beats and Profile tabs show placeholder screen

SYMPTOM: Tapping Beats or Profile bottom nav tabs shows centered text only.

REPRODUCTION:
1. Launch app
2. Tap "Beats" tab in bottom navigation
3. See only "Beats" text centered on screen
4. Same for "Profile" tab

EXPECTED: Real functionality for both tabs
ACTUAL: PlaceholderScreen composable — just shows title text

ROOT CAUSE: MainActivity routed Beats/Profile to PlaceholderScreen stub.

AFFECTED FEATURE: Bottom navigation

FILES: app/src/main/java/dev/phonk/editor/ui/MainActivity.kt

LINE/RANGE: Lines 108-115 (old)

PREVIEW IMPACT: N/A
EXPORT IMPACT: N/A
PERSISTENCE IMPACT: N/A
UNDO/REDO IMPACT: N/A

RECOMMENDED FIX: Create real BeatAnalyzerScreen and ProfileScreen, wire to routes.

REGRESSION TEST:
- Beats tab → project list → select → analysis runs → results shown
- Profile tab → app info displayed with stats

DEVICE TEST: Verified — both screens functional

OWNER: integration

COMMIT: Pending

NOTES: Fixed by creating BeatAnalyzerScreen.kt and ProfileScreen.kt.

---

BUG-ID: UI-001
CATEGORY: UI Duplication
SEVERITY: MEDIUM
STATUS: VERIFIED

TITLE: Duplicate controls across screens

SYMPTOM: Aspect ratio, playback controls, project name appeared in both EditorScreen and EditorPreview. Effects appeared in multiple categories. AdjustPanel duplicated FiltersPanel.

REPRODUCTION:
1. Open editor
2. See aspect ratio chips in both header bar AND preview overlay
3. See play/pause in both screen-level controls AND preview overlay
4. Open Effects → BLUR/GLITCH/FLASH appear in multiple categories
5. Open Adjust → same sliders as Filters

EXPECTED: Each control in ONE clear location
ACTUAL: Duplicated controls causing confusion

ROOT CAUSE: EditorPreview had its own controls that duplicated EditorScreen-level controls.

AFFECTED FEATURE: Editor UI

FILES:
- app/src/main/java/dev/phonk/editor/ui/EditorScreen.kt
- app/src/main/java/dev/phonk/editor/ui/editor/EditorPreview.kt
- app/src/main/java/dev/phonk/editor/ui/editor/panels/ToolPanels.kt

LINE/RANGE: Multiple

PREVIEW IMPACT: N/A
EXPORT IMPACT: N/A
PERSISTENCE IMPACT: N/A
UNDO/REDO IMPACT: N/A

RECOMMENDED FIX: Remove duplicates, consolidate to single entry points.

REGRESSION TEST: Each control appears exactly once in the UI.

DEVICE TEST: Verified — no duplicate controls remain.

OWNER: integration

COMMIT: Pending

NOTES: Removed aspect/playback/project-name from EditorPreview. Removed duplicate effects. Removed AdjustPanel (merged into Filters).

============================================================
DUPLICATES
============================================================

No duplicate bug entries.

============================================================
EXAMPLE
============================================================

BUG-ID:
PREVIEW-001
CATEGORY:
Overlay
SEVERITY:
HIGH
STATUS:
OPEN

TITLE:
Text overlay is invisible in preview

SYMPTOM:
Text can be added and exists in project state, but is not visible on the video canvas.

REPRODUCTION:
1. Import a video.
2. Add text.
3. Enter visible text.
4. Return to preview.

EXPECTED:
Text renders above the video.

ACTUAL:
Text is not visible.

ROOT CAUSE:
Must be determined from source inspection.

AFFECTED FEATURE:
Text overlay

PREVIEW IMPACT:
BROKEN

EXPORT IMPACT:
UNKNOWN

REGRESSION TEST:
Add a test proving the overlay becomes visible.

DEVICE TEST:
Required.

OWNER:
overlay

COMMIT:
Pending

Do not mark FIXED until runtime behavior is verified.
