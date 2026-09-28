package dev.settledlands;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class WorldEvents {
    private static final String ORIGIN="settledlands_origin";
    private record Place(ResourceKey<Level> dimension,long cell) {}
    private record Visit(Place place,long since) {}
    private final Map<UUID,Visit> visits=new HashMap<>();
    public final Map<UUID,Integer> debugging=new HashMap<>();
    private final Map<EntityType<?>,Boolean> eligibility=new IdentityHashMap<>();

    private boolean eligible(EntityType<?> type) {
        return eligibility.computeIfAbsent(type,t->{
            String id=BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
            return !Settings.EXCLUDED.get().contains(id) && (t.getCategory()==MobCategory.MONSTER||Settings.INCLUDED.get().contains(id));
        });
    }
    private long now(ServerLevel level) { return ActivityClock.get(level.getServer()).seconds; }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void spawn(MobSpawnEvent.PositionCheck e) {
        if(e.getSpawnType()!=MobSpawnType.NATURAL || !eligible(e.getEntity().getType()) || e.getResult()==MobSpawnEvent.PositionCheck.Result.FAIL)return;
        ServerLevel level=e.getLevel().getLevel(); TerritoryData d=TerritoryData.get(level);
        long cell=Cell.at(e.getEntity().blockPosition());
        double chance=Sanctity.count(level,cell)>0 ? 1 : d.score(cell,now(level))/Settings.TEMP_MAX.get();
        if(chance>=1 || chance>0 && level.random.nextDouble()<chance) {
            e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL); d.deniedSpawns++;
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void origin(FinalizeSpawnEvent e) {
        if(!eligible(e.getEntity().getType()) || e.isSpawnCancelled())return;
        CompoundTag tag=new CompoundTag(); tag.putBoolean("Natural",e.getSpawnType()==MobSpawnType.NATURAL);
        tag.putLong("Cell",Cell.at(e.getEntity().blockPosition()));
        tag.putString("Dimension",e.getLevel().getLevel().dimension().location().toString());
        e.getEntity().getPersistentData().put(ORIGIN,tag);
    }
    @SubscribeEvent
    public void conversion(LivingConversionEvent.Post e) {
        if(e.getEntity().getPersistentData().contains(ORIGIN))
            e.getOutcome().getPersistentData().put(ORIGIN,e.getEntity().getPersistentData().getCompound(ORIGIN).copy());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void death(LivingDeathEvent e) {
        LivingEntity mob=e.getEntity();
        if(!(mob.level() instanceof ServerLevel level) || !eligible(mob.getType()))return;
        boolean playerCredit=e.getSource().getEntity() instanceof Player || mob.lastHurtByPlayerTime>0 && mob.lastHurtByPlayer!=null;
        if(!playerCredit)return;
        CompoundTag persistent=mob.getPersistentData();
        boolean known=persistent.contains(ORIGIN);
        CompoundTag origin=persistent.getCompound(ORIGIN);
        if(known?!origin.getBoolean("Natural"):!Settings.UNKNOWN.get())return;
        if(persistent.getBoolean("settledlands_credited"))return;
        persistent.putBoolean("settledlands_credited",true);
        long now=now(level), center=Cell.at(mob.blockPosition());
        TerritoryData d=TerritoryData.get(level); int radius=Settings.KILL_RADIUS.get();
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)d.credit(Cell.offset(center,x,z),now);
        if(known) {
            ResourceLocation id=ResourceLocation.tryParse(origin.getString("Dimension")); if(id==null)return;
            ResourceKey<Level> dimension=ResourceKey.create(Registries.DIMENSION,id);
            long source=origin.getLong("Cell");
            boolean duplicate=dimension.equals(level.dimension()) && BlockPos.getY(source)==BlockPos.getY(center)
                && Math.abs(BlockPos.getX(source)-BlockPos.getX(center))<=radius && Math.abs(BlockPos.getZ(source)-BlockPos.getZ(center))<=radius;
            if(!duplicate) { ServerLevel from=level.getServer().getLevel(dimension); if(from!=null)TerritoryData.get(from).credit(source,now); }
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void place(BlockEvent.EntityPlaceEvent e) {
        if(!(e.getEntity() instanceof ServerPlayer)||!(e.getLevel() instanceof ServerLevel level))return;
        TerritoryData d=TerritoryData.get(level); long t=now(level);
        if(e instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for(var snapshot:multi.getReplacedBlockSnapshots()) {
                d.unregister(snapshot.getPos()); d.register(snapshot.getPos(),Household.category(level.getBlockState(snapshot.getPos())),t);
            }
        } else { d.unregister(e.getPos()); d.register(e.getPos(),Household.category(e.getPlacedBlock()),t); }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void broken(BlockEvent.BreakEvent e) {
        if(e.getLevel() instanceof ServerLevel level)TerritoryData.get(level).unregister(e.getPos());
    }
    @SubscribeEvent
    public void tick(ServerTickEvent.Post e) {
        MinecraftServer server=e.getServer(); if(server.getTickCount()%20!=0)return;
        ActivityClock clock=ActivityClock.get(server);
        var players=server.getPlayerList().getPlayers();
        boolean active=players.stream().anyMatch(p->!p.isSpectator());
        if(active||!Settings.PAUSE_EMPTY.get())clock.advance();
        long now=clock.seconds; Set<UUID> present=new HashSet<>(); Set<Place> occupied=new HashSet<>();
        for(ServerPlayer p:players) {
            if(p.isSpectator()||!p.isAlive()) { visits.remove(p.getUUID()); continue; }
            present.add(p.getUUID()); ServerLevel level=p.serverLevel(); TerritoryData d=TerritoryData.get(level);
            Visit visit=visits.get(p.getUUID());
            boolean retain=visit!=null && visit.place.dimension.equals(level.dimension()) && d.household(visit.place.cell) && d.within(visit.place.cell,p.blockPosition(),28);
            if(!retain) {
                Long anchor=d.nearestHousehold(p.blockPosition());
                if(anchor==null) { visits.remove(p.getUUID()); continue; }
                visit=new Visit(new Place(level.dimension(),anchor),now); visits.put(p.getUUID(),visit);
            }
            if(now-visit.since>=Settings.VISIT_GRACE.get())occupied.add(visit.place);
        }
        visits.keySet().retainAll(present);
        for(Place place:occupied) { ServerLevel level=server.getLevel(place.dimension); if(level!=null)TerritoryData.get(level).occupy(place.cell); }
        for(ServerLevel level:server.getAllLevels())TerritoryData.get(level).validate(level);
        for(ServerPlayer p:players)if(debugging.containsKey(p.getUUID())) {
            if(!p.hasPermissions(2)) { debugging.remove(p.getUUID()); DebugPayload.clear(p); }
            else DebugPayload.send(p,debugging.get(p.getUUID()),now);
        }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e) { visits.remove(e.getEntity().getUUID()); debugging.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { visits.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e) { visits.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public void stopped(ServerStoppedEvent e) { visits.clear(); debugging.clear(); eligibility.clear(); }
    @SubscribeEvent public void commands(RegisterCommandsEvent e) { ModCommands.register(e.getDispatcher(),this); }
}
