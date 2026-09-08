import org.openstar.map.TimeZoneBoundaryLookup;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Run with the asset directory and optional TSV exact-source fixture path. */
public final class TimeZoneBoundaryLookupTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        long started = System.nanoTime();
        TimeZoneBoundaryLookup lookup = new TimeZoneBoundaryLookup(name -> Files.newInputStream(root.resolve(name)));
        System.out.printf(Locale.US, "Index initialization: %.3f ms%n", (System.nanoTime()-started)/1e6);
        Object[][] cities = {
            {22.3193,114.1694,"Asia/Hong_Kong"}, {22.5431,114.0579,"Asia/Shanghai"},
            {22.1987,113.5439,"Asia/Macau"}, {25.0330,121.5654,"Asia/Taipei"},
            {35.6762,139.6503,"Asia/Tokyo"}, {1.3521,103.8198,"Asia/Singapore"},
            {27.7172,85.3240,"Asia/Kathmandu"}, {28.6139,77.2090,"Asia/Kolkata"},
            {39.9042,116.4074,"Asia/Shanghai"}, {40.7128,-74.0060,"America/New_York"},
            {34.0522,-118.2437,"America/Los_Angeles"}, {33.4484,-112.0740,"America/Phoenix"},
            {35.6806,-109.0526,"America/Denver"}, {51.5074,-0.1278,"Europe/London"},
            {48.8566,2.3522,"Europe/Paris"}, {50.4501,30.5234,"Europe/Kyiv"},
            {38.8814,-6.9707,"Europe/Madrid"}, {38.8815,-7.1635,"Europe/Lisbon"},
            {-33.8688,151.2093,"Australia/Sydney"}, {-34.9285,138.6007,"Australia/Adelaide"},
            {-31.9583,115.8613,"Australia/Perth"}, {-27.4698,153.0251,"Australia/Brisbane"},
            {-43.9500,-176.5500,"Pacific/Chatham"}, {-13.8333,-171.7500,"Pacific/Apia"},
            {-14.2756,-170.7020,"Pacific/Pago_Pago"}, {21.3069,-157.8583,"Pacific/Honolulu"},
            {31.6904,-106.4245,"America/Ciudad_Juarez"}, {-45.5712,-72.0683,"America/Coyhaique"}
        };
        List<Long> times = new ArrayList<>();
        for (Object[] city : cities) {
            long before = System.nanoTime();
            String actual = lookup.lookup((Double)city[0], (Double)city[1]);
            times.add(System.nanoTime()-before);
            require(actual.equals(city[2]), Arrays.toString(city)+" got "+actual);
        }
        for (double[] coordinates : new double[][] {{Double.NaN,0},{0,Double.NaN},{Double.POSITIVE_INFINITY,0},{91,0},{-91,0},{0,181},{0,-181}}) {
            boolean failed = false;
            try { lookup.lookup(coordinates[0], coordinates[1]); } catch (IllegalArgumentException expected) { failed = true; }
            require(failed,"Accepted invalid coordinates: "+Arrays.toString(coordinates));
        }
        require(lookup.lookup(0,-30).equals("Etc/GMT+2"), "West ocean sign");
        require(lookup.lookup(0,-150).equals("Etc/GMT+10"), "Pacific ocean sign");
        require(lookup.lookup(90,0).equals("Etc/GMT"), "North pole");
        require(lookup.lookup(-90,0) != null, "South pole");
        require(lookup.lookup(0,180).equals("Etc/GMT-12"), "East date line");
        require(lookup.lookup(0,-180).equals("Etc/GMT+12"), "West date line");
        require(lookup.lookup(22.3193,114.1694).equals(lookup.lookup(22.3193,114.1694)), "Deterministic lookup");
        times.sort(Long::compare);
        System.out.printf(Locale.US,"PASS %d known cities, invalid inputs, poles, date line and ocean signs. First-use city file reads median %.3f ms, p95 %.3f ms, max %.3f ms.%n",
            cities.length,times.get(times.size()/2)/1e6,times.get((int)(times.size()*.95))/1e6,times.get(times.size()-1)/1e6);
        if (args.length > 1) {
            long fixtureStart = System.nanoTime();
            int count = 0, differences = 0;
            List<Long> measured = new ArrayList<>();
            try (BufferedReader input = Files.newBufferedReader(Path.of(args[1]))) {
                input.readLine();
                String line;
                while ((line = input.readLine()) != null) {
                    String[] values = line.split("\t");
                    long before = System.nanoTime();
                    String actual = lookup.lookup(Double.parseDouble(values[0]),Double.parseDouble(values[1]));
                    measured.add(System.nanoTime()-before);
                    if (!actual.equals(values[2])) {
                        if (differences < 20) System.out.println("DIFFERENCE "+line+" actual="+actual);
                        differences++;
                    }
                    count++;
                }
            }
            measured.sort(Long::compare);
            System.out.printf(Locale.US,"Source comparison: %d/%d mismatches (%.5f%%). %.2fs total. Queries median %.3f ms, p95 %.3f ms, p99 %.3f ms, max %.3f ms.%n",
                differences,count,100.0*differences/count,(System.nanoTime()-fixtureStart)/1e9,measured.get(count/2)/1e6,measured.get((int)(count*.95))/1e6,measured.get((int)(count*.99))/1e6,measured.get(count-1)/1e6);
            require(differences <= Math.max(1,count/2000), "More than 0.05% mismatches with exact source; investigate coordinate quantization or clipping");
        }
    }
    private static void require(boolean value,String reason) { if(!value) throw new AssertionError(reason); }
}
