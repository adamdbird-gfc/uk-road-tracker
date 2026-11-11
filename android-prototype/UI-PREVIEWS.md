# Roadprints Android UI previews

Run from the repository root with JDK 17, Gradle 8.10 and Android SDK Platform 35 installed. Set `ANDROID_HOME` if the SDK is outside Gradle's configured location.

```sh
./android-prototype/scripts/ui-previews.sh
```

Open the PNGs in `android-prototype/app/build/ui-previews/`. Pass a directory as the first argument to write elsewhere. The first run resolves the normal Android test dependencies and the Robolectric API 35 runtime. Later runs reuse Gradle's caches.

| PNG | State |
| --- | --- |
| 01 | Undiscovered Milepost with the shared silhouette |
| 02 | Discovered Milepost with catalogue name, design, journey date and a supported achievement |
| 03 | Milepost achievement cards with a mixture of earned and hidden entries |
| 04 | An unlocked mystery chapter with earlier chapters available and the next chapter locked |
| 05 | Overview map controls, Milepost pins and a sample location accuracy circle |
| 06 | NCN layer selector and the discovered and undiscovered pin key |

These previews render the production Android Views with small fixtures supplied by `RoadprintsUiPreviewTest`. Change the fixture when adding a review state and change the production component to iterate on its appearance. Keep the fixture in the test source set so it has no runtime or APK footprint. The task compiles Android resources and code and runs only the preview tests. It uses a plain test application and runs without NCN historical assessment, Firebase initialisation, journey processing, map tile downloads, an emulator or APK packaging.

## Device acceptance

The PNGs show API 35 on a 412 dp wide viewport with sample content. The map uses a fixture and the offline basemap fallback. The host captures the Views themselves, so system bars, physical screen rendering, the Android permission prompt, and the actual device location provider require a phone check.

Install the signed Roadprints update over the existing app. In **Map**, tap the target icon and grant location access while using the app if prompted. Confirm the dot and accuracy circle stay at the correct coordinates while panning or zooming and clear when you leave the map. Use the existing journey archive and actual Milepost and achievement records to review the integrated UI. Keep the existing installation and its data in place.
