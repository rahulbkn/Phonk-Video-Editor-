PRODUCTION-GRADE ANDROID VIDEO EDITOR — ZERO-BROKEN-FUNCTIONALITY REQUIREMENTS

You are working on an EXISTING Android video editor project.

Your task is to transform the existing project into a professional, production-quality Phonk / AMV / Anime / Short Video Editor where:

EVERY BUTTON WORKS.
EVERY FEATURE WORKS.
EVERY SCREEN RENDERS CORRECTLY.
EVERY STATE TRANSITION WORKS.
NO UI IS CUT OFF.
NO FEATURE BREAKS ANOTHER FEATURE.
NO CRASHES FROM NORMAL USER ACTIONS.

Do not create a visual-only mockup.

Do not leave fake controls.

Do not add buttons that do nothing.

Do not claim functionality is implemented if it is not actually connected to working logic.

---

1. INSPECT THE ENTIRE EXISTING PROJECT FIRST

Before changing code, inspect the complete project.

Identify:

- Package name
- Application class
- Launcher Activity
- All Activities
- All Fragments
- Navigation
- XML layouts / Compose screens
- ViewModels
- Repositories
- Services
- Workers
- Media processing code
- Video playback implementation
- Audio processing implementation
- FFmpeg implementation if present
- MediaCodec implementation if present
- Media3/ExoPlayer implementation if present
- File picker
- Storage system
- Permissions
- Theme system
- Light/dark mode
- Localization
- Existing settings
- Existing project persistence
- Existing export system
- Existing database
- Existing dependencies

Search the entire project for:

- TODO
- FIXME
- Coming soon
- Not implemented
- UnsupportedOperationException
- empty click listeners
- placeholder buttons
- fake data
- hardcoded timeline data
- dummy export functions
- temporary UI
- crash-prone code
- unsafe casts
- nullable crashes
- lifecycle problems

Do not assume anything about the project before inspecting it.

---

2. PRESERVE THE EXISTING ARCHITECTURE

Do NOT rewrite the entire application unnecessarily.

If the project uses:

XML → keep XML.

Compose → keep Compose.

MVVM → preserve MVVM.

Existing repository architecture → preserve it.

Existing media stack → reuse it.

Existing FFmpeg → reuse it.

Existing Media3 → reuse it.

Existing storage system → reuse it.

Existing theme system → improve it rather than replacing it.

Do not introduce duplicate frameworks for the same functionality.

---

3. ZERO PLACEHOLDER FUNCTIONALITY

Every visible interactive element must have a real implementation.

For every:

- Button
- Icon
- Toolbar item
- Bottom navigation item
- Tab
- Menu item
- Slider
- Switch
- Checkbox
- Text field
- Timeline control
- Clip handle
- Playback control
- Dialog action
- Bottom-sheet action
- Export option
- Effect option
- Filter option
- Transition option

verify that it performs the expected operation.

DO NOT leave:

"Coming soon"

"TODO"

"Not implemented"

"Demo"

"Test"

or fake Toast-based functionality.

If a requested feature cannot be implemented because the current media engine does not support it, integrate the closest technically correct implementation or clearly isolate the limitation rather than creating a fake working-looking button.

---

4. FUNCTIONAL CONTRACT FOR EVERY BUTTON

Every button must have:

1. Correct click listener.
2. Correct state update.
3. Correct UI feedback.
4. Correct navigation if applicable.
5. Correct disabled state when unavailable.
6. Correct loading state when processing.
7. Correct error handling.
8. Correct lifecycle behavior.
9. Correct back navigation.
10. Correct state restoration.

Example:

If the user taps SPLIT:

- Read current playhead.
- Identify selected clip.
- Validate that the playhead is inside the clip.
- Split the clip at the exact position.
- Update timeline state.
- Update thumbnails.
- Update waveform if required.
- Update undo history.
- Update redo history.
- Preserve playback position.
- Refresh UI.
- Do not crash if nothing is selected.
- Show an appropriate disabled state when splitting is impossible.

