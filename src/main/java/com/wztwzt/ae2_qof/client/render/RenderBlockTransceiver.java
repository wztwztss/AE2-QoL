package com.wztwzt.ae2_qof.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.wireless.TileWirelessTransceiver;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;

public class RenderBlockTransceiver implements ISimpleBlockRenderingHandler {

    public static final RenderBlockTransceiver INSTANCE = new RenderBlockTransceiver();
    public static final int RENDER_ID = RenderingRegistry.getNextAvailableRenderId();

    private IIcon iconBase;
    private IIcon iconLight;
    private IIcon iconLightTop;
    private IIcon iconLightBottom;

    public void registerIcons(IIconRegister register) {
        iconBase = register.registerIcon(MyMod.MODID + ":wireless_transceiver");
        iconLight = register.registerIcon(MyMod.MODID + ":wireless_transceiver_light");
        iconLightTop = register.registerIcon(MyMod.MODID + ":wireless_transceiver_light_top");
        iconLightBottom = register.registerIcon(MyMod.MODID + ":wireless_transceiver_light_bottom");
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelID, RenderBlocks renderer) {
        Tessellator tessellator = Tessellator.instance;
        renderer.setRenderBoundsFromBlock(block);
        tessellator.startDrawingQuads();
        tessellator.setNormal(0.0F, 1.0F, 0.0F);
        renderer.renderFaceYPos(block, 0, 0, 0, iconBase);
        tessellator.setNormal(0.0F, -1.0F, 0.0F);
        renderer.renderFaceYNeg(block, 0, 0, 0, iconBase);
        tessellator.setNormal(0.0F, 0.0F, 1.0F);
        renderer.renderFaceZPos(block, 0, 0, 0, iconBase);
        tessellator.setNormal(0.0F, 0.0F, -1.0F);
        renderer.renderFaceZNeg(block, 0, 0, 0, iconBase);
        tessellator.setNormal(1.0F, 0.0F, 0.0F);
        renderer.renderFaceXPos(block, 0, 0, 0, iconBase);
        tessellator.setNormal(-1.0F, 0.0F, 0.0F);
        renderer.renderFaceXNeg(block, 0, 0, 0, iconBase);
        tessellator.draw();
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        if (iconBase == null) {
            return false;
        }

        renderer.setRenderBoundsFromBlock(block);

        IIcon sideIcon = iconBase;

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileWirelessTransceiver) {
            TileWirelessTransceiver twt = (TileWirelessTransceiver) te;
            if (twt.isConnected()) {
                sideIcon = iconLight;
            }
        }

        renderer.setOverrideBlockTexture(sideIcon);
        renderer.renderStandardBlock(block, x, y, z);
        renderer.clearOverrideBlockTexture();
        return true;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return RENDER_ID;
    }
}
