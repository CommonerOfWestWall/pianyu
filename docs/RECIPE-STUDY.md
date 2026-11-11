# Recipe study

`RecipeStudyDeviceTest` is a repeatable device experiment for comparing saved edit recipes against a reference film or AI generated digital input. It uses the actual `NativeCore.render` and `PhotoIO.finish` pipeline and writes PNG outputs plus `manifest.json` under the test app's `filesDir/recipe-study/<study>/` directory. The results are reference measurements, not a colorimetric calibration.

The input directory contains an sRGB `input.png` with a maximum dimension of 1536 and a `recipes.json` array:

```json
[
  {"id":"daily-warm", "settings":{"lookIndex":0,"strength":0.8,"warmth":0.08}}
]
```

`settings` uses the same schema as `settingsToJson`; omitted values use the normal edit defaults. Recipe IDs and the optional instrumentation `study` argument are restricted to ASCII letters, digits, `_`, and `-`. The test asserts full-frame crop, zero crop aspect, zero rotation/straightening and no flips. It also verifies the output has the input dimensions.

The test only reads and writes its isolated test files. It does not access the user's library or MediaStore. A typical workflow is:

```text
adb shell am instrument -e class studio.pianyu.app.RecipeStudyDeviceTest -e study default -w studio.pianyu.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell run-as studio.pianyu.app tar -cf - files/recipe-study/default
```

The exact test APK package and instrumentation component should be taken from the current build's manifest/output rather than assumed.

The three published studies and their full manual recipes are documented in [FILM-STUDY.md](FILM-STUDY.md). Each stage re-renders the same input with cumulative settings. The historical scan is a visual reference; it is never fed into the rendering pipeline. The app does not currently provide a JSON recipe import UI.
