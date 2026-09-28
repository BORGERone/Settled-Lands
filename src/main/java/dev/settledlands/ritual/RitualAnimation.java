package dev.settledlands.ritual;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Numeric tracks exported from the user's Blockbench 5 project, without replacing its keyframes. */
public final class RitualAnimation {
    public record Key(double time,double[] value,String easing) {}
    private static final Map<String,Map<String,List<Key>>> CLIPS=load();
    private static Map<String,Map<String,List<Key>>> load() {
        try(var input=RitualAnimation.class.getResourceAsStream("/assets/settledlands/animations/cult_tracks.json")) {
            if(input==null)throw new IllegalStateException("Missing ritual animation tracks");
            var json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String,Map<String,List<Key>>> clips=new HashMap<>();
            for(var clip:json.entrySet()) {
                Map<String,List<Key>> tracks=new HashMap<>();
                for(var track:clip.getValue().getAsJsonObject().getAsJsonObject("tracks").entrySet()) {
                    List<Key> keys=new ArrayList<>();
                    for(var frame:track.getValue().getAsJsonArray()) {
                        var f=frame.getAsJsonObject();var v=f.getAsJsonArray("value");
                        keys.add(new Key(f.get("time").getAsDouble(),new double[]{v.get(0).getAsDouble(),v.get(1).getAsDouble(),v.get(2).getAsDouble()},f.get("easing").getAsString()));
                    }
                    keys.sort(Comparator.comparingDouble(Key::time));tracks.put(track.getKey(),List.copyOf(keys));
                }
                clips.put(clip.getKey(),Map.copyOf(tracks));
            }
            return Map.copyOf(clips);
        } catch(IOException e) {throw new ExceptionInInitializerError(e);}
    }
    public static double[] sample(String bone,String channel,double ticks) {
        String clip=ticks<RitualRules.INTRO?"cult":"cultIDE";
        double seconds=clip.equals("cult")?Math.max(0,ticks)/20:((ticks-RitualRules.INTRO)/20)%1.0;
        var keys=CLIPS.get(clip).get(bone+"."+channel);
        if(keys==null||keys.isEmpty())return channel.equals("scale")?new double[]{1,1,1}:new double[3];
        return interpolate(keys,seconds);
    }
    public static double[] interpolate(List<Key> keys,double t) {
        if(t<=keys.getFirst().time)return keys.getFirst().value.clone();
        for(int i=1;i<keys.size();i++)if(t<=keys.get(i).time) {
            Key a=keys.get(i-1),b=keys.get(i);double alpha=ease(b.easing,(t-a.time)/(b.time-a.time));double[] v=new double[3];
            for(int c=0;c<3;c++)v[c]=a.value[c]+(b.value[c]-a.value[c])*alpha;
            return v;
        }
        return keys.getLast().value.clone();
    }
    /** Matches the GeckoLib Blockbench plugin's destination-key easing and default arguments. */
    public static double ease(String name,double t) {
        return switch(name) {
            case "linear" -> t;
            case "easeInBack" -> t*t*((1.70158+1)*t-1.70158);
            case "easeInExpo" -> Math.pow(2,10*(t-1));
            case "easeInElastic" -> 1-Math.pow(Math.cos(t*Math.PI/2),3)*Math.cos(t*Math.PI);
            case "easeInBounce" -> Math.min(Math.min(121.0/16*t*t,121.0/8*Math.pow(t-6.0/11,2)+.5),Math.min(121.0/4*Math.pow(t-9.0/11,2)+.75,121.0/2*Math.pow(t-10.5/11,2)+.875));
            default -> throw new IllegalArgumentException("Unsupported easing in imported track: "+name);
        };
    }
    private RitualAnimation() {}
}
