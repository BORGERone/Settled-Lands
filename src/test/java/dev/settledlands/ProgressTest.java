package dev.settledlands;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProgressTest {
    @Test void graceProtectsFullSafety() { assertEquals(100,Progress.current(100,50,1850,1800,300)); }
    @Test void decaysAfterGrace() { assertEquals(99,Progress.current(100,50,2150,1800,300)); }
    @Test void noNegativeValues() { assertEquals(0,Progress.current(2,0,100000,1800,300)); }
    @Test void backwardsClockDoesNotAddProgress() { assertEquals(40,Progress.current(40,100,10,0,300)); }
    @Test void pausedClockPreservesValue() { assertEquals(60,Progress.current(60,500,500,0,1)); }
    @Test void killAppliesDecayBeforeAdding() { assertEquals(50,Progress.afterKill(50,0,300,0,300,100)); }
    @Test void killCapsAtThreshold() { assertEquals(100,Progress.afterKill(100,0,0,1800,300,100)); }
    @Test void fractionalDecay() { assertEquals(99.5,Progress.current(100,0,150,0,300)); }
    @Test void maintenanceRefreshCanKeepFullSafety() {
        double score=100;long updated=0;
        for(int i=1;i<=100;i++) {long now=i*1000L;score=Progress.afterKill(score,updated,now,1800,300,100);updated=now;}
        assertEquals(100,score);
    }
}
