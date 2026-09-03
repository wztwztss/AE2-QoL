package com.wztwzt.ae2_qof.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.wztwzt.ae2_qof.client.ClientState;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class WirelessHighlightRenderer {

    public static final WirelessHighlightRenderer INSTANCE = new WirelessHighlightRenderer();

    private static final int[][] HATCH_COLORS = {
        { 255, 160, 0 },
        { 0, 160, 255 },
        { 180, 0, 255 },
        { 255, 255, 0 }
    };

    private int tickCount = 0;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!ClientState.highlightEnabled) return;
        if (ClientState.highlightPositions == null || ClientState.highlightPositions.isEmpty()) return;

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;
        World world = player.worldObj;
        if (world == null) return;

        int currentDim = world.provider.dimensionId;
        tickCount++;

        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

        float alpha = 0.80F + 0.20F * (float) Math.sin(tickCount * 0.08);

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glLineWidth(5.0F);

        for (int[] pos : ClientState.highlightPositions) {
            int dim = pos[0];
            int x = pos[1];
            int y = pos[2];
            int z = pos[3];
            int type = pos.length > 4 ? pos[4] : 0;

            if (dim != currentDim) continue;

            int[] color = HATCH_COLORS[type >= 0 && type < HATCH_COLORS.length ? type : 0];

            AxisAlignedBB box = AxisAlignedBB
                .getBoundingBox(x - 0.12, y - 0.12, z - 0.12, x + 1.12, y + 1.12, z + 1.12);

            drawBoundingBox(box, px, py, pz, color[0], color[1], color[2], alpha);
        }

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(1.0F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private void drawBoundingBox(AxisAlignedBB box, double px, double py, double pz, int r, int g, int b, float alpha) {
        Tessellator tes = Tessellator.instance;
        int a = (int) (alpha * 255);

        tes.startDrawing(GL11.GL_LINES);
        tes.setColorRGBA(r, g, b, a);

        tes.addVertex(box.minX - px, box.minY - py, box.minZ - pz);
        tes.addVertex(box.maxX - px, box.minY - py, box.minZ - pz);
        tes.addVertex(box.maxX - px, box.minY - py, box.minZ - pz);
        tes.addVertex(box.maxX - px, box.minY - py, box.maxZ - pz);
        tes.addVertex(box.maxX - px, box.minY - py, box.maxZ - pz);
        tes.addVertex(box.minX - px, box.minY - py, box.maxZ - pz);
        tes.addVertex(box.minX - px, box.minY - py, box.maxZ - pz);
        tes.addVertex(box.minX - px, box.minY - py, box.minZ - pz);

        tes.addVertex(box.minX - px, box.maxY - py, box.minZ - pz);
        tes.addVertex(box.maxX - px, box.maxY - py, box.minZ - pz);
        tes.addVertex(box.maxX - px, box.maxY - py, box.minZ - pz);
        tes.addVertex(box.maxX - px, box.maxY - py, box.maxZ - pz);
        tes.addVertex(box.maxX - px, box.maxY - py, box.maxZ - pz);
        tes.addVertex(box.minX - px, box.maxY - py, box.maxZ - pz);
        tes.addVertex(box.minX - px, box.maxY - py, box.maxZ - pz);
        tes.addVertex(box.minX - px, box.maxY - py, box.minZ - pz);

        tes.addVertex(box.minX - px, box.minY - py, box.minZ - pz);
        tes.addVertex(box.minX - px, box.maxY - py, box.minZ - pz);
        tes.addVertex(box.maxX - px, box.minY - py, box.minZ - pz);
        tes.addVertex(box.maxX - px, box.maxY - py, box.minZ - pz);
        tes.addVertex(box.maxX - px, box.minY - py, box.maxZ - pz);
        tes.addVertex(box.maxX - px, box.maxY - py, box.maxZ - pz);
        tes.addVertex(box.minX - px, box.minY - py, box.maxZ - pz);
        tes.addVertex(box.minX - px, box.maxY - py, box.maxZ - pz);

        tes.draw();
    }
}
