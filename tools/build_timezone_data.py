"""Build compact exact-boundary Android time-zone assets from TBB GeoJSON.

Build-only dependencies: shapely==2.1.2, ijson==3.4.0. No dependency is added
to the Android runtime. Source polygons are clipped, never simplified; integer
coordinates retain six decimal places (at most ~0.08 m diagonal rounding).
"""
from pathlib import Path
import argparse
import gzip
import hashlib
import ijson
import json
import math
import random
import struct
import time
import zipfile
import shapely
from shapely.geometry import shape, box, Point, Polygon
from shapely import STRtree

STEP = 4
SCALE = 1_000_000
SOURCE_SHA256 = '7d3f0c5a33b6acd891335c0ad5ba767736b6914cb1a1d68c71921c17ce358948'


def unsigned(value):
    result = bytearray()
    while value >= 128:
        result.append((value & 127) | 128)
        value >>= 7
    result.append(value)
    return result


def signed(value):
    return unsigned((value << 1) ^ (value >> 31))


def polygon_bytes(polygon):
    rings = [polygon.exterior, *polygon.interiors]
    body = bytearray(unsigned(len(rings)))
    for ring in rings:
        coordinates = [(round(x * SCALE), round(y * SCALE)) for x, y in ring.coords]
        body.extend(unsigned(len(coordinates)))
        prior_x = prior_y = 0
        for x, y in coordinates:
            body.extend(signed(x - prior_x))
            body.extend(signed(y - prior_y))
            prior_x, prior_y = x, y
    minx, miny, maxx, maxy = polygon.bounds
    header = struct.pack('>iiiiI', math.floor(minx*SCALE)-1, math.floor(miny*SCALE)-1,
                         math.ceil(maxx*SCALE)+1, math.ceil(maxy*SCALE)+1, len(body))
    return header + body


