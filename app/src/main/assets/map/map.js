/* OpenStar location picker. Leaflet 1.9.4 and all executable code are bundled. */
(function () {
  'use strict';

  var MAX_LAT = 85;
  var currentStatus = '';
  var tilesStarted = false;
  var tileErrors = false;
  var outlineReady = false;
  var selectedLatitude = 0;
  var selectedLongitude = 0;

  function nativeCall(method, args) {
    var bridge = window.OpenStarNative;
    if (bridge && typeof bridge[method] === 'function') {
      try { bridge[method].apply(bridge, args || []); } catch (_) { /* Activity may have closed. */ }
    }
  }

  function status(message) {
    if (message !== currentStatus) {
      currentStatus = message;
      nativeCall('mapStatus', [message]);
    }
  }

  function wrapLongitude(longitude) {
    return ((longitude + 180) % 360 + 360) % 360 - 180;
  }

  function viewLatitude(latitude) {
    return Math.max(-MAX_LAT, Math.min(MAX_LAT, latitude));
  }

  var map = L.map('map', {
    minZoom: 1,
    maxZoom: 19,
    maxBounds: [[-MAX_LAT, -180], [MAX_LAT, 180]],
    maxBoundsViscosity: 1,
    touchZoom: true,
    doubleClickZoom: true,
    dragging: true,
    keyboard: true,
    zoomControl: true,
    attributionControl: true,
    bounceAtZoomLimits: false
  }).setView([0, 0], 2);

  // This local land layer remains visible beneath missing or unavailable street tiles.
  map.createPane('offlineLand').style.zIndex = '150';
  map.getPane('offlineLand').style.pointerEvents = 'none';
  map.attributionControl.setPrefix(false);
  map.attributionControl.addAttribution('Outline: <a href="https://www.naturalearthdata.com/" target="_blank" rel="noopener noreferrer">Natural Earth</a>');
  // Keep OSM credit visible even before the first tile, and during offline use.
  map.attributionControl.addAttribution('&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap contributors</a>');
  L.control.scale({ imperial: false, position: 'bottomleft' }).addTo(map);

  var tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    minZoom: 1,
    maxZoom: 19,
    noWrap: true,
    bounds: [[-85.05112878, -180], [85.05112878, 180]],
    keepBuffer: 0,
    updateWhenIdle: true,
    updateWhenZooming: false,
    detectRetina: false
  });
  tiles.on('loading', function () {
    tileErrors = false;
    status('Loading street map. World outline works offline.');
  });
  tiles.on('tileerror', function () {
    tileErrors = true;
    status('Street tiles unavailable. Use the offline world outline or enter coordinates.');
  });
  tiles.on('load', function () {
    if (tileErrors) {
      status('Street tiles unavailable. Use the offline world outline or enter coordinates.');
    } else if (Math.abs(selectedLatitude) > MAX_LAT) {
      status('Polar coordinate kept. This street map displays only 85°S to 85°N.');
    } else {
      status('Tap the map or drag the pin to choose a location.');
    }
  });

  var pin = L.marker([0, 0], {
    icon: L.divIcon({
      className: 'openstar-pin',
      html: '<span class="openstar-pin-head" aria-hidden="true"></span>',
      iconSize: [46, 50],
      iconAnchor: [23, 43]
    }),
    title: 'Selected observing location. Drag to move the pin.',
    keyboard: true,
    draggable: true,
    autoPan: true,
    autoPanPadding: [40, 40]
  }).addTo(map);
  pin.getElement().setAttribute('aria-label', 'Selected observing location. Drag the pin or edit the coordinate fields.');

  var accuracyCircle = L.circle([0, 0], {
    radius: 0,
    color: '#178ce5',
    fillColor: '#178ce5',
    fillOpacity: 0.12,
    weight: 2,
    interactive: false
  });

  function select(latitude, longitude) {
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return;
    selectedLatitude = Math.max(-90, Math.min(90, latitude));
    selectedLongitude = wrapLongitude(longitude);
    pin.setLatLng([viewLatitude(selectedLatitude), selectedLongitude]);
    if (map.hasLayer(accuracyCircle)) map.removeLayer(accuracyCircle);
    nativeCall('selected', [selectedLatitude, selectedLongitude]);
  }

  map.on('click', function (event) {
    select(event.latlng.lat, event.latlng.lng);
  });
  pin.on('dragend', function () {
    var point = pin.getLatLng();
    select(point.lat, point.lng);
  });

  window.OpenStarMap = {
    setPosition: function (latitude, longitude, accuracyMeters, zoom) {
      latitude = Number(latitude);
      longitude = Number(longitude);
      if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude) > 90) return;
      selectedLatitude = latitude;
      selectedLongitude = wrapLongitude(longitude);
      var point = [viewLatitude(latitude), selectedLongitude];
      var targetZoom = Number(zoom);
      if (!Number.isFinite(targetZoom)) targetZoom = map.getZoom();
      targetZoom = Math.max(1, Math.min(19, targetZoom));
      pin.setLatLng(point);
      if (map.hasLayer(accuracyCircle)) map.removeLayer(accuracyCircle);
      if (Number.isFinite(Number(accuracyMeters)) && Number(accuracyMeters) > 0 && Math.abs(latitude) <= MAX_LAT) {
        accuracyCircle.setLatLng(point).setRadius(Math.min(Number(accuracyMeters), 20000000)).addTo(map);
      }
      map.setView(point, targetZoom, { animate: false });
      if (!tilesStarted) {
        tilesStarted = true;
        tiles.addTo(map);
      }
      if (Math.abs(latitude) > MAX_LAT) status('Polar coordinate kept. This street map displays only 85°S to 85°N.');
      // No selected() callback here: only deliberate user actions alter native coordinates.
    }
  };

  fetch('land.geojson').then(function (response) {
    if (!response.ok) throw new Error('Outline unavailable');
    return response.json();
  }).then(function (geojson) {
    L.geoJSON(geojson, {
      pane: 'offlineLand',
      interactive: false,
      style: { color: '#728f97', weight: 1, fillColor: '#36535c', fillOpacity: 1 }
    }).addTo(map);
    outlineReady = true;
  }).catch(function () {
    status('World outline unavailable. You can still select or enter coordinates.');
  });

  window.addEventListener('offline', function () {
    status(outlineReady ? 'Offline. Use the world outline or enter coordinates.' : 'Offline. Enter coordinates or tap to choose a location.');
  });
  window.addEventListener('online', function () {
    if (tilesStarted) tiles.redraw();
  });
  window.addEventListener('resize', function () { map.invalidateSize({ pan: false }); });
  nativeCall('ready');
}());
