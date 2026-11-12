# BaMah Reel Studio — Android

Native Android app for creating vocabulary/pronunciation challenge cards and recording Instagram Reels.

## Build
Open this project in Android Studio or push it to GitHub. The included `.github/workflows/android.yml` runs unit tests, assembles the debug APK, and uploads it as a workflow artifact.

- JDK 17
- Android SDK 35
- Gradle 8.9
- `gradle testDebugUnitTest`
- `gradle assembleDebug`

## V2 / app version 1.2
- Reference-inspired two-state challenge card: white word during question phase, accent-colored word during reveal.
- Header with optional translucent challenge-label pill, editable CTA, and automatic word counter.
- Optional peach top-edge tab.
- Large main word, optional emoji tile, optional definition.
- Upper timer bar remains visible in both phases and fills completely on reveal.
- `YOUR TURN` changes to pronunciation; uppercase stressed syllable letters are accent-colored.
- Bottom segmented challenge progress.
- Main and accent colors with preset swatches and custom HEX values; text color override.
- All card text fields are optional. Empty values hide their corresponding elements; blank cards can be added and exported.
- Optional visibility toggles for challenge label, CTA, top tab, icon, timer and bottom progress.
- Zero-to-60-second manual answer/reveal timers.
- JSON import/export preserves content and appearance.
- Full-screen portrait CameraX recording; front-camera video is mirrored to match the selfie preview.
- Media3 Transformer burns the challenge card into the exported 720x1280 (9:16) H.264/AAC MP4.
- Saves finished MP4 to Movies/BaMah Reel Studio.

## GitHub Actions
The workflow is included at `.github/workflows/android.yml`. Ensure hidden files are included when uploading the project to GitHub.