Do this level of correctness for every editor operation.

---

5. EDITOR STATE MUST BE CONSISTENT

Create or improve a single reliable editor state architecture.

State should include at minimum:

- Project
- Current video
- Video clips
- Audio tracks
- Overlay tracks
- Text layers
- Effects
- Filters
- Transitions
- Speed curves
- Keyframes
- Beat markers
- Current playhead
- Current selection
- Selected track
- Timeline zoom
- Playback state
- Current tool
- Export state

Never allow UI and underlying editor state to disagree.

Example:

If a clip is deleted:

- Remove it from the model.
- Remove it from timeline.
- Remove associated effects if appropriate.
- Update selection.
- Update thumbnails.
- Update duration.
- Update undo history.
- Update preview.
- Update UI.

Do not only remove the visual item.

---

6. TOOL SWITCHING MUST BE SAFE

Users must be able to switch repeatedly between tools without breaking state.

Test sequences such as:

Video → Audio → Text → Effects → Speed → Beat → Filters → Transform → Video

and:

Effects → Speed → Timeline → Effects → Export → Back → Effects

Every switch must:

- Save current changes.
- Restore correct state.
- Remove temporary UI correctly.
- Avoid duplicated views.
- Avoid stale references.
- Avoid memory leaks.
- Avoid crashes.

When switching tools, never lose unsaved editor changes.

---

7. RAPID BUTTON TESTING

Assume users will tap buttons rapidly.

Protect against:

- Double click
- Triple click
- Rapid navigation
- Rapid play/pause
- Rapid split
- Rapid delete
- Rapid export
- Rapid tool switching

Prevent duplicate operations.

For long-running operations:

- Disable conflicting buttons.
- Show progress.
- Prevent duplicate jobs.
- Allow cancellation when safe.

Never create two simultaneous exports accidentally.

---

8. BACK BUTTON BEHAVIOR

Implement predictable back navigation.

If an editing panel is open:

Back → close panel.

If a bottom sheet is open:

Back → close sheet.

If text editing is active:

Back → finish/cancel editing according to state.

If a dialog is open:

Back → dismiss dialog.

If fullscreen preview is active:

Back → exit fullscreen.

Only when there is no temporary editor state:

Back → navigate to previous screen.

Never unexpectedly destroy the project.

---

9. UNSAVED CHANGES

Never lose user work.

If leaving an editor with unsaved changes:

Show:

Save Project
Discard Changes
Cancel

If autosave exists, ensure it is reliable.

Autosave must not block the UI.

Recover project state after:

- Activity recreation
- Rotation if supported
- Background/foreground
- Process recreation where possible
- Temporary memory pressure

---

10. TIMELINE CORRECTNESS

Timeline operations must be mathematically consistent.

Required:

- Accurate duration
- Accurate clip positions
- Accurate playhead
- Accurate trim
- Accurate split
- Accurate clip movement
- Accurate snapping
- Accurate zoom
- Accurate waveform position

Never allow:

- Negative duration
- Overlapping invalid clips
- Impossible clip positions
- Playhead outside valid range
- NaN values
- Infinity values

Clamp all values safely.

---

11. PREVIEW MUST MATCH TIMELINE

The preview position and timeline position must remain synchronized.

If playhead moves:

Preview moves.

If video seeks:

Timeline moves.

If clip is selected:

Preview reflects selection.

If speed changes:

Preview duration/position updates correctly.

If clip is split:

Preview continues from the correct location.

Never allow timeline and preview to show different timestamps.

---

12. AUDIO / WAVEFORM CORRECTNESS

Waveform rendering must be synchronized with actual audio.

Verify:

- Correct duration
- Correct waveform scaling
- Correct scrolling
- Correct zoom
- Correct playhead
- Correct beat marker position
- Correct audio trimming

Never generate a waveform that is visually offset from the actual audio.

---

13. BEAT SYNC CORRECTNESS

Beat detection must produce valid timestamps.

Validate:

