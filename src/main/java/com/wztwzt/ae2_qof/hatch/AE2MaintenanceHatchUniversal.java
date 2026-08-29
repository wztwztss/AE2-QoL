package com.wztwzt.ae2_qof.hatch;

import static gregtech.api.enums.GTValues.V;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.SoundResource;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.common.misc.WirelessNetworkManager;

import com.wztwzt.ae2_qof.MyMod;

/**
 * 万能维护仓：维护绕过 + 无线能源 + 电路板并行映射。
 * <p>
 * 维护绕过由 {@code MixinMTEMultiBlockBase.shouldCheckMaintenance()} 全局实现，
 * 本仓室仅作为占位符放置于多方块结构中。
 * <p>
 * 无线能源：放置时绑定放置者 UUID，定期从全球无线电网拉取 EU 到本地存储。
 * 电路板并行映射：电路板槽读取 GT 电路板等级，并行数 = 4^level。
 */
public class AE2MaintenanceHatchUniversal extends MTEHatchMaintenance {

    private static final int CIRCUIT_SLOT = 0;
    private static final long TICKS_BETWEEN_FETCH = 20L;

    private UUID ownerUuid;
    private long euPerTick;

    public AE2MaintenanceHatchUniversal(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
        this.mWrench = this.mScrewdriver = this.mSoftMallet = this.mHardHammer = this.mCrowbar = this.mSolderingTool = true;
    }

    public AE2MaintenanceHatchUniversal(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures, false);
        this.mWrench = this.mScrewdriver = this.mSoftMallet = this.mHardHammer = this.mCrowbar = this.mSolderingTool = true;
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            "Universal Maintenance Hatch",
            "Bypasses all maintenance issues",
            "Provides wireless energy from your global network",
            "Insert a circuit board to set parallel count (4^level)"
        };
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] {
            aBaseTexture,
            TextureFactory.builder().addIcon(gregtech.api.enums.Textures.BlockIcons.OVERLAY_AUTOMAINTENANCE_IDLE).extFacing().build(),
            TextureFactory.builder().addIcon(gregtech.api.enums.Textures.BlockIcons.OVERLAY_AUTOMAINTENANCE_IDLE_GLOW).extFacing().glow().build()
        };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] {
            aBaseTexture,
            TextureFactory.builder().addIcon(gregtech.api.enums.Textures.BlockIcons.OVERLAY_AUTOMAINTENANCE).extFacing().build(),
            TextureFactory.builder().addIcon(gregtech.api.enums.Textures.BlockIcons.OVERLAY_AUTOMAINTENANCE_GLOW).extFacing().glow().build()
        };
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new AE2MaintenanceHatchUniversal(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (aBaseMetaTileEntity.isServerSide()) {
            ownerUuid = aBaseMetaTileEntity.getOwnerUuid();
            if (ownerUuid != null) {
                WirelessNetworkManager.strongCheckOrAddUser(ownerUuid);
            }
            updateEuPerTick();
        }
    }

    @Override
    public void onPreTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPreTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && aTick % TICKS_BETWEEN_FETCH == 0) {
            tryFetchingEnergy();
        }
    }

    private void tryFetchingEnergy() {
        if (ownerUuid == null) return;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return;

        long currentEU = base.getStoredEU();
        long maxEU = maxEUStore();
        long euToTransfer = Math.min(maxEU - currentEU, euPerTick * TICKS_BETWEEN_FETCH);
        if (euToTransfer <= 0) return;

        if (WirelessNetworkManager.addEUToGlobalEnergyMap(ownerUuid, -euToTransfer)) {
            setEUVar(currentEU + euToTransfer);
        }
    }

    private void updateEuPerTick() {
        int circuitLevel = getCircuitLevel();
        if (circuitLevel >= 0 && circuitLevel < V.length) {
            euPerTick = V[circuitLevel];
        } else {
            euPerTick = V[1];
        }
    }

    private int getCircuitLevel() {
        ItemStack circuitStack = mInventory[CIRCUIT_SLOT];
        if (circuitStack == null) return -1;

        int damage = circuitStack.getItemDamage();
        if (damage >= 0 && damage <= 15) {
            return damage;
        }
        return -1;
    }

    public int getParallelCount() {
        int level = getCircuitLevel();
        if (level < 0) return 1;
        if (level >= 15) return Integer.MAX_VALUE;
        return 1 << (2 * level);
    }

    @Override
    public boolean isEnetInput() {
        return true;
    }

    @Override
    public long maxEUInput() {
        return euPerTick;
    }

    @Override
    public long maxEUStore() {
        return euPerTick * 8L * TICKS_BETWEEN_FETCH;
    }

    @Override
    public long getMinimumStoredEU() {
        return euPerTick * 2L;
    }

    @Override
    public MTEHatch.ConnectionType getConnectionType() {
        return MTEHatch.ConnectionType.WIRELESS;
    }

    @Override
    public void onMaintenancePerformed(MTEMultiBlockBase aMaintenanceTarget) {
        setMaintenanceSound(SoundResource.GT_MAINTENANCE_CREATIVE_HATCH, 1.0F, 1.0F);
        this.mWrench = this.mScrewdriver = this.mSoftMallet = this.mHardHammer = this.mCrowbar = this.mSolderingTool = true;
        super.onMaintenancePerformed(aMaintenanceTarget);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (side == aBaseMetaTileEntity.getFrontFacing()) {
            if (aBaseMetaTileEntity.isClientSide()) return true;
            openGui(aPlayer);
            return true;
        }
        return false;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (ownerUuid != null) {
            aNBT.setString("ae2qolOwnerUuid", ownerUuid.toString());
        }
        aNBT.setLong("ae2qolEuPerTick", euPerTick);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        String uuidStr = aNBT.getString("ae2qolOwnerUuid");
        if (uuidStr != null && !uuidStr.isEmpty()) {
            try {
                ownerUuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                MyMod.LOG.warn("Invalid UUID in universal maintenance hatch: {}", uuidStr);
            }
        }
        euPerTick = aNBT.getLong("ae2qolEuPerTick");
        if (euPerTick <= 0) {
            euPerTick = V[1];
        }
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return aIndex == CIRCUIT_SLOT;
    }

    @Override
    public int getSizeInventory() {
        return 1;
    }
}
