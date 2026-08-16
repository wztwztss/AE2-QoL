package com.wztwzt.ae2_qof.tile;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.me.GridAccessException;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.storage.TileIOPort;
import io.netty.buffer.ByteBuf;

/**
 * 强化版 IO 端口：继承原版 TileIOPort，增加供电状态渲染，传输倍率由 MixinTileIOPort 实现。
 */
public class TileExIOPort extends TileIOPort implements IPowerChannelState {

    private boolean isPowered = false;

    @Override
    public boolean isPowered() {
        return isPowered;
    }

    public boolean isActive() {
        return isPowered;
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream(final ByteBuf data) {
        final boolean oldPower = isPowered;
        isPowered = data.readBoolean();
        return isPowered != oldPower;
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream(final ByteBuf data) {
        data.writeBoolean(isActive());
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange p) {
        updatePowerState();
    }

    @MENetworkEventSubscribe
    public final void bootingRender(final MENetworkBootingStatusChange c) {
        updatePowerState();
    }

    private void updatePowerState() {
        boolean newState = false;
        try {
            newState = getProxy().isActive() && getProxy().getEnergy()
                .extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.0001;
        } catch (final GridAccessException ignored) {}
        if (newState != isPowered) {
            isPowered = newState;
            markForUpdate();
        }
    }
}