- BPM > 0
- Beat timestamps are sorted
- Beat timestamps are within audio duration
- No duplicate markers
- No negative markers

When applying:

Beat Cut
Beat Zoom
Beat Flash
Beat Shake
Beat Velocity

the effect must correspond to the correct timeline position.

---

14. VIDEO PROCESSING SAFETY

Never process video on the main/UI thread.

All expensive operations must run asynchronously.

Examples:

- Video decoding
- Thumbnail generation
- Waveform generation
- Beat analysis
- FFmpeg processing
- Rendering
- Export
- File copying
- Media metadata extraction

UI must remain responsive.

---

15. MEDIA MEMORY MANAGEMENT

Optimize for low-memory Android devices.

Never load an entire 4K video into memory.

Use:

- Thumbnail scaling
- Bitmap recycling where applicable
- Caching
- Lazy loading
- Efficient preview resolution
- Background processing

Prevent:

OutOfMemoryError

Bitmap memory explosions

Massive timeline allocations

---

16. UI RENDERING — ZERO CLIPPING

Every screen must render correctly.

No:

- Text clipping
- Button clipping
- Icon clipping
- Bottom toolbar clipping
- Timeline clipping
- Dialog clipping
- Bottom sheet clipping
- Navigation bar overlap
- Status bar overlap
- Keyboard overlap
- Content hidden behind system bars
- Off-screen controls
- Overlapping labels

Use:

- dp
- sp
- constraints
- proper padding
- scrolling containers
- WindowInsets
- safe areas

Never solve UI problems by simply reducing font size excessively.

---

17. SMALL SCREEN SUPPORT

Test the UI conceptually and programmatically for:

- Small Android phones
- Normal phones
- Large phones
- Different aspect ratios
- 16:9
- 18:9
- 20:9
- 21:9

Important editor controls must remain reachable.

Horizontal toolbars should scroll instead of clipping.

Vertical panels should scroll when content exceeds available height.

---

18. KEYBOARD SAFETY

When keyboard opens:

- Do not hide the text editor.
- Do not cover input fields.
- Do not move the entire editor unpredictably.
- Keep Save/Done controls accessible.
- Handle IME insets correctly.

Test:

Text → Edit → Keyboard → Rotate/Back → Save.

---

19. DIALOG / BOTTOM SHEET SAFETY

Every dialog and bottom sheet must:

- Fit screen
- Scroll if needed
- Respect system insets
- Have correct dismissal
- Have correct buttons
- Avoid duplicated instances
- Restore underlying editor state

No dialog should appear partially outside the screen.

---

20. THEME SWITCHING

Light and dark themes must work across the entire application.

Light:

Background #FFF0F6
Primary #FF7EB6
Secondary #FFC1E3
Surface #FFFFFF
Surface Variant #FFE6F0
Accent #FF4D94
Text #2B2B2B
Secondary Text #6B6B6B

Dark:

Background #0D1025
Primary #8B5CF6
Secondary #6D28D9
Surface #161B33
Surface Variant #1E2347
Accent #E879F9
Text #F1F1F1
Secondary Text #B0B3D1

After switching theme:

- Current screen must remain open.
- Editor state must remain intact.
- Timeline position must remain intact.
- Selected clip must remain selected.
- No activity should unexpectedly restart unless architecture requires it.
- No UI component should retain the wrong theme.
- No invisible black/white text should appear.
- Dialogs and bottom sheets must update too.

---

21. NO HARDCODED THEME COLORS

Do not write theme colors directly in:

Kotlin
Java
XML layouts
Compose components

Use centralized resources/theme tokens.

Every UI component must support both themes.

---

22. PROFESSIONAL UI QUALITY

The editor must visually look like a serious professional application.

Use:

- Consistent spacing
- Consistent typography
- Consistent icon sizes
- Consistent corner radius
- Proper elevation
- Proper hierarchy
- Clear selected states
- Clear disabled states
- Professional empty states
- Professional loading states

Avoid:

- Random colors
- Random margins
- Oversized icons
- Tiny text
- Excessive gradients
- Excessive rounded cards
- Clutter
- Default Android-looking controls

