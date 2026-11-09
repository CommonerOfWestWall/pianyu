# Notice: what SpektraLab is derived from, and what it changed

**The SpektraLab engine is a C++/Metal implementation derived from spektrafilm by Andrea Volpato
(GPL-3.0-or-later).** spektrafilm is at https://github.com/andreavolpato/spektrafilm; its film
simulation, its measured film and paper profiles and its print and scan model are what SpektraLab
renders. SpektraLab is not affiliated with or endorsed by the spektrafilm project.

This file is the notice GPL-3.0 §5(a) asks of a modified version. The licence texts themselves are
beside it and are not edited: `LICENSE` (GPL-3.0) and `SPEKTRAFILM_LICENSE.txt` (CC BY-SA 4.0 for the
spektrafilm profiles, the LUTs and their direct derivatives, with Andrea Volpato's preamble).

## What was modified

| | |
|---|---|
| the engine (`engine/`) | spektrafilm's Python pipeline, written again in C++20 with Metal kernels behind a C ABI (`spk_*`), from 2026-09-10 (RFC-014). It is held to the Python reference by the parity harnesses in `engine/tests/`, which import the upstream `spektrafilm` package as their oracle. Nodes and wire fields the reference does not have are SpektraLab's additions (`API-SPEC-callable-render-service.md` §11 on) |
| the profiles (`engine/resources/profiles/`) | spektrafilm's 28 profiles, baked from the upstream data and unmodified |
| the print LUTs and the minilab tables (inside `engine/resources/spektralab_constants.bin`, indexed by `print_luts.json`; `engine/resources/minilab/`) | derivatives of those profiles (the print LUTs baked by `engine/tools/bake_resources.py` from the upstream reference); CC BY-SA 4.0 like the profiles they encode |
| SpektraLab's own data (`engine/resources_product/`) | profiles SpektraLab built from manufacturers' published data sheets, and one alias over an unmodified spektrafilm profile; `engine/resources_product/NOTICE.md` says what each is built from |
| the application | SpektraLab's own, GPL-3.0-or-later |

## Other components

- **metal-cpp** (`engine/third_party/metal-cpp`), Apple's C++ bindings for Metal, Apache-2.0.
- **spklib** (`vendor/spklib/macos/spklib-host`), the helper that reads SpektraLab libraries and
  Capture One catalogs, MIT, shipped as a prebuilt binary and run as a separate process
  (`API-SPEC-library-helper.md`).

## Pianyu modifications — 2026-11-09

Pianyu (片屿) is an independent Android application by the Pianyu contributors, with a new Kotlin/Compose UI, JNI integration, curated recipes, mobile preview/export, HSL, geometry editing and local recipe/library persistence. Code is GPL-3.0-or-later. The original upstream notices above and license/ texts are retained. The fixed source base is SpektraLab d90cf9a005d5c7eb4c0bc1eb9cac17459c2a9289 plus the changes included in native/SOURCE-DIRTY-DIFF.patch and docs/build-engine.ps1. Unfused-FMA support is verified only on the documented device; it is not a cross-GPU parity claim. The Android UI and binary core are not taken from the community Android port.

Version 0.3.1 replaces the old internal test photograph and orientation fixtures with an original AI-generated demonstration asset, and includes an in-app license reader. Profiles/LUT data remain under their upstream CC BY-SA 4.0 terms; no material data recalibration is claimed. See asset-provenance/DEMO.md and docs/OPEN-SOURCE-NOTES.md.

