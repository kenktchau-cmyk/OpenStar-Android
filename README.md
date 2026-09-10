# OpenStar for Android

[English](README.md) | [繁體中文](README.zh-TW.md)

An open-source Android sky map built on the [OpenStar Python/Skyfield engine](https://github.com/kenktchau-cmyk/OpenStar). Explore the sky by touch or use an optional camera-and-compass AR view.

## Get the source

```sh
git clone --recurse-submodules https://github.com/kenktchau-cmyk/OpenStar-Android.git
cd OpenStar-Android
```

Already cloned without the engine? Run `git submodule update --init --recursive`.

## Shared Python engine

`engine/` is a Git submodule pinned to a specific commit of OpenStar. Chaquopy packages `engine/python/` directly. `Catalog.calculate()` calls `StarMapGenerator.positions_json()`, which calls the public `generate_star_map()` function. The native Android UI renders those returned positions. Astronomy changes belong in the Python repository; update the submodule commit after verifying them here.

The catalogue snapshot at `app/src/main/assets/stars.tsv` matches `engine/python/data/stars.tsv`. If you update the engine catalogue, copy it to the Android asset and retain its attribution. DE421 is bundled through the engine. The standalone script, its API examples, requirements and tests live in [OpenStar](https://github.com/kenktchau-cmyk/OpenStar).

## Features

Normal mode uses the **same perspective projection as AR**, with a dark sky background. **Drag with one finger** to look left, right, up or down through the sky; **pinch with two fingers** to zoom from 0.5× to 6×. Your finger controls the direction; phone motion and the camera do not control this mode. A direction/altitude readout, centre crosshair and horizon help you orient yourself. **Double-tap** or choose **Reset map** to return to north, 35° above the horizon, at 1× zoom. **Find star** turns the view toward the selected star; stars below the horizon remain hidden and are identified as below the horizon. Screen rotation preserves the viewing direction and zoom.

Use the **hour bar** below the map to choose **00:00–23:00 on the selected date**, in the time zone of the selected latitude/longitude. Its label follows your finger; release to calculate the sky at that hour. Arrow keys change the hour in one-hour steps. Tap **Live** on the right to return to the current date and time, updated every 30 seconds. Moving the bar leaves Live mode. Use **Time** to choose another date or an exact minute. For daylight-saving changes, a skipped hour moves forward to the next valid local time; a repeated hour keeps the selected time's offset when possible.

The observing time zone is selected automatically from the coordinates and shown by its IANA name (for example, `Asia/Hong_Kong`). The hour bar, date/time picker, Live and AR all use that zone, including its daylight-saving rules. Changing location preserves the observation instant and shows its local date/time at the new place. An explicitly typed ISO UTC offset remains authoritative. The automatic initial time is reformatted for the selected location until you edit it yourself.

The app bundles Skyfield 1.54, Python 3.13, NumPy and JPL DE421 using Chaquopy 17.0.0. Sky calculations work offline; the geographic location picker uses the internet for street tiles. There are no accounts or analytics. The APK supports **Android 8+ on ARM64 devices**, plus x86-64 emulators. It does not include older 32-bit CPUs. NumPy is rebuilt with 16 KB native alignment and its bundled C numerical routines; see `vendor/README.md`.

### Choose a location on a map

The initial time/location form requests foreground location permission to fill the coordinates with **your device location by default**. Approximate permission works too. It uses a fix from the last two minutes or waits up to 20 seconds for a fresh one. If permission is denied, location is disabled, or no fix arrives, the saved/reference coordinates remain clearly labelled and you can choose manually. Typing coordinates or selecting a map point cancels the pending location lookup. **My location** retries when you want it; the app does not track location in the background.

Tap **Choose on map** in the initial form, or **Location** below the sky chart. Tap anywhere on the geographic map or drag its pin, then tap **Use this location**. The displayed latitude/longitude update with the pin. You can also use **My location** or **Enter coordinates** in this screen. Back/Cancel leaves the prior observing location unchanged. The selection is saved on your device and used for both the normal sky map and AR.

The picker bundles **Leaflet 1.9.4** and a **Natural Earth world outline**. OpenStreetMap supplies street tiles over HTTPS without an API key; its attribution stays visible. Viewing street tiles sends the viewed tile area and your IP address to OpenStreetMap. Only visible tiles are requested, with normal HTTP caching; there is no map download or prefetch feature. Without internet, the bundled coarse world outline and manual coordinate entry remain available; zoom out to see the outline clearly. No offline street detail is bundled. The street map covers about 85°S–85°N; manual coordinate entry also accepts the poles for sky calculation.

### AR mode

After generating the map, tap **AR**. Each new AR visit starts with **Camera off**: stars, labels and constellation guides follow your phone over a dark background, without camera access. Point the back of your phone at the sky. Turn the **Camera** switch on for a live camera background; permission is requested only when needed. Switch it off again to close the camera while continuing to explore the sky. Screen rotation preserves the current switch setting.

AR has just two main controls: the **Camera on/off** switch and the **•••** options menu. A brief pop-up gives the pointing instructions when AR opens; there is no permanent instruction banner. Tap a star marker to show its details at the bottom. Open **•••** for red night mode, constellation lines, star names, star density, reset alignment, and About/open-source licenses. The same menu contains **Live now**, **Calibrate**, **AR details**, and **Back to map**. Time, location, camera status and compass readouts appear only in **AR details**, keeping that information off the sky view. Android Back also returns to the normal touch-controlled sky.

AR uses the map's selected location and time. Night mode, density, names and constellation lines are shared between both modes: changes in AR survive rotation and appear when you return to the normal sky. For alignment with the actual sky, choose your current location and **••• → Live now**. In **AR details**, a historical or future time is labelled **TIME PREVIEW**.

The compass is corrected to true north for the observer location. **••• → Calibrate** explains the figure-eight movement and provides a heading adjustment of ±30°. **Reset alignment** clears that adjustment and the selected-star highlight; the sky continues to follow your phone. This is a camera-and-compass overlay, with approximate alignment; it does not recognize stars in camera frames or perform ARCore tracking. Magnetic interference, sensor accuracy, camera field-of-view estimates and lens distortion can affect alignment. Real outdoor accuracy still requires checking on a physical phone.

Camera frames are used only for the live preview: no photographs, recording or uploads. Direction tracking requires an orientation sensor, or an accelerometer and compass. The rear camera is optional; camera permission denial or missing camera hardware keeps the compass sky view available. Camera and sensor listeners stop when AR leaves the foreground. Before camera metadata is available, the camera-free view uses a 60-degree field of view along the screen's long edge.

## Build

Open this directory in Android Studio. Use JDK 17 or 21, Android SDK platform 36, and a local Python 3.13 interpreter. Gradle 9.1.0, Android Gradle Plugin 9.0.1 and Chaquopy 17.0.0 are pinned in this repository.

Let Android Studio set the SDK path, or configure `ANDROID_HOME`. Set the Python executable in an ignored `local.properties` file:

```properties
openstar.buildPython=C\:/path/to/Python313/python.exe
```

Use forward slashes, escape a Windows drive colon and omit quotes. Alternatively set `OPENSTAR_BUILD_PYTHON` to the executable path; this overrides `local.properties`. The build interpreter must be Python **3.13** to match Android's embedded runtime. Desktop OpenStar has separate Python 3.10–3.12 requirements; do not install its desktop NumPy wheel into the Android build.

Windows:

```powershell
.\gradlew.bat assembleDebug lintDebug
```

macOS/Linux:

```sh
sh gradlew assembleDebug lintDebug
```

First builds need internet for build dependencies. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. It is debug-signed for local testing. Android 8+ ARM64 devices and x86-64 emulators are supported. The custom NumPy wheels in `vendor/wheels/` are required for 16 KB memory-page compatibility; provenance and reproduction steps are in [vendor/README.md](vendor/README.md).

## Coordinates and scope


- Apparent star directions are calculated with Skyfield: `(earth + wgs84.latlon(...)).at(t).observe(stars).apparent().altaz()`.
- HYG v4.1 provides 8,870 catalogue entries with HIP identifiers and magnitude ≤6.5. The bundled subset supplies J2000 RA/Dec, without proper motion, parallax or radial velocity.
- Skyfield supplies precession/nutation and apparent-direction corrections; atmospheric refraction is disabled. These are illustrative catalogue positions, not a precision telescope-pointing solution.
- Supported dates are **2000–2050**, inside the bundled DE421 range. Built-in time-scale data avoids downloads; future leap seconds and Earth-rotation predictions are only as current as the bundled Skyfield tables.
- In the Python SVG output, the circle's edge is the geometric horizon, centre is zenith, north is up and east is left. Android normal mode and AR use a perspective sky view instead. Neither models terrain, daylight, clouds or light pollution.
- Android dates use the observing location's time zone after the initial ISO time is converted to an instant. Android assumes elevation zero; the Python API supports elevation input.
- This version displays stars and eight original constellation/asterism guides, including a camera-and-compass AR overlay. It does not include planets or the Moon.
- The interface is English. Red mode changes the main chart and controls; Android dialogs retain their system theme. Search provides an accessible text alternative to individual map points.

## Validation

See [VALIDATION.md](VALIDATION.md) for this source split's build checks and the app's existing device validation. Run the shared engine tests from its repository directory with its desktop dependencies installed:

```sh
cd engine
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v
```

Java geometry and calendar tests are in `tests/java/`; the offline geographic time-zone test is `tests/TimeZoneBoundaryLookupTest.java`. Check native alignment after building:

```sh
python tools/check_native_alignment.py app/build/outputs/apk/debug/app-debug.apk
```

## Licenses

Original Android code, icon and constellation guides are **MIT**, see [LICENSE](LICENSE). The shared Python engine is MIT with separately licensed data. The HYG catalogue is **CC BY-SA 4.0**; the adapted time-zone database is **ODbL 1.0**. Keep their attribution and license terms when redistributing data. Full provenance is in [NOTICE.txt](NOTICE.txt), [licenses/](licenses/), and the in-app About menu. The time-zone database source and rebuild steps are in [vendor/timezones/README.md](vendor/timezones/README.md).

