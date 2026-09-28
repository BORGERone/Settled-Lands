package dev.settledlands.ritual;
import java.util.UUID;
import java.util.function.Consumer;
import dev.settledlands.SettledLands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
public record RitualPayload(String dimension,UUID zombie,int entityId,BlockPos banner,int age,boolean active) implements CustomPacketPayload {
    public static final Type<RitualPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SettledLands.ID,"ritual"));
    public static Consumer<RitualPayload> receiver=p->{};
    public static final StreamCodec<RegistryFriendlyByteBuf,RitualPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeUtf(p.dimension,128);b.writeUUID(p.zombie);b.writeVarInt(p.entityId);b.writeBlockPos(p.banner);b.writeVarInt(p.age);b.writeBoolean(p.active);
    },b->new RitualPayload(b.readUtf(128),b.readUUID(),b.readVarInt(),b.readBlockPos(),b.readVarInt(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
    public static void register(RegisterPayloadHandlersEvent e) {e.registrar("1").optional().playToClient(TYPE,CODEC,(p,c)->receiver.accept(p));}
    public static void broadcast(RitualManager.State s,boolean active) {
        var packet=new RitualPayload(s.level().dimension().location().toString(),s.zombie().getUUID(),s.zombie().getId(),s.target(),s.age(),active);
        for(var p:s.level().players())if(p.distanceToSqr(s.zombie())<128*128 && NetworkRegistry.hasChannel(p.connection,TYPE.id()))PacketDistributor.sendToPlayer(p,packet);
    }
}
