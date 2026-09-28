package dev.settledlands;
import java.lang.reflect.Method;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PersistenceTest {
    private TerritoryData roundTrip(TerritoryData original) throws Exception {
        CompoundTag serialized=original.save(new CompoundTag(),null);
        Method load=TerritoryData.class.getDeclaredMethod("load",CompoundTag.class,HolderLookup.Provider.class);
        load.setAccessible(true);return (TerritoryData)load.invoke(null,serialized,null);
    }
    @Test void progressAndPermanentFlagsSurvive() throws Exception {
        TerritoryData d=new TerritoryData();long key=Cell.key(-10,7,30);
        TerritoryData.Zone z=new TerritoryData.Zone();z.score=17.25;z.kills=139;z.updated=987654321L;d.zones.put(key,z);
        long permanent=Cell.key(1,-2,3);d.permanent.add(permanent);
        TerritoryData loaded=roundTrip(d);
        assertEquals(17.25,loaded.zones.get(key).score);
        assertEquals(139,loaded.zones.get(key).kills);
        assertEquals(987654321L,loaded.zones.get(key).updated);
        assertTrue(loaded.permanent.contains(permanent));
    }
    @Test void exhaustedTemporaryScoreDoesNotEraseLifetimeProgress() throws Exception {
        TerritoryData d=new TerritoryData();TerritoryData.Zone z=new TerritoryData.Zone();z.score=0;z.kills=80;
        d.zones.put(0L,z);assertEquals(80,roundTrip(d).zones.get(0L).kills);
    }
    @Test void permanentRecordWinsOverDuplicateMutableRecordOnLoad() throws Exception {
        TerritoryData d=new TerritoryData();d.zones.put(0L,new TerritoryData.Zone());d.permanent.add(0L);
        TerritoryData loaded=roundTrip(d);assertTrue(loaded.permanent.contains(0L));assertFalse(loaded.zones.containsKey(0L));
    }
}
