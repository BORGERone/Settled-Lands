package dev.settledlands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import static net.minecraft.commands.Commands.*;

public final class ModCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,WorldEvents events) {
        dispatcher.register(literal("cleansing").requires(s->s.hasPermission(2))
            .then(literal("info").executes(c->{
                var s=c.getSource(); var p=s.getPlayerOrException(); var d=TerritoryData.get(p.serverLevel());
                long k=Cell.at(p.blockPosition()),now=ActivityClock.get(s.getServer()).seconds;
                s.sendSuccess(()->Component.literal(String.format(java.util.Locale.ROOT,
                    "Settled Lands | ячейка %s | зачистка %.2f/%d | закрепление %d/%d | обжитость %d/%d с | постоянная: %s | часы: %d с",
                    net.minecraft.core.BlockPos.of(k).toShortString(),d.score(k,now),Settings.TEMP_MAX.get(),d.kills(k),Settings.PERM_MAX.get(),d.habitation(k),Settings.HABITATION.get(),d.permanent.contains(k),now)),false); return 1;
            }))
            .then(literal("ritual").executes(c->{
                var ritual=dev.settledlands.ritual.RitualManager.state();
                c.getSource().sendSuccess(()->Component.literal(ritual==null?"Активного ритуала нет. Нужны zombieRituals=true, mobGriefing=true и зомби в ячейке освящённого баннера.":"Ритуал: "+(ritual.age()<40?"cult":"cultIDE")+" | до завершения "+Math.max(0,(140-ritual.age())/20.0)+" с | баннер "+ritual.target().toShortString()+" | "+ritual.level().dimension().location()),false);return ritual==null?0:1;
            }))
            .then(literal("version").executes(c->{c.getSource().sendSuccess(()->Diagnostics.report(c.getSource().getServer()),false);return 1;}))
            .then(literal("ward").executes(c->{
                var s=c.getSource(); long key=Cell.at(net.minecraft.core.BlockPos.containing(s.getPosition()));
                int count=Sanctity.count(s.getLevel(),key);
                int fire=Sanctity.countFire(s.getLevel(),key);
                s.sendSuccess(()->Component.literal("Святость I: "+(count>0?"активна":"нет")+" | баннеров: "+count+" (из них II с горением: "+fire+") | ячейка "+net.minecraft.core.BlockPos.of(key).toShortString()),false);return count;
            }))
            .then(literal("stats").executes(c->{
                var d=TerritoryData.get(c.getSource().getLevel());
                c.getSource().sendSuccess(()->Component.literal("Измерение: изменяемых ячеек "+d.zones.size()+", постоянных "+d.permanent.size()+", бытовых блоков "+d.blockCount()+", отклонено спавнов с запуска "+d.deniedSpawns),false); return 1;
            }))
            .then(literal("debug")
                .then(literal("on").executes(c->debug(c.getSource(),events,32)))
                .then(literal("off").executes(c->{var p=c.getSource().getPlayerOrException();events.debugging.remove(p.getUUID());DebugPayload.clear(p);return 1;}))
                .then(literal("radius").then(argument("blocks",IntegerArgumentType.integer(16,64)).executes(c->debug(c.getSource(),events,IntegerArgumentType.getInteger(c,"blocks"))))))
            .then(literal("set").then(argument("temporary",IntegerArgumentType.integer(0,100000)).then(argument("progress",IntegerArgumentType.integer(0,1000000)).executes(c->{
                var p=c.getSource().getPlayerOrException();var d=TerritoryData.get(p.serverLevel());
                d.force(Cell.at(p.blockPosition()),Math.min(Settings.TEMP_MAX.get(),IntegerArgumentType.getInteger(c,"temporary")),Math.min(Settings.PERM_MAX.get(),IntegerArgumentType.getInteger(c,"progress")),false,ActivityClock.get(p.server).seconds);
                c.getSource().sendSuccess(()->Component.literal("Прогресс текущей ячейки изменён."),true);return 1;
            }))))
            .then(literal("permanent").then(literal("confirm").executes(c->{
                var p=c.getSource().getPlayerOrException();TerritoryData.get(p.serverLevel()).force(Cell.at(p.blockPosition()),0,0,true,ActivityClock.get(p.server).seconds);
                c.getSource().sendSuccess(()->Component.literal("Текущая ячейка закреплена навсегда."),true);return 1;
            })))
            .then(literal("reset").then(literal("confirm").executes(c->{
                var p=c.getSource().getPlayerOrException();TerritoryData.get(p.serverLevel()).reset(Cell.at(p.blockPosition()));
                c.getSource().sendSuccess(()->Component.literal("Зачистка и закрепление текущей ячейки сброшены. Обустройство сохранено."),true);return 1;
            })))
            .then(literal("register").executes(c->{
                var p=c.getSource().getPlayerOrException(); HitResult hit=p.pick(8,0,false);
                if(!(hit instanceof BlockHitResult b)||hit.getType()!=HitResult.Type.BLOCK)return 0;
                int category=Household.category(p.serverLevel().getBlockState(b.getBlockPos()));
                if(category==0) { c.getSource().sendFailure(Component.literal("Посмотри на кровать, сундук, бочку, верстак, печь или костёр."));return 0; }
                TerritoryData.get(p.serverLevel()).register(b.getBlockPos(),category,ActivityClock.get(p.server).seconds);
                c.getSource().sendSuccess(()->Component.literal("Бытовой блок зарегистрирован. Команда подходит для старых построек."),true);return 1;
            }))
            .then(literal("origin").then(argument("entity",EntityArgument.entity()).executes(c->{
                var entity=EntityArgument.getEntity(c,"entity");var tag=entity.getPersistentData();
                c.getSource().sendSuccess(()->Component.literal(tag.contains("settledlands_origin")?tag.getCompound("settledlands_origin").toString():"Нет записанного происхождения."),false);return 1;
            })))
        );
    }
    private static int debug(CommandSourceStack s,WorldEvents events,int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=s.getPlayerOrException();
        if(!DebugPayload.available(p)) { s.sendFailure(Component.literal("Для подсветки установи этот же JAR на клиент. Текстовые команды доступны без него."));return 0; }
        events.debugging.put(p.getUUID(),radius);DebugPayload.send(p,radius,ActivityClock.get(s.getServer()).seconds);
        s.sendSuccess(()->Component.literal("Подсветка включена: "+radius+" блоков, максимум 512 ячеек. T — зачистка, P — закрепление, H — обжитость."),false);return 1;
    }
    private ModCommands() {}
}
