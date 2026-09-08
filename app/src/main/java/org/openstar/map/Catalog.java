package org.openstar.map;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.chaquo.python.Python;
import org.json.*;
import java.time.Instant;

public final class Catalog {
    public static final class Star {
        public final int hip;
        public final String name, constellation;
        public final double ra, dec, magnitude;
        public double altitude=-90, azimuth;
        Star(String[] f) {
            hip=Integer.parseInt(f[0]); name=f[1]; ra=Double.parseDouble(f[2]);
            dec=Double.parseDouble(f[3]); magnitude=Double.parseDouble(f[4]); constellation=f[5];
        }
        public String title() { return name.isEmpty()?"HIP "+hip:name; }
    }
    public final List<Star> stars=new ArrayList<>();
    public final List<Star[]> lines=new ArrayList<>();
    private final Map<Integer,Star> index=new HashMap<>();
    private final String catalogPath;
    public Catalog(Context context) throws IOException {
        File file=new File(context.getFilesDir(),"stars.tsv");
        try(InputStream input=context.getAssets().open("stars.tsv");OutputStream output=new FileOutputStream(file)){
            byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
        }
        catalogPath=file.getAbsolutePath();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(context.getAssets().open("stars.tsv"),StandardCharsets.UTF_8))) {
            String row; while((row=reader.readLine())!=null) {
                Star star=new Star(row.split("\t",-1)); stars.add(star); index.put(star.hip,star);
            }
        }
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(context.getAssets().open("lines.tsv"),StandardCharsets.UTF_8))) {
            String row; while((row=reader.readLine())!=null) {
                String[] f=row.split("\t"); Star a=index.get(Integer.parseInt(f[0])),b=index.get(Integer.parseInt(f[1]));
                if(a==null||b==null) throw new IOException("Missing constellation endpoint");
                lines.add(new Star[]{a,b});
            }
        }
        stars.sort(Comparator.comparingDouble(s->s.magnitude));
    }
    public double[][] calculate(double latitude,double longitude,long time) {
        String json=Python.getInstance().getModule("StarMapGenerator").callAttr("positions_json",Instant.ofEpochMilli(time).toString(),latitude,longitude,catalogPath).toString();
        try{JSONArray positions=new JSONArray(json);
            double[][] result=new double[positions.length()][3];
            for(int i=0;i<positions.length();i++){JSONArray row=positions.getJSONArray(i);result[i]=new double[]{row.getInt(0),row.getDouble(1),row.getDouble(2)};}
            return result;
        }catch(JSONException e){throw new IllegalStateException("Invalid Python star map result",e);
        }
    }
    public void apply(double[][] positions){for(double[] row:positions){Star star=index.get((int)row[0]);if(star!=null){star.altitude=row[1];star.azimuth=row[2];}}}
}
