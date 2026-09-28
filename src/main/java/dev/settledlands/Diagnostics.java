package dev.settledlands;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports which enchantment data the running game actually loaded.
 * The most common installation problem is an old jar, a stray copy of the old datapack or a
 * world datapack overriding data/settledlands/enchantment/sanctity.json, which silently keeps
 * the maximum level at 1 even though the new jar is installed.
 */
public final class Diagnostics {
    private static final Logger LOG=LoggerFactory.getLogger("settledlands");
    public static final int EXPECTED_MAX_LEVEL=2;
    /** -1 means the enchantment is absent from the registry entirely. */
    public static int loadedMaxLevel(MinecraftServer server) {
        Holder<Enchantment> holder=server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(Sanctity.KEY).orElse(null);
        return holder==null?-1:holder.value().getMaxLevel();
    }
    public static String version() {
        var container=ModList.get().getModContainerById(SettledLands.ID);
        String version=container.map(c->c.getModInfo().getVersion().toString()).orElse("не загружен");
        var file=ModList.get().getModFileById(SettledLands.ID);
        String name=file==null?"файл неизвестен":file.getFile().getFileName();
        return version+" ("+name+")";
    }
    /** Full report for /cleansing version. */
    public static Component report(MinecraftServer server) {
        int level=loadedMaxLevel(server);
        String head="Settled Lands: версия мода "+version()+" | максимальный уровень Святости в реестре: "+level;
        if(level==EXPECTED_MAX_LEVEL)return Component.literal(head+" — всё в порядке.");
        return Component.literal(head+" — ОШИБКА. Ожидается "+EXPECTED_MAX_LEVEL+". "
            +"Значит данные зачарования подменяет что-то ещё: старый JAR рядом в mods, копия старого файла "
            +"в datapacks/ или в мире (папка datapacks), либо сторонний датапак с тем же путём. "
            +"Проверьте /datapack list и папки mods и datapacks.");
    }
    @SubscribeEvent public void started(ServerStartedEvent event) {
        int level=loadedMaxLevel(event.getServer());
        if(level==EXPECTED_MAX_LEVEL) {
            LOG.info("[settledlands] loaded {}; Sanctity max level = {} as expected",version(),level);
            return;
        }
        LOG.error("[settledlands] WARNING: loaded {} but the enchantment registry reports max level {} instead of {}.",version(),level,EXPECTED_MAX_LEVEL);
        LOG.error("[settledlands] Something else provides data/settledlands/enchantment/sanctity.json: an old Settled Lands jar, a copy of the old datapack, or a world datapack.");
        LOG.error("[settledlands] Run /cleansing version in game and /datapack list to see the active data packs.");
    }
}