---

23. TOOLBAR QUALITY

Top toolbar must correctly support:

Back
Project name
Undo
Redo
Preview
Export
More

Each button must be:

- Visible
- Reachable
- Correctly enabled/disabled
- Functional
- Accessible

If screen width is insufficient, prioritize important controls and move secondary actions into More.

Never let toolbar icons overlap.

---

24. BOTTOM TOOLBAR QUALITY

The editing toolbar must:

- Scroll horizontally.
- Never clip the final tool.
- Keep selected tool visible.
- Preserve scroll position where appropriate.
- Correctly open corresponding panel.
- Close correctly.
- Update when selection changes.

Tools:

Media
Audio
Text
Effects
Filters
Speed
Beat
Transition
Overlay
Adjust

---

25. EXPORT MUST BE REAL

Export must actually generate the edited video.

Do not create a fake progress bar.

Progress must represent real processing when possible.

Handle:

- Success
- Failure
- Cancellation
- Insufficient storage
- Unsupported codec
- Missing input
- Corrupted media
- Permission errors

After export:

Open
Share
Save location
Edit again

must all work correctly.

---

26. ERROR HANDLING

Normal user mistakes must never crash the app.

Handle:

- No media selected
- No clip selected
- Invalid timeline position
- Missing audio
- Missing video
- Corrupt file
- Unsupported format
- Permission denied
- Storage full
- Export failure
- Decoder failure
- Encoder failure
- Process cancellation
- Activity recreation
- Null media reference

Show useful user-facing error messages.

Log technical details separately.

---

27. CRASH PREVENTION

Search for and eliminate:

- NullPointerException
- IndexOutOfBoundsException
- IllegalStateException
- ClassCastException
- ConcurrentModificationException
- SecurityException
- FileNotFoundException
- OutOfMemoryError
- lifecycle-related crashes
- Fragment transaction crashes
- Activity context leaks
- coroutine/job leaks

Do not blindly catch Throwable everywhere.

Fix root causes.

---

28. LIFECYCLE SAFETY

Test editor behavior when:

- App goes to background.
- App returns to foreground.
- Activity recreates.
- Screen is recreated.
- Export continues.
- Preview is playing.
- Audio is playing.
- A panel is open.

Release/reconnect media resources correctly.

Cancel obsolete background jobs.

Do not leak Activities or Views.

---

29. ACCESSIBILITY

Every icon-only button must have:

contentDescription

Every important control must be reachable.

Text must remain readable.

Touch targets should be appropriately sized.

Do not depend exclusively on color.

---

30. PERFORMANCE VALIDATION

Avoid unnecessary recompositions/layout passes.

Avoid:

- Rebuilding the entire timeline on every frame.
- Regenerating thumbnails repeatedly.
- Regenerating waveforms unnecessarily.
- Excessive allocations.
- Main-thread disk operations.
- Main-thread video processing.

Scrolling must remain smooth.

Preview must remain responsive.

Timeline must remain responsive.

---

31. TEST ALL FEATURE COMBINATIONS

Do not test features only individually.

Test combinations:

Trim + Speed

Split + Effect

Split + Transition

Audio + Beat markers

Beat markers + Velocity

Text + Animation

Text + Keyframes

Filter + Effect

Effect + Transition

Multiple clips + Audio

Delete + Undo

Undo + Redo

Export + Cancel

Theme switch + Editor

Theme switch + Dialog

Theme switch + Bottom Sheet

Theme switch + Timeline

Tool switching + Unsaved changes

---

32. UNDO / REDO MUST BE REAL

Every destructive editor operation must create a reversible state when appropriate.

Test:

Action
Undo
Redo
Action
Undo
New Action

After a new action following Undo, invalid redo history must be cleared correctly.

---

33. NO STATE DESYNCHRONIZATION

Never allow:

UI says clip exists but model says deleted.

UI says audio muted but audio is playing.

UI says effect disabled but renderer applies it.

UI says 2x speed but preview plays at 1x.

