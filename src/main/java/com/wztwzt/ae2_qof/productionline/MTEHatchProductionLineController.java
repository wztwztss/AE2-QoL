package com.wztwzt.ae2_qof.productionline;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 产线聚合器控制器仓
 * 提供GUI界面和配方激活功能
 */
public class MTEHatchProductionLineController extends MTEHatch {

    private String selectedRecipeId;
    private final List<String> activatedRecipes = new ArrayList<>();

    public MTEHatchProductionLineController(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0, new String[]{
            "产线聚合器控制器",
            "用于选择和激活产线配方",
            "需要插入产线所需的所有机器"
        });
    }

    public MTEHatchProductionLineController(String aName, int aTier, int aInvSlotCount, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aInvSlotCount, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchProductionLineController(mName, mTier, 0, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[]{aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE)};
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[]{aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE)};
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aMetaTileEntity, ForgeDirection aSide, ForgeDirection aFacing,
                                 int aColorIndex, boolean aActive, boolean aRedstone) {
        if (aSide == aFacing) {
            if (aActive) return getTexturesActive(Textures.BlockIcons.MACHINE_CASINGS[mTier][aColorIndex + 1]);
            return getTexturesInactive(Textures.BlockIcons.MACHINE_CASINGS[mTier][aColorIndex + 1]);
        }
        return new ITexture[]{Textures.BlockIcons.MACHINE_CASINGS[mTier][aColorIndex + 1]};
    }

    @Override
    public boolean isFacingValid(ForgeDirection aPos) {
        return true;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aTileEntity, EntityPlayer aPlayer, ForgeDirection aSide,
                                float aX, float aY, float aZ) {
        if (aTileEntity.isServerSide()) {
            sendMessage(aPlayer, "产线聚合器控制器 - 功能开发中");
        }
        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (selectedRecipeId != null) {
            aNBT.setString("SelectedRecipe", selectedRecipeId);
        }
        NBTTagList list = new NBTTagList();
        for (String recipeId : activatedRecipes) {
            list.appendTag(new NBTTagString(recipeId));
        }
        aNBT.setTag("ActivatedRecipes", list);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        selectedRecipeId = aNBT.getString("SelectedRecipe");
        activatedRecipes.clear();
        NBTTagList list = aNBT.getTagList("ActivatedRecipes", 8);
        for (int i = 0; i < list.tagCount(); i++) {
            activatedRecipes.add(list.getStringTagAt(i));
        }
    }

    public void selectRecipe(String recipeId) {
        this.selectedRecipeId = recipeId;
    }

    public boolean activateRecipe(String recipeId, MachineConsumptionManager manager) {
        if (activatedRecipes.contains(recipeId)) {
            return false;
        }
        activatedRecipes.add(recipeId);
        manager.markMachineConsumed(recipeId);
        return true;
    }

    public boolean isRecipeActivated(String recipeId) {
        return activatedRecipes.contains(recipeId);
    }

    public String getSelectedRecipeId() {
        return selectedRecipeId;
    }

    public List<String> getActivatedRecipes() {
        return activatedRecipes;
    }

    private void sendMessage(EntityPlayer aPlayer, String message) {
        if (aPlayer instanceof EntityPlayerMP) {
            ((EntityPlayerMP) aPlayer).addChatMessage(new ChatComponentText(message));
        }
    }

    @Override
    public String[] getDescription() {
        return new String[]{
            "产线聚合器控制器",
            "用于选择和激活产线配方",
            "需要插入产线所需的所有机器"
        };
    }

    @Override
    public boolean canExtractItem(int aIndex, ItemStack aStack, int aSide) {
        return false;
    }

    @Override
    public boolean canInsertItem(int aIndex, ItemStack aStack, int aSide) {
        return false;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int aSide) {
        return new int[0];
    }

    @Override
    public boolean isItemValidForSlot(int aIndex, ItemStack aStack) {
        return false;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isUseableByPlayer(EntityPlayer aPlayer) {
        return true;
    }

    @Override
    public void markDirty() {}

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public String getInventoryName() {
        return "Production Line Controller";
    }

    @Override
    public void setInventorySlotContents(int aIndex, ItemStack aStack) {}

    @Override
    public ItemStack getStackInSlotOnClosing(int aIndex) {
        return null;
    }

    @Override
    public ItemStack decrStackSize(int aIndex, int aAmount) {
        return null;
    }

    @Override
    public ItemStack getStackInSlot(int aIndex) {
        return null;
    }

    @Override
    public int getSizeInventory() {
        return 0;
    }
}
