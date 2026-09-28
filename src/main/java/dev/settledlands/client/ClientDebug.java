package dev.settledlands.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid=SettledLands.ID,value=Dist.CLIENT)
public final class ClientDebug {
    private static DebugPayload latest;
    private static long received;
    public static void receive(DebugPayload packet) { latest=packet; received=System.nanoTime(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { latest=null; }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance(); DebugPayload data=latest;
        if(data==null || mc.level==null || mc.player==null || System.nanoTime()-received>5_000_000_000L
            || !mc.level.dimension().location().toString().equals(data.dimension()))return;
        PoseStack pose=e.getPoseStack(); Vec3 camera=e.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();
        pose.pushPose(); pose.translate(-camera.x,-camera.y,-camera.z);
        var lines=buffers.getBuffer(RenderType.lines());
        for(var cell:data.cells()) {
            BlockPos p=Cell.min(cell.key()); AABB box=new AABB(p.getX()+0.04,p.getY()+0.04,p.getZ()+0.04,p.getX()+15.96,p.getY()+7.96,p.getZ()+15.96);
            if(!e.getFrustum().isVisible(box))continue;
            float ratio=Math.clamp(cell.score()/data.tempMax(),0f,1f);
            float r=cell.permanent()?0.15f:1-ratio, g=cell.permanent()?0.8f:Math.max(0.2f,ratio),b=cell.permanent()?1f:0.15f;
            if(cell.banners()>0 && !cell.permanent()) { r=0.8f;g=0.3f;b=1f; }
            LevelRenderer.renderLineBox(pose,lines,box,r,g,b,0.8f);
        }
        pose.popPose(); buffers.endBatch(RenderType.lines());
        int labels=0;
        for(var cell:data.cells()) {
            BlockPos p=Cell.min(cell.key()); double x=p.getX()+8,y=p.getY()+4,z=p.getZ()+8;
            if(camera.distanceToSqr(x,y,z)>48*48 || labels>=100)continue;
            if(!e.getFrustum().isVisible(new AABB(p.getX(),p.getY(),p.getZ(),p.getX()+16,p.getY()+8,p.getZ()+16)))continue;
            labels++;
            String text=cell.permanent()?"PERMANENT":String.format(java.util.Locale.ROOT,"T %.0f/%d | P %d/%d | H %d%%",cell.score(),data.tempMax(),cell.kills(),data.permanentMax(),Math.min(100,100L*cell.habitation()/data.habitationMax()));
            if(cell.banners()>0)text+=" | Святость I x"+cell.banners();
            if(cell.fireBanners()>0)text+=" | Святость II (огонь) x"+cell.fireBanners();
            pose.pushPose(); pose.translate(x-camera.x,y-camera.y,z-camera.z);
            pose.mulPose(e.getCamera().rotation());pose.scale(0.025f,-0.025f,0.025f);
            mc.font.drawInBatch(text,-mc.font.width(text)/2f,0,cell.permanent()?0xFF55DDFF:0xFFFFFFFF,false,pose.last().pose(),buffers,Font.DisplayMode.SEE_THROUGH,0x88000000,LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }
}
