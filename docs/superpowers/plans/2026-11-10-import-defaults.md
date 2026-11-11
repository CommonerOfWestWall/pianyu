# Import diagnostics and consistent style defaults implementation plan

**Goal:** Keep photo import understandable on unverified devices, distinguish memory from GPU failures, and make new-photo style strength start consistently without changing saved edits or measured film data.

**Architecture:** Keep the existing Kotlin/Compose + serialized Vulkan engine. Add bounded, stage-aware error reporting and preview-only memory recovery; retain original-resolution export. Version the curated recipe interpretation so new presets can display 100 while preserving their already reviewed film blend and finish. No CPU film renderer or GPU compatibility bypass is added.

**Confirmed context:** User identifies OPPO Find X9, system “内存使用异常” then process exit. Root cause is unknown without device logs. Native allocation errors incorrectly mentioned original-size export during preview; decode was already capped to960. SpektraLab default AE is off/EV0/grain on; Python defaults differ. Phones are unavailable this turn; no ADB. Add restart-readable exit reason matched to checkpoint/PID; do not claim native crash prevention.

**Files / ownership:** Main agent owns `EditorModel.kt`, `NativeCore.kt`, `PhotoIO.kt`, `EditorScreen.kt`, JNI error context, new failure diagnostic helpers, documentation and packaging. One `luna_worker` owns the pure normalized style interpretation (`LookFinish.kt`, `EditSettings.kt`, `LibraryStore.kt`, optional small pure helper), corresponding unit/device compatibility tests. No overlapping edits.

- [x] Record upstream defaults with code references; distinguish app defaults from Python defaults.
- [x] Add recipe version2: new style UI defaults to100; preserve calibrated blend/finish and v0/v1 saved edits. Cover equivalent endpoints, partial strength, bypass, persistence and recipe geometry.
- [x] Render grain off / subtle / upstream default: 63 desktop outputs, inspect contacts/100% details; new-photo UI grain .125, engine .25. Models/assets match apart from platform math-probe shader; EV0/AE off preserved.
- [x] Classify decode/preview/export failures, retain local reports, preserve original native report through ProcessingException. Add API30+ system-exit diagnostics; no URI/photo storage or upload.
- [x] Single preview-only640 recovery, dimension-aware cache, no reduced source cache pollution, diagnostic view/copy. Original export never silently shrinks. Added Android test source; execution pending device.
- [x] Run host unit tests and representative diagnostic/parameter checks, assemble debug/release/androidTest and lint. Mark new Android tests and other-GPU recovery as pending device verification.
- [x] Update user guide, source/version provenance and candidate packaging. Keep v8/source/video evidence intact; create a new clearly labelled candidate rather than overwriting prior evidence.

**Observable acceptance:** New images and style selection show100 for their default recipe; old JSON renders identically under its saved version. EV0 is described as source-exposure preservation, not no film processing. Allocation errors during preview no longer tell users to change export size; raw error/device context can be copied. No result claims to reproduce the absent phone failure or prove all-GPU compatibility.

**Desktop result:** 6 JVM tests passed; assembleDebug/Release/DebugAndroidTest and lintRelease succeeded. Lint: 0 errors, 33 warnings, 1 hint (four intentional background synchronous diagnostic writes, five additional KTX suggestions, plus prior warnings). Android execution and OPPO reproduction remain pending. v9 candidate packaging includes matching GPL source, APK signature/assets/hash checks; v8 artifacts are retained.
