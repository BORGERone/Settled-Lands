package dev.settledlands.client;
import dev.settledlands.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
@EventBusSubscriber(modid=SettledLands.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) { event.enqueueWork(()->{DebugPayload.receiver=ClientDebug::receive;dev.settledlands.ritual.RitualPayload.receiver=RitualClient::receive;}); }
}
