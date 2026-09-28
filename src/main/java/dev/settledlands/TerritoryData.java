package dev.settledlands;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Main-thread-only, dimension-local sparse storage. Never loads chunks. */
public final class TerritoryData extends SavedData {
    public static final class Zone {
        public double score;
        public long updated;
        public int kills;
        public double current(long now) { return Progress.current(score,updated,now,Settings.GRACE.get(),Settings.DECAY.get()); }
    }
    public final Long2ObjectOpenHashMap<Zone> zones=new Long2ObjectOpenHashMap<>();
    public final LongOpenHashSet permanent=new LongOpenHashSet();
    private final Long2ByteOpenHashMap blocks=new Long2ByteOpenHashMap();
    private final Long2ObjectOpenHashMap<int[]> categories=new Long2ObjectOpenHashMap<>();
    private final Long2LongOpenHashMap occupied=new Long2LongOpenHashMap();
    private final LongLinkedOpenHashSet validation=new LongLinkedOpenHashSet();
    private long lastWarning=Long.MIN_VALUE;
    public long deniedSpawns;

    public static TerritoryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(TerritoryData::new,TerritoryData::load,null),"settledlands_territories");
    }
    public int blockCount() { return blocks.size(); }
    private void warn(long now) {
        if(lastWarning==Long.MIN_VALUE || now-lastWarning>=300) {
            LogUtils.getLogger().warn("Settled Lands storage limit reached: new records skipped; existing progress retained."); lastWarning=now;
        }
    }
    public boolean room(long now) {
        if(zones.size()+permanent.size()<Settings.MAX_CELLS.get()) return true;
        warn(now); return false;
    }
    public double score(long key,long now) {
        if(permanent.contains(key) && Settings.PERMANENT.get()) return Settings.TEMP_MAX.get();
        Zone z=zones.get(key); return z==null?0:Math.min(Settings.TEMP_MAX.get(),z.current(now));
    }
    public int kills(long key) {
        if(permanent.contains(key)) return Settings.PERM_MAX.get();
        Zone z=zones.get(key); return z==null?0:z.kills;
    }
    public void credit(long key,long now) {
        if(permanent.contains(key)) return;
        Zone z=zones.get(key);
        if(z==null) { if(!room(now))return; z=new Zone(); zones.put(key,z); }
        z.score=Progress.afterKill(z.score,z.updated,now,Settings.GRACE.get(),Settings.DECAY.get(),Settings.TEMP_MAX.get());
        z.updated=now; z.kills=Math.min(Settings.PERM_MAX.get(),z.kills+1);
        promote(key); setDirty();
    }
    private void promote(long key) {
        Zone z=zones.get(key);
        if(Settings.PERMANENT.get() && z!=null && z.kills>=Settings.PERM_MAX.get() && habitation(key)>=Settings.HABITATION.get()) {
            permanent.add(key); zones.remove(key); setDirty();
        }
    }
    public void reset(long key) { zones.remove(key); permanent.remove(key); setDirty(); }
    public void force(long key,double score,int kills,boolean forever,long now) {
        if(!zones.containsKey(key) && !permanent.contains(key) && !room(now))return;
        reset(key);
        if(forever) permanent.add(key);
        else { Zone z=new Zone(); z.score=score; z.updated=now; z.kills=kills; zones.put(key,z); promote(key); }
        setDirty();
    }
    private void index(long position,int category,int delta) {
        long cell=Cell.at(BlockPos.of(position));
        int[] counts=categories.computeIfAbsent(cell,k->new int[4]); counts[category-1]+=delta;
        if(counts[0]+counts[1]+counts[2]+counts[3]==0) { categories.remove(cell); occupied.remove(cell); }
    }
    public void register(BlockPos pos,int category,long now) {
        if(category==0)return;
        long key=pos.asLong();
        if(!blocks.containsKey(key) && blocks.size()>=Settings.MAX_BLOCKS.get()) { warn(now); return; }
        byte old=blocks.get(key); if(old==category)return;
        if(old!=0)index(key,old,-1);
        blocks.put(key,(byte)category); index(key,category,1); validation.add(key); setDirty();
    }
    public void unregister(BlockPos pos) {
        long key=pos.asLong(); byte old=blocks.remove(key);
        if(old!=0) { index(key,old,-1); validation.remove(key); setDirty(); }
    }
    /** Bounded round-robin verification catches explosions, pistons and changes made by other mods. */
    public void validate(ServerLevel level) {
        int budget=Math.min(16,validation.size());
        for(int i=0;i<budget;i++) {
            long key=validation.removeFirstLong(); BlockPos p=BlockPos.of(key);
            if(level.hasChunkAt(p) && Household.category(level.getBlockState(p))!=blocks.get(key)) unregister(p);
            else validation.add(key);
        }
    }
    public boolean household(long key) {
        if(!categories.containsKey(key))return false;
        int mask=0;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            int[] counts=categories.get(Cell.offset(key,dx,dz));
            if(counts!=null)for(int c=0;c<4;c++)if(counts[c]>0)mask|=1<<c;
        }
        return Integer.bitCount(mask)>=Settings.CATEGORIES.get();
    }
    public boolean within(long key,BlockPos pos,double radius) {
        if(BlockPos.getY(key)!=Math.floorDiv(pos.getY(),8))return false;
        BlockPos min=Cell.min(key); double dx=pos.getX()+0.5-(min.getX()+8),dz=pos.getZ()+0.5-(min.getZ()+8);
        return dx*dx+dz*dz<=radius*radius;
    }
    public Long nearestHousehold(BlockPos pos) {
        long cell=Cell.at(pos); Long best=null; double distance=Double.MAX_VALUE;
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
            long k=Cell.offset(cell,dx,dz);
            if(!within(k,pos,24)||!household(k))continue;
            BlockPos p=Cell.min(k); double d=pos.distToCenterSqr(p.getX()+8,p.getY()+4,p.getZ()+8);
            if(d<distance) { best=k; distance=d; }
        }
        return best;
    }
    public long habitation(long key) {
        long best=0;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            long k=Cell.offset(key,dx,dz); long seconds=occupied.get(k);
            if(seconds>best && household(k))best=seconds;
        }
        return best;
    }
    /** Called at most once per occupied anchor per sampled second, regardless of player count. */
    public void occupy(long anchor) {
        long before=occupied.get(anchor); long goal=Settings.HABITATION.get();
        if(before<goal) { occupied.put(anchor,before+1); setDirty(); }
        if(before+1>=goal)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)promote(Cell.offset(anchor,dx,dz));
    }
    private static TerritoryData load(CompoundTag tag,HolderLookup.Provider registries) {
        int version=tag.getInt("Version"); if(version!=1)throw new IllegalStateException("Unsupported Settled Lands data version: "+version);
        TerritoryData d=new TerritoryData();
        for(Tag t:tag.getList("Zones",Tag.TAG_COMPOUND)) {
            CompoundTag n=(CompoundTag)t; Zone z=new Zone(); z.score=n.getDouble("Score"); z.updated=n.getLong("Updated"); z.kills=n.getInt("Kills"); d.zones.put(n.getLong("Key"),z);
        }
        for(long k:tag.getLongArray("Permanent")) { d.permanent.add(k); d.zones.remove(k); }
        for(Tag t:tag.getList("Blocks",Tag.TAG_COMPOUND)) {
            CompoundTag n=(CompoundTag)t; long k=n.getLong("Pos"); byte c=n.getByte("Category");
            if(c<1||c>4)continue;
            d.blocks.put(k,c); d.index(k,c,1); d.validation.add(k);
        }
        for(Tag t:tag.getList("Occupied",Tag.TAG_COMPOUND)) {
            CompoundTag n=(CompoundTag)t; long k=n.getLong("Key"); if(d.categories.containsKey(k))d.occupied.put(k,n.getLong("Seconds"));
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putInt("Version",1); ListTag list=new ListTag();
        for(var e:zones.long2ObjectEntrySet()) {
            Zone z=e.getValue(); CompoundTag n=new CompoundTag(); n.putLong("Key",e.getLongKey()); n.putDouble("Score",z.score); n.putLong("Updated",z.updated); n.putInt("Kills",z.kills); list.add(n);
        }
        tag.put("Zones",list); tag.putLongArray("Permanent",permanent.toLongArray()); list=new ListTag();
        for(var e:blocks.long2ByteEntrySet()) { CompoundTag n=new CompoundTag(); n.putLong("Pos",e.getLongKey()); n.putByte("Category",e.getByteValue()); list.add(n); }
        tag.put("Blocks",list); list=new ListTag();
        for(var e:occupied.long2LongEntrySet()) { CompoundTag n=new CompoundTag(); n.putLong("Key",e.getLongKey()); n.putLong("Seconds",e.getLongValue()); list.add(n); }
        tag.put("Occupied",list); return tag;
    }
}
