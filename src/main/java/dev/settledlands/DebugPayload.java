package dev.settledlands;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public record DebugPayload(String dimension,int tempMax,int permanentMax,int habitationMax,List<Entry> cells) implements CustomPacketPayload {
    public record Entry(long key,float score,int kills,int habitation,boolean permanent,int banners,int fireBanners) {}
    public static final int MAX_ENTRIES=512;
    public static final Type<DebugPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SettledLands.ID,"debug"));
    /** Installed only by the physical client; common code never links client renderer classes. */
    public static Consumer<DebugPayload> receiver=p->{};
    public static final StreamCodec<RegistryFriendlyByteBuf,DebugPayload> CODEC=StreamCodec.of(DebugPayload::encode,DebugPayload::decode);
    private static void encode(RegistryFriendlyByteBuf b,DebugPayload p) {
        b.writeUtf(p.dimension,128); b.writeVarInt(p.tempMax); b.writeVarInt(p.permanentMax); b.writeVarInt(p.habitationMax);
        b.writeVarInt(p.cells.size());
        for(Entry c:p.cells) { b.writeLong(c.key); b.writeFloat(c.score); b.writeVarInt(c.kills); b.writeVarInt(c.habitation); b.writeBoolean(c.permanent); b.writeVarInt(c.banners); b.writeVarInt(c.fireBanners); }
    }
    private static DebugPayload decode(RegistryFriendlyByteBuf b) {
        String dim=b.readUtf(128); int temp=b.readVarInt(),perm=b.readVarInt(),hab=b.readVarInt(),size=b.readVarInt();
        if(size<0||size>MAX_ENTRIES||temp<1||perm<1||hab<1)throw new IllegalArgumentException("Invalid Settled Lands debug payload");
        List<Entry> list=new ArrayList<>(size);
        for(int i=0;i<size;i++)list.add(new Entry(b.readLong(),b.readFloat(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readVarInt()));
        return new DebugPayload(dim,temp,perm,hab,List.copyOf(list));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("3").optional().playToClient(TYPE,CODEC,(payload,context)->receiver.accept(payload));
    }
    public static boolean available(ServerPlayer p) { return NetworkRegistry.hasChannel(p.connection,TYPE.id()); }
    public static void clear(ServerPlayer p) {
        if(available(p))PacketDistributor.sendToPlayer(p,new DebugPayload(p.serverLevel().dimension().location().toString(),1,1,1,List.of()));
    }
    public static void send(ServerPlayer p,int radius,long now) {
        if(!available(p))return;
        TerritoryData d=TerritoryData.get(p.serverLevel()); long center=Cell.at(p.blockPosition());
        int n=(radius+15)/16; List<Entry> list=new ArrayList<>();
        // Near-to-far bounded grid, not a scan of the dimension's saved cells.
        for(int ring=0;ring<=n;ring++)for(int dy=-1;dy<=1;dy++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
            long key=Cell.key(BlockPos.getX(center)+dx,BlockPos.getY(center)+dy,BlockPos.getZ(center)+dz);
            float score=(float)d.score(key,now); int kills=d.kills(key); int hab=(int)d.habitation(key); boolean perm=d.permanent.contains(key)&&Settings.PERMANENT.get();
            int banners=Sanctity.count(p.serverLevel(),key);
            // Show the player's empty cell as a reference, but don't send all empty space.
            if(score==0&&kills==0&&hab==0&&banners==0&&key!=center)continue;
            list.add(new Entry(key,score,kills,hab,perm,banners,Sanctity.countFire(p.serverLevel(),key)));
            if(list.size()>=MAX_ENTRIES) { PacketDistributor.sendToPlayer(p,packet(p,list)); return; }
        }
        PacketDistributor.sendToPlayer(p,packet(p,list));
    }
    private static DebugPayload packet(ServerPlayer p,List<Entry> cells) {
        return new DebugPayload(p.serverLevel().dimension().location().toString(),Settings.TEMP_MAX.get(),Settings.PERM_MAX.get(),Settings.HABITATION.get(),cells);
    }
}
