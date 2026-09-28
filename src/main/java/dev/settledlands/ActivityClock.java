package dev.settledlands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
public final class ActivityClock extends SavedData {
    public long seconds;
    public static ActivityClock get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ActivityClock::new,ActivityClock::load,null),"settledlands_clock");
    }
    private static ActivityClock load(CompoundTag tag,HolderLookup.Provider registries) {
        ActivityClock c=new ActivityClock(); c.seconds=tag.getLong("Seconds"); return c;
    }
    public void advance() { seconds++; setDirty(); }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putInt("Version",1); tag.putLong("Seconds",seconds); return tag;
    }
}
