package dev.settledlands;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SanctityFireTest {
    @Test void onlySecondLevelBurns() {assertFalse(SanctityFire.burns(0));assertFalse(SanctityFire.burns(1));assertTrue(SanctityFire.burns(2));assertTrue(SanctityFire.burns(3));}
    @Test void burningIsSixVanillaSeconds() {assertEquals(120,SanctityFire.FIRE_TICKS);}
    @Test void fireIsNotStackedWhileStillBurning() {
        assertFalse(SanctityFire.extinguished(SanctityFire.FIRE_TICKS));
        assertFalse(SanctityFire.extinguished(1));
        assertTrue(SanctityFire.extinguished(0));
        assertTrue(SanctityFire.extinguished(-1));
    }
}
