package dev.settledlands;
/** Pure arithmetic. Time unit: active server seconds. */
public final class Progress {
    public static double current(double score,long updated,long now,long grace,long secondsPerPoint) {
        long elapsed = Math.max(0L,now-updated);
        return Math.max(0.0, score-Math.max(0L,elapsed-grace)/(double)secondsPerPoint);
    }
    public static double afterKill(double score,long updated,long now,long grace,long secondsPerPoint,int cap) {
        return Math.min(cap,current(score,updated,now,grace,secondsPerPoint)+1.0);
    }
    private Progress() {}
}
