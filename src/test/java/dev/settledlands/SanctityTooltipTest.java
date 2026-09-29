package dev.settledlands;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SanctityTooltipTest {
    @Test void levelKeysPointAtDescriptions() {
        assertEquals("enchantment.settledlands.sanctity.desc.1",SanctityTooltip.descKey(1));
        assertEquals("enchantment.settledlands.sanctity.desc.2",SanctityTooltip.descKey(2));
    }
    @Test void unknownLevelsHaveNoKey() {assertNull(SanctityTooltip.descKey(0));assertNull(SanctityTooltip.descKey(3));assertNull(SanctityTooltip.descKey(-1));}
}
