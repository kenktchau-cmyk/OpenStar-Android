# Location picker assets

Fetched 2026-09-08. All executable map code is bundled in the APK; only visible
OpenStreetMap raster tiles are requested over HTTPS during map use.

- Leaflet stable 1.9.4: https://leafletjs.com/download.html
- API reference used: https://leafletjs.com/reference.html (explicitly 1.9.4).
- Context7 `/websites/leafletjs` documentation consulted for stable initialization.
- Distribution: `https://unpkg.com/leaflet@1.9.4/dist/` (`leaflet.js`, `leaflet.css`,
  and the five PNGs in `images/`). JS/CSS bytes are unchanged.
- Leaflet license: `https://raw.githubusercontent.com/Leaflet/Leaflet/v1.9.4/LICENSE`,
  saved in [licenses/Leaflet-1.9.4-BSD-2-Clause.txt](../licenses/Leaflet-1.9.4-BSD-2-Clause.txt).
- Verified JavaScript SHA-256 against the official published SRI:
  `20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=`.
- Verified CSS SHA-256 against the official published SRI:
  `p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=`.
- Offline land source is pinned to Natural Earth 5.1.2:
  https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/geojson/ne_110m_land.geojson
- Unmodified Natural Earth file SHA-256:
  `9e0729ee253ca7d7a5c4ae9395fb1902264c5377c52e224d13dd85010e2835d9`.
- Public-domain terms: https://www.naturalearthdata.com/about/terms-of-use/
  Summary saved in [licenses/Natural-Earth-public-domain.txt](../licenses/Natural-Earth-public-domain.txt).
- All vendored asset hashes are in `app/src/main/assets/map/SHA256SUMS-vendor.txt`.
- Map tiles: `https://tile.openstreetmap.org/{z}/{x}/{y}.png`. Visible attribution
  links to https://www.openstreetmap.org/copyright . No tile prefetch/bulk download:
  `keepBuffer: 0`, `updateWhenIdle: true`, `updateWhenZooming: false`, no retina
  doubling, and tiles are attached only after the native initial location arrives.
- Native WebView must retain default HTTP cache behavior, use an identifiable
  OpenStar User-Agent, and permit HTTPS tile image requests and attribution links
  safely (external pages must never load with the JavaScript bridge attached).
- Page uses `strict-origin-when-cross-origin` and restrictive CSP; local scripts
  only, tile images from the exact OSM host, and local GeoJSON fetch only.

Bridge: `OpenStarNative.ready()`, `selected(double latitude, double longitude)`,
and `mapStatus(String text)`. Native can call
`OpenStarMap.setPosition(latitude, longitude, accuracyMeters, zoom)`; this does
not emit `selected`. Positive accuracy adds a circle; 0 removes it. Coordinates
at the poles are retained by the native state, while the rendered marker/view
is limited to 85 degrees. User taps and marker drags wrap longitude to [-180,180).
