package com.wztwzt.ae2_qof.tile;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import com.wztwzt.ae2_qof.quest.QuestDetectLogic;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.me.GridAccessException;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.storage.TileIOPort;
import io.netty.buffer.ByteBuf;

/**
 * 「ME 任务检测器」（3.11.0）：接入 ME 网络的方块设备。放置时绑定放置者 UUID，
 * 每 20 tick 将网络中的物品以只读方式喂给 BetterQuesting 检索型任务（consume=false），
 * 库存达标即由 BQ 官方逻辑自动完成任务；不消耗任何物品。
 * <p>
 * 供电/通道状态经 {@link IPowerChannelState} 供渲染与 WAILA 展示；BQ 未安装时
 * tick 内首行守卫直接返回（详见 {@link QuestDetectLogic} 隔离约定）。
 */
public class TileQuestDetector extends TileIOPort implements IPowerChannelState {

    private static final int SCAN_INTERVAL = 20;

    private UUID owner;
    private boolean powered = false;

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID uuid) {
        this.owner = uuid;
    }

    @Override
    public boolean isPowered() {
        return powered;
    }

    /** 供渲染/状态查询的活跃别名（与强化 IO 端口语义一致：供电即视为激活）。 */
    public boolean isActive() {
        return powered;
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeOwnerToNBT(final NBTTagCompound tag) {
        if (owner != null) {
            tag.setString("ae2qolOwner", owner.toString());
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readOwnerFromNBT(final NBTTagCompound tag) {
        owner = null;
        if (tag.hasKey("ae2qolOwner")) {
            try {
                owner = UUID.fromString(tag.getString("ae2qolOwner"));
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readPoweredFromStream(final ByteBuf data) {
        final boolean old = powered;
        powered = data.readBoolean();
        return powered != old;
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writePoweredToStream(final ByteBuf data) {
        data.writeBoolean(powered);
    }

    @MENetworkEventSubscribe
    public void onPowerChange(final MENetworkPowerStatusChange change) {
        refreshPowerState();
    }

    @MENetworkEventSubscribe
    public void onBooting(final MENetworkBootingStatusChange change) {
        refreshPowerState();
    }

    private void refreshPowerState() {
        boolean newState = false;
        try {
            newState = getProxy().isActive()
                && getProxy().getEnergy()
                    .extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.0001;
        } catch (final GridAccessException ignored) {}
        if (newState != powered) {
            powered = newState;
            markForUpdate();
        }
    }

    @TileEvent(TileEventType.TICK)
    public void onQuestDetectTick() {
        if (!QuestDetectLogic.bqAvailable()) return; // 必须最先执行：BQ 缺失时不触碰 Logic 其余符号
        if (worldObj.isRemote || !powered || owner == null) return;
        if (worldObj.getTotalWorldTime() % SCAN_INTERVAL != 0L) return;
        EntityPlayerMP player = findOnlinePlayer(owner);
        if (player == null) return;
        try {
            IGrid grid = getProxy().getNode()
                .getGrid();
            if (grid == null) return;
            QuestDetectLogic.runDetection(grid, player);
        } catch (Throwable ignored) {}
    }

    private static EntityPlayerMP findOnlinePlayer(UUID uuid) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return null;
        List<?> players = server.getConfigurationManager().playerEntityList;
        for (Object o : players) {
            if (o instanceof EntityPlayerMP
                && uuid.equals(((EntityPlayerMP) o).getGameProfile()
                    .getId())) {
                return (EntityPlayerMP) o;
            }
        }
        return null;
    }

    /** WAILA/JADE 用：绑定玩家显示名（BQ NameCache），不可用返回 null。 */
    public String getOwnerNameForDisplay() {
        if (owner == null || !QuestDetectLogic.bqAvailable()) return null;
        return QuestDetectLogic.resolvePlayerName(owner);
    }
}
