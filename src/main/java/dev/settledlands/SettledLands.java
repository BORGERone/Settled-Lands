package dev.settledlands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
@Mod(SettledLands.ID)
public final class SettledLands {
    public static final String ID = "settledlands";
    public SettledLands(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Settings.SPEC);
        NeoForge.EVENT_BUS.register(new WorldEvents());
        NeoForge.EVENT_BUS.register(new Sanctity());
        NeoForge.EVENT_BUS.register(new Diagnostics());
        NeoForge.EVENT_BUS.register(new AnvilBannerMerge());
        NeoForge.EVENT_BUS.register(new SanctityTooltip());
        bus.addListener(DebugPayload::register);
        NeoForge.EVENT_BUS.register(new dev.settledlands.ritual.RitualManager());
        bus.addListener(dev.settledlands.ritual.RitualPayload::register);
    }
}