def polygons(geometry):
    if geometry.is_empty:
        return []
    if geometry.geom_type == 'Polygon':
        return [geometry]
    if not hasattr(geometry, 'geoms'):
        return []
    return [p for part in geometry.geoms for p in polygons(part) if p.area > 0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--fixtures', type=Path)
    parser.add_argument('--simplify-degrees', type=float, default=0,
                        help='Optional topology-preserving tolerance; 0 retains full source detail.')
    args = parser.parse_args()
    digest = hashlib.sha256(args.archive.read_bytes()).hexdigest()
    if digest != SOURCE_SHA256:
        raise SystemExit('Unexpected source archive SHA-256; review release and update builder deliberately.')
    started = time.monotonic()
    zones, shapes, owners = [], [], []
    with zipfile.ZipFile(args.archive) as archive:
        for feature in ijson.items(archive.open('combined.json'), 'features.item', use_float=True):
            zone = feature['properties']['tzid']
            zid = len(zones)
            zones.append(zone)
            geometry = shape(feature['geometry'])
            if not geometry.is_valid:
                raise ValueError('Invalid source geometry: ' + zone)
            for polygon in polygons(geometry):
                shapes.append(polygon)
                owners.append(zid)
    print(f'Loaded {len(zones)} zones and {len(shapes)} polygons in {time.monotonic()-started:.1f}s', flush=True)
    original_shapes = shapes
    original_tree = STRtree(original_shapes)
    if args.simplify_degrees:
        shapes = [p.simplify(args.simplify_degrees, preserve_topology=True) for p in shapes]
    tree = STRtree(shapes)
    for geometry in shapes:
        shapely.prepare(geometry)
    output = args.output
    output.mkdir(parents=True, exist_ok=True)
    index = bytearray(b'OSTZ1') + struct.pack('>HHH', STEP, 180//STEP, 360//STEP)
    index.extend(struct.pack('>H', len(zones)))
    for zone in zones:
        value = zone.encode('utf-8')
        index.extend(struct.pack('>H', len(value)))
        index.extend(value)
    payloads = []
    count = total = biggest = 0
    for row in range(180//STEP):
        for col in range(360//STEP):
            cell = row*(360//STEP)+col
            left, bottom = -180+col*STEP, -90+row*STEP
            square = box(left, bottom, left+STEP, bottom+STEP)
            candidates = sorted(tree.query(square, predicate='intersects'), key=lambda n: owners[n])
            if not candidates:
                index.extend(struct.pack('>i', -1))
                continue
            if shapes[candidates[0]].covers(square):
                index.extend(struct.pack('>i', owners[candidates[0]]))
                continue
            groups = {}
            for candidate in candidates:
                clipped = shapes[candidate].intersection(square)
                pieces = polygons(clipped)
                if pieces:
                    groups.setdefault(owners[candidate], []).extend(pieces)
            if not groups:
                index.extend(struct.pack('>i', -1))
                continue
            data = bytearray(b'OSTP1') + struct.pack('>H', len(groups))
            for zid, pieces in groups.items():
                data.extend(struct.pack('>H', zid))
                data.extend(unsigned(len(pieces)))
                for piece in pieces:
                    data.extend(polygon_bytes(piece))
            packed = gzip.compress(data, compresslevel=9, mtime=0)
            # AAPT auto-expands assets ending in .gz; .tzb retains gzip bytes.
            (output/f'{cell:04d}.tzb').write_bytes(packed)
            index.extend(struct.pack('>i', -cell-2))
            count += 1
            total += len(packed)
            biggest = max(biggest, len(data))
        print(f'Row {row+1}/45: {count} boundary cells, {total/1048576:.2f} MiB compressed, {time.monotonic()-started:.1f}s', flush=True)
    (output/'index.tzi').write_bytes(index)
    manifest = {'source': 'timezone-boundary-builder 2026c', 'source_sha256': digest,
                'source_url': 'https://github.com/evansiroky/timezone-boundary-builder/releases/download/2026c/timezones.geojson.zip',
                'coordinate_scale': SCALE, 'cell_degrees': STEP, 'zones': len(zones),
                'boundary_cells': count, 'compressed_polygon_bytes': total,
                'largest_uncompressed_cell_bytes': biggest, 'index_bytes': len(index),
                'simplification': 'none' if not args.simplify_degrees else f'topology preserving tolerance {args.simplify_degrees} degrees',
                'license': 'ODbL-1.0'}
    (output/'provenance.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    (output/'SHA256SUMS.txt').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in sorted(output.iterdir()) if p.is_file() and p.name!='SHA256SUMS.txt'), encoding='utf-8')
    print(json.dumps(manifest, indent=2), flush=True)

    if args.fixtures:
        rng = random.Random(20260908)
        examples = [(rng.uniform(-89.99,89.99), rng.uniform(-179.99,179.99), 'global') for _ in range(20000)]
        # Exercise both sides of real boundaries, including tiny islands/enclaves.
        for _ in range(20000):
            p = rng.choice(original_shapes)
            x, y = p.exterior.coords[rng.randrange(len(p.exterior.coords))]
            lat = max(-89.999999, min(89.999999, y+rng.uniform(-0.001,0.001)))
            lon = max(-179.999999, min(179.999999, x+rng.uniform(-0.001,0.001)))
            examples.append((lat,lon,'boundary'))
        args.fixtures.parent.mkdir(parents=True, exist_ok=True)
        with args.fixtures.open('w',encoding='utf-8') as stream:
            stream.write('latitude\tlongitude\texpected\tkind\n')
            for lat,lon,kind in examples:
                hit = original_tree.query(Point(lon,lat), predicate='intersects')
                if len(hit):
                    expected = zones[min(owners[n] for n in hit)]
                else:
                    offset = min(12,max(-12,math.floor((lon+7.5)/15)))
                    expected = 'Etc/GMT' if offset == 0 else ('Etc/GMT-' if offset > 0 else 'Etc/GMT+')+str(abs(offset))
                stream.write(f'{lat:.9f}\t{lon:.9f}\t{expected}\t{kind}\n')
        print(f'Wrote {len(examples)} exact-source validation points', flush=True)


if __name__ == '__main__':
    main()
