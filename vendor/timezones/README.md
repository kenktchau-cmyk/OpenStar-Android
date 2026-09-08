# Offline observer time zones

OpenStar uses timezone-boundary-builder **2026c** comprehensive geographic
polygons. It does not ship the lossy `tz-lookup` grid. The geographic source is
OpenStreetMap-derived data under **ODbL 1.0**; the adapted database remains under
that license. OpenStar's new lookup code is MIT. Attribution and license are
included in app assets and this source distribution.

## API and behavior

`ObserverTimeZone.resolve(Context, double latitude, double longitude)` returns
an Android `java.time.ZoneId`. It uses a lazily loaded 23,527-byte index. Cells
covered completely by a source polygon return directly; border cells stream a
gzip payload from `assets/timezones/NNNN.tzb` and perform point-in-polygon checks
including holes. Lookup state is immutable and safe for concurrent calls.

Coordinate ranges are inclusive: latitude [-90,90], longitude [-180,180]. NaN,
infinities, and out-of-range values throw `IllegalArgumentException`. Missing or
invalid assets throw `IllegalStateException`. A valid geographic ID absent from
the Android device's time-zone rules throws `IllegalArgumentException`. Exact
legacy IANA aliases are tried for renamed IDs, such as Europe/Kyiv -> Europe/Kiev;
a different nearby geographic zone is never substituted. The caller must handle
these failures explicitly. Offset and DST rules are supplied by Android and may
be stale on devices whose system time-zone database has not been updated.

Points in overlapping source zones select the first ID in source order. This
includes places where communities use more than one timekeeping convention.
Points outside mapped territorial zones use the conventional 15-degree ocean
zones. `Etc/GMT+N` denotes a negative UTC offset by IANA convention. Coordinates
at +180 and -180 retain their respective sides of the date line.

The data preserves source polygon detail: clipping and rounding to six decimal
places introduce at most about 0.08 metre diagonal coordinate rounding. **No
boundary simplification is used.** Present-day geographic boundaries do not
encode historical changes to the area covered by a zone. The source itself can
contain errors or ambiguous boundaries.

## Source and reproducibility

- Release: https://github.com/evansiroky/timezone-boundary-builder/releases/tag/2026c
- Input: https://github.com/evansiroky/timezone-boundary-builder/releases/download/2026c/timezones.geojson.zip
- Input bytes: 51,283,605.
- Input SHA-256: `7d3f0c5a33b6acd891335c0ad5ba767736b6914cb1a1d68c71921c17ce358948`,
  checked against the digest in the official GitHub release API response.
- Data license source: https://raw.githubusercontent.com/evansiroky/timezone-boundary-builder/2026c/DATA_LICENSE
- Build dependencies only: Python, Shapely 2.1.2, ijson 3.4.0. These are **not**
  Android runtime dependencies. Runtime uses only the Android/Java standard APIs.

```powershell
python tools/build_timezone_data.py timezones.geojson.zip --output app/src/main/assets/timezones --fixtures source-fixtures.tsv
```

Always build into an empty directory when changing the data source. `index.tzi`
and the 1,679 `.tzb` files are the runtime database. Polygon payloads total
24,194,122 bytes; the largest uncompressed tile is 544,486 bytes. Gzip streaming
means the entire tile and dataset are not held in memory. The two stream buffers
total about 64 KiB per active query. Keep `tzi` and `tzb` in Android's
`androidResources.noCompress`. A `.gz` suffix must not be used: AAPT strips it and
auto-decompresses the payload, making it unavailable under its expected name.

Each packed polygon contains a bounding box, byte length, ring count and ring
coordinates. Coordinates are integer millionths of degrees; each ring's first
coordinate is absolute and later coordinates are deltas, encoded with zigzag
unsigned base-128 varints. The included Java decoder and Python builder fully
document the machine-readable format. Asset checksums and source metadata are
in `assets/timezones/SHA256SUMS.txt` and `provenance.json`.

## Verification

`tests/TimeZoneBoundaryLookupTest.java` exercises 28 known cities, including Hong
Kong/Shenzhen/Macau, Nepal's fractional offset, Arizona and Navajo DST regions,
Spain/Portugal, Chatham Islands, Samoa/American Samoa, and newer Ciudad Juarez and
Coyhaique zone names. Invalid inputs, poles, both date-line sides and ocean offset
signs also pass.

The builder generated 40,000 deterministic independent expected results using
Shapely against the **unclipped original** polygons: 20,000 global coordinates
plus 20,000 points near sampled boundary vertices. The pure Java implementation
returned **0 mismatches out of 40,000**. This validates this sample, not every
coordinate or the original boundary source's factual accuracy.

JDK 21, `-Xmx32m`, local Windows filesystem measurements: index initialization
17.452 ms; initial city queries median 0.355 ms, p95 2.041 ms, maximum 2.207 ms.
Across 40,000 uncached per-query file opens: median 0.070 ms, p95 1.080 ms,
p99 2.080 ms, maximum 8.955 ms. The OS file cache may be warm; these are not
physical Android timings. First/border lookups can be called on a worker thread
when a slow device needs to keep editing responsive.

No app build or emulator was driven by the data-helper agent. Parent integration
performs Android packaging and UI checks, including checking that compressed
`.tzb` entries retain their expected names and gzip signature.