UI says export finished but file doesn't exist.

UI says beat marker exists but timeline doesn't contain it.

Fix the underlying state architecture if necessary.

---

34. FILE SAFETY

Use Android-supported file APIs.

Handle modern Android storage restrictions correctly.

Do not assume direct filesystem access.

Validate:

- URI permissions
- MIME type
- File existence
- Read access
- Write access

Never crash because a URI becomes inaccessible.

---

35. BUILD VERIFICATION

After implementation:

Run the actual project build.

Fix ALL:

- Compilation errors
- Kotlin errors
- Java errors
- XML errors
- Resource errors
- Manifest errors
- Gradle errors
- Dependency conflicts
- Lint issues that indicate real bugs

Do not stop after the first successful compilation.

---

36. STATIC CODE AUDIT

Before finishing, search again for:

TODO
FIXME
Coming soon
Not implemented
UnsupportedOperationException
empty click listeners
fake buttons
placeholder data
debug-only UI
hardcoded sample media
fake export
fake progress

Remove or properly implement anything found.

---

37. FINAL UI AUDIT

Inspect every screen.

Verify:

- No clipping.
- No overlapping elements.
- No hidden controls.
- No broken scrolling.
- No wrong colors.
- No invisible text.
- No broken icons.
- No incorrect padding.
- No system-bar overlap.
- No keyboard overlap.
- No bottom-navigation overlap.
- No timeline overflow.
- No toolbar overflow.

---

38. FINAL FUNCTIONAL AUDIT

Create a checklist and verify:

[ ] App launches

[ ] Existing features work

[ ] Project creation works

[ ] Media import works

[ ] Video preview works

[ ] Play/pause works

[ ] Seeking works

[ ] Timeline works

[ ] Clip selection works

[ ] Split works

[ ] Trim works

[ ] Delete works

[ ] Duplicate works

[ ] Undo works

[ ] Redo works

[ ] Audio import works

[ ] Waveform works

[ ] Beat detection works if supported

[ ] Beat markers work

[ ] Speed works

[ ] Velocity works if supported

[ ] Effects work

[ ] Filters work

[ ] Transitions work

[ ] Text works

[ ] Transform works

[ ] Keyframes work if supported

[ ] Project save works

[ ] Project restore works

[ ] Export works

[ ] Export cancellation works

[ ] Share works

[ ] Error handling works

[ ] Back navigation works

[ ] Light theme works

[ ] Dark theme works

[ ] Theme switching preserves editor state

[ ] No major UI clipping

[ ] No normal-action crashes

[ ] No fake controls remain

---

39. IMPORTANT: DO NOT HIDE BUGS

If you discover a bug:

1. Reproduce it.
2. Identify the root cause.
3. Fix the root cause.
4. Re-test the original scenario.
5. Test related functionality.
6. Check for regressions.

Do not hide bugs with:

- try/catch everywhere
- disabling buttons permanently
- silently ignoring errors
- fake success messages
- resetting editor state
- force-closing panels
- deleting user data

---

40. FINAL REQUIREMENT

The final application must satisfy this standard:

A user should be able to open the editor, import media, edit a Phonk video, move between tools repeatedly, switch light/dark theme, undo/redo changes, save the project, export the final video, and return to editing without encountering broken buttons, incorrect state, UI clipping, unexpected navigation, or normal-action crashes.

The UI must look professional.

The editor must behave professionally.

The state management must be reliable.

The rendering must be correct.

The media processing must be real.

The application must be robust on Android devices.

DO NOT FINISH AFTER UI IMPLEMENTATION ONLY.

Finish only after:

INSPECT → IMPLEMENT → CONNECT → BUILD → TEST → FIX → REBUILD → AUDIT.

If a feature is visible, it must work.

If a button is enabled, it must work.

If a control cannot currently work, it must not misleadingly appear as a completed feature.

No broken UI.
No fake functionality.
No silent failures.
No accidental data loss.
No normal-action crashes.
No clipped controls.
No state desynchronization.