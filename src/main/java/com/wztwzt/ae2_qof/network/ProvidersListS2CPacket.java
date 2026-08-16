package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.wztwzt.ae2_qof.client.ClientState;
import com.wztwzt.ae2_qof.client.gui.GuiProviderSelect;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class ProvidersListS2CPacket implements IMessage {

    private List<Long> ids;
    private List<String> names;
    private List<Integer> emptySlots;
    private String recipeMap;
    private boolean forceGui;

    public ProvidersListS2CPacket() {
        this.ids = new ArrayList<Long>();
        this.names = new ArrayList<String>();
        this.emptySlots = new ArrayList<Integer>();
        this.recipeMap = null;
        this.forceGui = false;
    }

    public ProvidersListS2CPacket(List<Long> ids, List<String> names, List<Integer> emptySlots, String recipeMap,
        boolean forceGui) {
        this.ids = ids;
        this.names = names;
        this.emptySlots = emptySlots;
        this.recipeMap = recipeMap;
        this.forceGui = forceGui;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int size = buf.readInt();
            ids = new ArrayList<Long>(size);
            names = new ArrayList<String>(size);
            emptySlots = new ArrayList<Integer>(size);

            for (int i = 0; i < size; i++) {
                ids.add(buf.readLong());
                names.add(readString(buf));
                emptySlots.add(buf.readInt());
            }

            boolean hasRecipeMap = buf.readBoolean();
            recipeMap = hasRecipeMap ? readString(buf) : null;
            forceGui = buf.readBoolean();
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            ids = new ArrayList<Long>();
            names = new ArrayList<String>();
            emptySlots = new ArrayList<Integer>();
            recipeMap = null;
            forceGui = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            buf.writeLong(ids.get(i));
            writeString(buf, names.get(i));
            buf.writeInt(emptySlots.get(i));
        }

        buf.writeBoolean(recipeMap != null);
        if (recipeMap != null) {
            writeString(buf, recipeMap);
        }
        buf.writeBoolean(forceGui);
    }

    private void writeString(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private String readString(ByteBuf buf) {
        int len = buf.readShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<ProvidersListS2CPacket, IMessage> {

        @Override
        public IMessage onMessage(final ProvidersListS2CPacket message, MessageContext ctx) {
            Minecraft minecraft = Minecraft.getMinecraft();
            minecraft.func_152344_a(new Runnable() {

                @Override
                public void run() {
                    // Shift+点击：强制打开选择页面
                    if (message.forceGui) {
                        openGuiWithSearch(message);
                        return;
                    }

                    // 策略1: 只有一个有效供应器时直接上传
                    List<Long> validIds = new ArrayList<Long>();
                    for (int i = 0; i < message.ids.size(); i++) {
                        if (message.emptySlots.get(i) > 0) {
                            validIds.add(message.ids.get(i));
                        }
                    }
                    if (validIds.size() == 1) {
                        ClientState.set(null, validIds.get(0));
                        System.out.println("[APU] Auto-upload: lastProviderId=" + ClientState.lastProviderId);
                        ModNetwork.CHANNEL.sendToServer(new UploadPatternPacket(validIds.get(0)));
                        return;
                    }

                    // 策略2: 查已记住的 Provider 名字
                    if (message.recipeMap != null && !message.recipeMap.isEmpty()) {
                        ClientState.lastRecipeMap = message.recipeMap;

                        String rememberedName = ClientState.getRememberedProviderName(message.recipeMap);
                        if (rememberedName != null) {
                            long matchId = 0;
                            int matchCount = 0;
                            for (int i = 0; i < message.ids.size(); i++) {
                                if (message.emptySlots.get(i) > 0 && message.names.get(i)
                                    .equals(rememberedName)) {
                                    matchId = message.ids.get(i);
                                    matchCount++;
                                }
                            }
                            if (matchCount == 1) {
                                ClientState.set(rememberedName, matchId);
                                ModNetwork.CHANNEL.sendToServer(new UploadPatternPacket(matchId));
                                return;
                            }
                        }
                    }

                    // 策略3: 打开搜索界面
                    openGuiWithSearch(message);
                }

                private void openGuiWithSearch(final ProvidersListS2CPacket message) {
                    GuiScreen current = Minecraft.getMinecraft().currentScreen;
                    String searchKey = null;
                    if (message.recipeMap != null && !message.recipeMap.isEmpty()) {
                        searchKey = message.recipeMap;
                    }
                    GuiProviderSelect gui = new GuiProviderSelect(
                        current,
                        message.ids,
                        message.names,
                        message.emptySlots);
                    if (searchKey != null) {
                        gui.setPresetSearchKey(searchKey);
                    }
                    Minecraft.getMinecraft()
                        .displayGuiScreen(gui);
                }
            });
            return null;
        }
    }
}
