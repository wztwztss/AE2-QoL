package com.wztwzt.ae2_qof.network;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

import com.wztwzt.ae2_qof.api.ISmartDoublingMedium;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;

/**
 * S2C：服务端权威的智能倍增开关状态（3.21.3 新增）。
 *
 * <p>为什么需要它：GT/GTNL/PH 的开关控件建在**仅客户端**的 mixin 里，界面上的 getter 读的是
 * **客户端内存里**那台机器的字段——在专用服务端上它与服务端真值可能长期不一致
 * （勾选只改了客户端对象、重登后服务端值又读不回来）。
 * 本包把服务端真值送回来写进客户端对象，界面于是始终显示权威值。
 *
 * <p>只注册在 {@link Side#CLIENT}，服务端永不解包（类里的 {@code Minecraft} 引用因此不会在服务端加载）。
 */
public class SmartDoublingStatePacket implements IMessage {

    private int dim;
    private int x;
    private int y;
    private int z;
    private boolean enabled;

    public SmartDoublingStatePacket() {}

    public SmartDoublingStatePacket(int dim, int x, int y, int z, boolean enabled) {
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
        this.enabled = enabled;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.dim = buf.readInt();
            this.x = buf.readInt();
            this.y = buf.readInt();
            this.z = buf.readInt();
            this.enabled = buf.readBoolean();
        } catch (Throwable ignored) {}
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeBoolean(enabled);
    }

    public static class Handler implements IMessageHandler<SmartDoublingStatePacket, IMessage> {

        @Override
        public IMessage onMessage(SmartDoublingStatePacket message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) return null;
            try {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null || mc.theWorld == null) return null;
                if (mc.theWorld.provider.dimensionId != message.dim) return null;
                TileEntity te = mc.theWorld.getTileEntity(message.x, message.y, message.z);
                if (!(te instanceof IGregTechTileEntity)) return null;
                IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
                if (mte instanceof ISmartDoublingMedium sdm) {
                    // 只写客户端对象（GUI getter 读的就是它）——服务端那份由服务端自己维护
                    sdm.setSmartDoubling(message.enabled);
                }
            } catch (Throwable ignored) {}
            return null;
        }
    }
}
