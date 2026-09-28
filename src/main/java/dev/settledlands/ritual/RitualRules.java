package dev.settledlands.ritual;
public final class RitualRules {
    public static final int INTRO=40, CHANNEL=100, TOTAL=INTRO+CHANNEL, NO_DROP=200, IMMUNE=400;
    public static boolean channeling(long age) {return age>=INTRO && age<TOTAL;}
    public static boolean finished(long age) {return age>=TOTAL;}
    public static boolean locked(long now,long until) {return now<until;}
    private RitualRules() {}
}
