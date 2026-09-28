package dev.settledlands;

/** Pure Sanctity II rules, kept free of Minecraft types so they are unit-testable. */
public final class SanctityFire {
    /** Sanctity II adds burning; Sanctity I keeps the original protection only. */
    public static boolean burns(int level) {return level>=2;}
    /** A normal vanilla fire of six seconds. Refreshed only after it burns out, so the damage
     *  cadence stays one point per second instead of stacking with every refresh. */
    public static final int FIRE_TICKS=120;
    public static boolean extinguished(int remainingFireTicks) {return remainingFireTicks<=0;}
    private SanctityFire() {}
}
