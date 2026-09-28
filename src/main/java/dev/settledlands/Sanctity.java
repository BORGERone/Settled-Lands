package dev.settledlands;

import java.util.IdentityHashMap;
import java.util.Map;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Only an index of loaded sources lives in RAM. The authoritative enchantment is the vanilla
 * ENCHANTMENTS component on the banner block entity, saved by Minecraft itself.
 * All index access is server-thread-only. Each queried source is verified against the real BE;
 * therefore even removal by commands cannot leave active ghost protection.
 */
public final class Sanctity {
    public static final ResourceKey<Enchantment> KEY=ResourceKey.create(Registries.ENCHANTMENT,ResourceLocation.fromNamespaceAndPath(SettledLands.ID,"sanctity"));
    private static final Map<ServerLevel,Long2ObjectOpenHashMap<LongOpenHashSet>> INDEX=new IdentityHashMap<>();
    public static boolean holy(BannerBlockEntity banner) {
        ItemEnchantments enchantments=banner.components().getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
        for(var entry:enchantments.entrySet())if(entry.getIntValue()>0 && entry.getKey().is(KEY))return true;
        return false;
    }
    /** Highest valid level on this banner; 0 when not enchanted. */
    public static int level(BannerBlockEntity banner) {
        ItemEnchantments enchantments=banner.components().getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
        for(var entry:enchantments.entrySet())if(entry.getKey().is(KEY))return Math.max(0,entry.getIntValue());
        return 0;
    }
    /** Undead standing in a level II banner's own cell. Fire-immune undead and wet entities are skipped. */
    public static boolean inflammable(LivingEntity entity) {
        return entity.isAlive() && !entity.isSpectator() && !entity.fireImmune() && !entity.isInWaterRainOrBubble()
            && entity.getType().is(EntityTypeTags.UNDEAD);
    }
    public static void changed(BannerBlockEntity banner) {
        if(!(banner.getLevel() instanceof ServerLevel level))return;
        if(!level.getServer().isSameThread()) { level.getServer().execute(()->changed(banner));return; }
        long key=Cell.at(banner.getBlockPos());
        var cells=INDEX.computeIfAbsent(level,l->new Long2ObjectOpenHashMap<>());
        if(holy(banner) && !banner.isRemoved())cells.computeIfAbsent(key,k->new LongOpenHashSet()).add(banner.getBlockPos().asLong());
        else {
            var positions=cells.get(key);
            if(positions!=null) { positions.remove(banner.getBlockPos().asLong());if(positions.isEmpty())cells.remove(key); }
        }
    }
    /** Cell width == chunk width: verifying a source never needs a neighbouring chunk. */
    public static int count(ServerLevel level,long key) {
        var cells=INDEX.get(level);if(cells==null)return 0;
        var positions=cells.get(key);if(positions==null||!level.hasChunkAt(Cell.min(key)))return 0;
        int count=0;var it=positions.iterator();
        while(it.hasNext()) {
            BlockEntity be=level.getBlockEntity(BlockPos.of(it.nextLong()));
            if(be instanceof BannerBlockEntity banner && !banner.isRemoved() && holy(banner))count++;
            else it.remove();
        }
        if(positions.isEmpty())cells.remove(key);
        return count;
    }
    /** Level II banners in one cell; verified against real block entities, same as {@link #count}. */
    public static int countFire(ServerLevel level,long key) {
        var cells=INDEX.get(level);if(cells==null)return 0;
        var positions=cells.get(key);if(positions==null||!level.hasChunkAt(Cell.min(key)))return 0;
        int count=0;
        for(long packed:positions)if(level.getBlockEntity(BlockPos.of(packed)) instanceof BannerBlockEntity banner && !banner.isRemoved() && SanctityFire.burns(level(banner)))count++;
        return count;
    }
    private static final LongOpenHashSet BURN_CELLS=new LongOpenHashSet();
    /** No per-mob tasks: a few times per second we walk the small index of holy banners,
     *  collect the cells holding a level II banner and look at entities only in those cells. */
    @SubscribeEvent public void burning(ServerTickEvent.Post event) {
        if(!Settings.SANCTITY_FIRE.get()||event.getServer().getTickCount()%5!=0)return;
        for(ServerLevel level:event.getServer().getAllLevels())burnLevel(level);
    }
    /** Also called directly by the optional smoke suite. */
    public static void burnLevel(ServerLevel level) {
        {
            var cells=INDEX.get(level);if(cells==null||cells.isEmpty())return;
            BURN_CELLS.clear();
            for(var cell:cells.long2ObjectEntrySet())
                for(long packed:cell.getValue())
                    if(level.getBlockEntity(BlockPos.of(packed)) instanceof BannerBlockEntity banner && !banner.isRemoved() && SanctityFire.burns(level(banner))) { BURN_CELLS.add(cell.getLongKey());break; }
            for(long key:BURN_CELLS) {
                BlockPos min=Cell.min(key);
                for(LivingEntity entity:level.getEntitiesOfClass(LivingEntity.class,new AABB(min.getX(),min.getY(),min.getZ(),min.getX()+16,min.getY()+8,min.getZ()+16),Sanctity::inflammable)) {
                    if(Cell.at(entity.blockPosition())!=key)continue;
                    if(SanctityFire.extinguished(entity.getRemainingFireTicks()))entity.setRemainingFireTicks(SanctityFire.FIRE_TICKS);
                }
            }
        }
    }
    /** Same-cell lookup only. No block scan, no pathfinding, no chunk tickets. */
    public static BlockPos ritualTarget(ServerLevel level,BlockPos from) {
        long cell=Cell.at(from);count(level,cell);var cells=INDEX.get(level);if(cells==null)return null;
        var positions=cells.get(cell);if(positions==null)return null;
        BlockPos best=null;double distance=Double.MAX_VALUE;
        for(long packed:positions) {
            BlockPos pos=BlockPos.of(packed);
            if(level.getBlockEntity(pos) instanceof BannerBlockEntity banner && !dev.settledlands.ritual.RitualWard.immune(banner,level.getGameTime())) {
                double d=pos.distSqr(from);if(d<distance) {best=pos;distance=d;}
            }
        }
        return best;
    }
    public static void copyEnchantments(BannerBlockEntity banner,ItemStack drop) {
        if(!(drop.getItem() instanceof BannerItem) || !holy(banner))return;
        ItemEnchantments original=banner.components().getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
        // Modify the vanilla drop, not replace it: patterns, dye colour, custom name and count survive.
        EnchantmentHelper.updateEnchantments(drop,mutable->{
            for(var entry:original.entrySet())mutable.upgrade(entry.getKey(),entry.getIntValue());
        });
    }
    @SubscribeEvent
    public void loaded(ChunkEvent.Load e) {
        if(!(e.getLevel() instanceof ServerLevel level)||!(e.getChunk() instanceof LevelChunk chunk))return;
        Runnable action=()->{
            removeChunk(level,chunk.getPos());
            for(BlockEntity be:chunk.getBlockEntities().values())if(be instanceof BannerBlockEntity banner)changed(banner);
        };
        if(level.getServer().isSameThread())action.run();else level.getServer().execute(action);
    }
    private static void removeChunk(ServerLevel level,ChunkPos pos) {
        var cells=INDEX.get(level);if(cells==null)return;
        for(int y=Math.floorDiv(level.getMinBuildHeight(),8);y<=Math.floorDiv(level.getMaxBuildHeight()-1,8);y++)cells.remove(Cell.key(pos.x,y,pos.z));
    }
    @SubscribeEvent
    public void unloaded(ChunkEvent.Unload e) {
        if(e.getLevel() instanceof ServerLevel level) {
            ChunkPos pos=e.getChunk().getPos();
            if(level.getServer().isSameThread())removeChunk(level,pos);else level.getServer().execute(()->removeChunk(level,pos));
        }
    }
    @SubscribeEvent public void levelUnloaded(LevelEvent.Unload e) { if(e.getLevel() instanceof ServerLevel level)INDEX.remove(level); }
    @SubscribeEvent public void stopped(ServerStoppedEvent e) { INDEX.clear(); }
}
