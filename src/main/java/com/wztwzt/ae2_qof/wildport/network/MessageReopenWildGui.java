/*
 * 本文件属于本模组自有改造（3.22.0-fix53），不是搬运代码。
 */
package com.wztwzt.ae2_qof.wildport.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.wildcard.ItemSmartWildcardPattern;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 客户端 → 服务端：**重新打开通配样板窗口**（3.22.0-fix53）。
 *
 * <h2>为什么需要它</h2>
 * 通配窗口（Wild 界面，GUI {@link ItemSmartWildcardPattern#GUI_ID}=130）是 GTNH-ModularUI 自绘界面，
 * 里面**系统输入法不可用**，所以文本框旁加了「改」按钮走**原版输入框对话框**
 * （{@code client/gui/GuiTextInputDialog}）—— 而原版 GuiScreen 一打开就会把 MUI 窗口顶掉，
 * 关掉对话框后回到"没有界面"，用户观感就是"输入完界面就没了"。
 *
 * <p>本包让服务端在对话框确认后**把 Wild 窗口原样重开**，用户回到刚才那个界面继续操作。
 * 空载荷即可：窗口自身从**手持样板物品**读状态，重开即恢复。
 *
 * <p>说明：Wild 窗口上现有的「改」按钮只挂在**搜索框**（预览页搜索、去重页搜索）这类
 * **纯临时文本**上，不会写进样板数据，所以不需要额外落盘；真正要落盘的规则行文本没有用这套入口。
 */
public class MessageReopenWildGui implements IMessage {

    public MessageReopenWildGui() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // 空载荷
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // 空载荷
    }

    public static class Handler implements IMessageHandler<MessageReopenWildGui, IMessage> {

        @Override
        public IMessage onMessage(MessageReopenWildGui message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (player == null) return null;
            try {
                player.openGui(
                    MyMod.instance,
                    ItemSmartWildcardPattern.GUI_ID,
                    player.worldObj,
                    0,
                    0,
                    0);
                MyMod.LOG.info("[AE2QoL] 「改」对话框确认后已重开通配样板界面（GUI {})", ItemSmartWildcardPattern.GUI_ID);
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] 重开通配样板界面失败", t);
            }
            return null;
        }
    }
}
