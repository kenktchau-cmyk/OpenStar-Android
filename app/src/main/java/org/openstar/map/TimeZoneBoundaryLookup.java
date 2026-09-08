package org.openstar.map;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/** Pure Java lookup against bundled timezone-boundary-builder 2026c polygons.
 * Data: ODbL-1.0. Algorithm: OpenStar's project license. No network is used.
 * The grid only shortcuts cells fully covered by a source polygon. Border
 * cells use all source vertices, rounded to six decimal places (~0.1 metre).
 */
public final class TimeZoneBoundaryLookup {
    public interface Source { InputStream open(String name) throws IOException; }
    private final Source source;
    private final int step, rows, columns;
    private final String[] zones;
    private final int[] cells;

    public TimeZoneBoundaryLookup(Source source) throws IOException {
        this.source = source;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(source.open("index.tzi")))) {
            requireMagic(input, "OSTZ1");
            step = input.readUnsignedShort();
            rows = input.readUnsignedShort();
            columns = input.readUnsignedShort();
            if (step != 4 || rows != 45 || columns != 90) throw new IOException("Unsupported time zone index dimensions");
            int count = input.readUnsignedShort();
            if (count < 1 || count > 1000) throw new IOException("Invalid time zone count");
            zones = new String[count];
            for (int i = 0; i < count; i++) {
                int length = input.readUnsignedShort();
                if (length < 1 || length > 100) throw new IOException("Invalid time zone ID length");
                byte[] bytes = new byte[length];
                input.readFully(bytes);
                zones[i] = new String(bytes, StandardCharsets.UTF_8);
            }
            cells = new int[rows * columns];
            for (int i = 0; i < cells.length; i++) {
                cells[i] = input.readInt();
                if (cells[i] >= count || cells[i] < -cells.length-1) throw new IOException("Invalid time zone index cell");
            }
        }
    }

    /** Returns an IANA ID. Overlapping zones use the first source zone ID.
     * Locations outside mapped territorial zones use standard 15-degree ocean
     * zones (Etc/GMT, whose +/- spelling is reversed by the IANA convention).
     */
    public String lookup(double latitude, double longitude) throws IOException {
        validate(latitude, longitude);
        int row = Math.min(rows - 1, (int)Math.floor((latitude + 90) / step));
        int col = Math.min(columns - 1, (int)Math.floor((longitude + 180) / step));
        int value = cells[row * columns + col];
        if (value >= 0) return zones[value];
        if (value == -1) return oceanZone(longitude);
        int tile = -value - 2;
        // AAPT treats a .gz suffix specially and strips/decompresses it, so use
        // .tzb for these gzip payloads and keep tzb in androidResources.noCompress.
        String name = String.format(Locale.US, "%04d.tzb", tile);
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(source.open(name), 32768), 32768))) {
            requireMagic(input, "OSTP1");
            int groups = input.readUnsignedShort();
            if (groups > zones.length) throw new IOException("Invalid polygon groups");
            double x = longitude * 1_000_000, y = latitude * 1_000_000;
            for (int group = 0; group < groups; group++) {
                int zone = input.readUnsignedShort();
                if (zone >= zones.length) throw new IOException("Invalid polygon time zone");
                int count = readUnsigned(input);
                if (count > 100000) throw new IOException("Invalid polygon count");
                for (int polygon = 0; polygon < count; polygon++) {
                    int minX = input.readInt(), minY = input.readInt();
                    int maxX = input.readInt(), maxY = input.readInt();
                    int length = input.readInt();
                    if (length < 0 || length > 2000000) throw new IOException("Invalid polygon size");
                    if (x < minX || x > maxX || y < minY || y > maxY) {
                        skipFully(input, length);
                    } else if (contains(input, x, y)) {
                        return zones[zone];
                    }
                }
            }
        }
        return oceanZone(longitude);
    }

    public static void validate(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("Latitude must be -90 to 90 and longitude -180 to 180 degrees");
    }

    private static boolean contains(DataInputStream input, double x, double y) throws IOException {
        int count = readUnsigned(input);
        if (count < 1 || count > 100000) throw new IOException("Invalid ring count");
        boolean exterior = false, inHole = false, boundary = false;
        for (int ring = 0; ring < count; ring++) {
            int points = readUnsigned(input);
            if (points < 4 || points > 1000000) throw new IOException("Invalid ring point count");
            int px = 0, py = 0;
            boolean inside = false, onBoundary = false;
            for (int i = 0; i < points; i++) {
                int nx = px + readSigned(input), ny = py + readSigned(input);
                if (i > 0) {
                    double cross = (x-px)*(ny-py) - (y-py)*(nx-px);
                    if (Math.abs(cross) < 1e-5 && x >= Math.min(px,nx) && x <= Math.max(px,nx) && y >= Math.min(py,ny) && y <= Math.max(py,ny)) onBoundary = true;
                    if ((py > y) != (ny > y) && x < px + (y-py)*(nx-px)/(double)(ny-py)) inside = !inside;
                }
                px = nx;
                py = ny;
            }
            if (ring == 0) exterior = inside || onBoundary;
            else if (inside && !onBoundary) inHole = true;
            boundary |= onBoundary;
        }
        return exterior && (boundary || !inHole);
    }

    private static int readUnsigned(DataInputStream input) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int next = input.readUnsignedByte();
            if (shift == 28 && (next & 0xf0) != 0) throw new IOException("Invalid time zone integer");
            value |= (next & 0x7f) << shift;
            if ((next & 0x80) == 0) return value;
        }
        throw new IOException("Invalid time zone integer");
    }

    private static int readSigned(DataInputStream input) throws IOException {
        int value = readUnsigned(input);
        return (value >>> 1) ^ -(value & 1);
    }

    private static void skipFully(DataInputStream input, int count) throws IOException {
        while (count > 0) {
            int skipped = input.skipBytes(count);
            if (skipped == 0) {
                if (input.read() == -1) throw new EOFException("Truncated time zone polygon");
                skipped = 1;
            }
            count -= skipped;
        }
    }

    private static void requireMagic(DataInputStream input, String expected) throws IOException {
        for (int i = 0; i < expected.length(); i++) if (input.readUnsignedByte() != expected.charAt(i)) throw new IOException("Invalid bundled time zone data");
    }

    private static String oceanZone(double longitude) {
        int offset = Math.max(-12, Math.min(12, (int)Math.floor((longitude + 7.5) / 15)));
        return offset == 0 ? "Etc/GMT" : "Etc/GMT" + (offset > 0 ? "-" : "+") + Math.abs(offset);
    }
}
