package com.wztwzt.ae2_qof;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.command.CommandHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import appeng.api.storage.data.IAEStack;
import appeng.tile.inventory.IAEStackInventory;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerPatternTermEx;

import com.wztwzt.ae2_qof.client.ClientState;
import com.wztwzt.ae2_qof.common.RecipeMapNameConfig;
import com.wztwzt.ae2_qof.client.CommandOverlay;
import com.wztwzt.ae2_qof.client.event.GuiUploadButtonHandler;
import com.wztwzt.ae2_qof.client.event.KeyInputHandler;
import com.wztwzt.ae2_qof.client.event.KnifeNameCopyHandler;
import com.wztwzt.ae2_qof.client.GuideNHIntegration;
import com.wztwzt.ae2_qof.client.event.MergedTerminalPanelHandler;
import com.wztwzt.ae2_qof.client.gui.GuiProviderSelect;
import com.wztwzt.ae2_qof.client.render.RenderBlockTransceiver;
import com.wztwzt.ae2_qof.client.render.WirelessHighlightRenderer;
import com.wztwzt.ae2_qof.merged.GuiMergedTerminal;
import com.wztwzt.ae2_qof.network.ConfigUpdatePacket;
import com.wztwzt.ae2_qof.network.CraftingCompletePacket;
import com.wztwzt.ae2_qof.network.CraftingResponsePacket;
import com.wztwzt.ae2_qof.network.MergedTerminalBlankCountPacket;
import com.wztwzt.ae2_qof.network.MergedTerminalResultPacket;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.ProvidersListS2CPacket;
import com.wztwzt.ae2_qof.network.ReplaceCandidatesPacket;
import com.wztwzt.ae2_qof.network.RequestProvidersListPacket;
import com.wztwzt.ae2_qof.network.SwapPatternPacket;
import com.wztwzt.ae2_qof.network.UploadFeedbackPacket;
import com.wztwzt.ae2_qof.network.UploadPatternPacket;
import com.wztwzt.ae2_qof.network.WirelessChannelSyncPacket;
import com.wztwzt.ae2_qof.network.WirelessHighlightPacket;
import com.wztwzt.ae2_qof.network.HatchListSyncPacket;
import com.wztwzt.ae2_qof.util.RecipeNameUtil;
import com.wztwzt.ae2_qof.wireless.gui.GuiWireless;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        RenderingRegistry.registerBlockHandler(RenderBlockTransceiver.INSTANCE);
        GuiUploadButtonHandler.register();
        MergedTerminalPanelHandler.register();
        KeyInputHandler.register();
        KnifeNameCopyHandler.register();
        GuideNHIntegration.register();
        MinecraftForge.EVENT_BUS.register(WirelessHighlightRenderer.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new ClientRenderEventHandler());
        try {
            codechicken.nei.guihook.GuiContainerManager
                .addTooltipHandler(new com.wztwzt.ae2_qof.client.nei.NetworkTooltipHandler());
        } catch (Throwable ignored) {}
        try {
            codechicken.nei.api.API.registerNEIGuiHandler(new com.wztwzt.ae2_qof.client.nei.MergedNeiHandler());
        } catch (Throwable ignored) {}
        try {
            // 让 NEI 配方界面的「+」覆盖层对合并终端生效（与 AE2 原生终端一致注册方式）
            codechicken.nei.api.API.registerGuiOverlay(
                com.wztwzt.ae2_qof.merged.GuiMergedTerminal.class,
                "crafting",
                new appeng.integration.modules.NEIHelpers.TerminalCraftingSlotFinder());
            codechicken.nei.api.API.registerGuiOverlayHandler(
                com.wztwzt.ae2_qof.merged.GuiMergedTerminal.class,
                new codechicken.nei.recipe.DefaultOverlayHandler(),
                "crafting");
        } catch (Throwable ignored) {}
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {
        super.serverStarting(event);
        ((CommandHandler) event.getServer()
            .getCommandManager()).registerCommand(new CommandOverlay());
    }

    // ===== S2C 包客户端处理实现（#74）=====
    // 全部归队到客户端主线程执行（顺带修复 CraftingResponse/ReplaceCandidates 原先未归队问题）。

    @Override
    public void handleProvidersList(final ProvidersListS2CPacket message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                try {
                // Shift+点击：强制打开选择页面
                if (message.forceGui) {
                    openGuiWithSearch(message);
                    return;
                }

                // 策略1: 只有一个有效供应器时直接上传
                List<Long> validIds = new ArrayList<Long>();
                List<String> validKeys = new ArrayList<String>();
                for (int i = 0; i < message.ids.size(); i++) {
                    if (message.emptySlots.get(i) > 0) {
                        validIds.add(message.ids.get(i));
                        validKeys.add(
                            i < message.locationKeys.size() ? message.locationKeys.get(i) : null);
                    }
                }
                if (validIds.size() == 1) {
                    MyMod.LOG.info("[Upload] strategy1: single provider, id={}", validIds.get(0));
                    ClientState.set(null, validIds.get(0));
                    // fix41：单目标也带上稳定位置，避免该机器被拆装/重载后 ID 失效
                    ModNetwork.CHANNEL.sendToServer(new UploadPatternPacket(validIds.get(0), validKeys.get(0)));
                    return;
                }

                // 策略2: 查已记住的 Provider 名字
                MyMod.LOG.info("[Upload] strategy2 check: recipeMap={}, rememberedProviders size={}",
                    message.recipeMap, ClientState.rememberedProviders.size());
                if (message.recipeMap != null && !message.recipeMap.isEmpty()) {
                    ClientState.lastRecipeMap = message.recipeMap;

                    String rememberedName = ClientState.getRememberedProviderName(message.recipeMap);
                    MyMod.LOG.info("[Upload] strategy2: recipeMap='{}', rememberedName='{}'",
                        message.recipeMap, rememberedName);
                    if (rememberedName != null) {
                        long matchId = 0;
                        String matchKey = null;
                        int matchCount = 0;
                        for (int i = 0; i < message.ids.size(); i++) {
                            if (message.emptySlots.get(i) > 0 && message.names.get(i)
                                .equals(rememberedName)) {
                                    matchId = message.ids.get(i);
                                    matchKey = i < message.locationKeys.size() ? message.locationKeys.get(i) : null;
                                matchCount++;
                            }
                        }
                        // 兼容旧配置：旧版 remembered 只存基础机器名，没有坐标后缀。
                        // 仅当基础名唯一命中时才采用，多个同名候选仍交给玩家手动选择。
                        if (matchCount != 1) {
                            matchId = 0;
                            matchCount = 0;
                            String base = stripLocationSuffix(rememberedName);
                            for (int i = 0; i < message.ids.size(); i++) {
                                if (message.emptySlots.get(i) > 0
                                    && stripLocationSuffix(message.names.get(i))
                                        .equals(base)) {
                                    matchId = message.ids.get(i);
                                    matchKey = i < message.locationKeys.size() ? message.locationKeys.get(i) : null;
                                    matchCount++;
                                }
                            }
                        }
                        MyMod.LOG.info("[Upload] strategy2: matchCount={}, matchId={}", matchCount, matchId);
                        if (matchCount == 1) {
                            MyMod.LOG.info("[Upload] strategy2: remembered provider '{}', id={}", rememberedName, matchId);
                            ClientState.set(rememberedName, matchId);
                            ModNetwork.CHANNEL.sendToServer(new UploadPatternPacket(matchId, matchKey));
                            return;
                        }
                    }
                } else {
                    MyMod.LOG.info("[Upload] strategy2: recipeMap is null or empty, skip remembered check");
                }

                // 策略3: 打开搜索界面
                openGuiWithSearch(message);
                } catch (Throwable t) {
                    MyMod.LOG.error("[Upload] handleProvidersList failed", t);
                }
            });
    }

    private void openGuiWithSearch(ProvidersListS2CPacket message) {
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        String searchKey = null;
        boolean craftingPattern = message.recipeMap != null && "crafting".equalsIgnoreCase(message.recipeMap.trim());
        if (craftingPattern) {
            // F1：工作台合成样板的目标是 AE2 接口一类机器，而不是某台 GT 加工机。
            // 旧实现把 crafting 解析成关键词“合成”预填搜索框，界面里没有任何机器叫“合成”，
            // 玩家因此看不出该选哪台，表现为“工作台合成类无法识别”。
            // 改为：优先用上次记住的目标名过滤，没有记住就不预填、直接展示全部可选目标。
            String remembered = ClientState.getRememberedProviderName(message.recipeMap);
            if (remembered != null && !remembered.isEmpty()) {
                searchKey = remembered;
            }
        } else if (message.recipeMap != null && !message.recipeMap.isEmpty()) {
            // 优先用映射表解析中文名，查不到时用 NEI 捕获的中文名兜底，最后才用英文 id
            searchKey = RecipeMapNameConfig.resolveSearchKeyword(message.recipeMap);
            if (searchKey == null || searchKey.equals(message.recipeMap)) {
                String cnName = ClientState.pendingRecipeCnName;
                if (cnName != null && !cnName.isEmpty()) {
                    searchKey = cnName;
                } else {
                    searchKey = message.recipeMap;
                }
            }
        }
        GuiProviderSelect gui = new GuiProviderSelect(current, message.ids, message.names, message.emptySlots,
            message.totalSlots, message.locationKeys, message.icons, message.recipeMap);
        if (searchKey != null) {
            gui.setPresetSearchKey(searchKey);
        }
        Minecraft.getMinecraft()
            .displayGuiScreen(gui);
    }

    /**
     * 去掉供应器展示名里的「 @D维度 x,y,z」坐标后缀，用于兼容旧配置中只存机器名的映射。
     */
    private static String stripLocationSuffix(String name) {
        if (name == null) {
            return "";
        }
        int idx = name.indexOf(" @D");
        return idx >= 0 ? name.substring(0, idx) : name;
    }

    @Override
    public void handleWirelessChannelSync(final WirelessChannelSyncPacket message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen instanceof GuiWireless) {
                    ((GuiWireless) screen).syncChannelList(message.channels);
                }
            });
    }

    @Override
    public void handleWirelessHighlight(final WirelessHighlightPacket message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                ClientState.highlightPositions = message.positions;
                ClientState.highlightEnabled = message.enable;
            });
    }

    @Override
    public void handleHatchListSync(final HatchListSyncPacket message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                ClientState.hatchListCache = message.buildCache();
            });
    }

    @Override
    public void handleSwapPattern(final SwapPatternPacket message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        // 归队到客户端主线程执行，避免 Netty IO 线程操作容器
        mc.func_152344_a(() -> {
            try {
                applyClientSwap(message);
            } catch (Throwable e) {
                MyMod.LOG.error("Swap pattern apply failed on client", e);
            }
        });
    }

    private void applyClientSwap(SwapPatternPacket message) {
        if (message.slotStacks == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        Container container = mc.thePlayer.openContainer;
        if (container == null) {
            return;
        }

        IAEStack<?>[] outputSlots = null;
        IAEStackInventory clientOutputs = null;

        if (container instanceof ContainerPatternTerm pt) {
            outputSlots = pt.outputSlotsClient;
            try {
                Field outputsField = ContainerPatternTerm.class.getDeclaredField("outputs");
                outputsField.setAccessible(true);
                clientOutputs = (IAEStackInventory) outputsField.get(pt);
            } catch (Throwable ignored) {}
        } else if (container instanceof ContainerPatternTermEx pte) {
            outputSlots = pte.outputSlotsClient;
            try {
                Field outputsField = ContainerPatternTermEx.class.getDeclaredField("outputs");
                outputsField.setAccessible(true);
                clientOutputs = (IAEStackInventory) outputsField.get(pte);
            } catch (Throwable ignored) {}
        }

        if (outputSlots == null) {
            return;
        }

        for (int i = 0; i < Math.min(message.slotStacks.size(), outputSlots.length); i++) {
            IAEStack<?> aeStack = message.slotStacks.get(i);
            outputSlots[i] = aeStack;
            if (clientOutputs != null) {
                clientOutputs.putAEStackInSlot(i, aeStack);
            }
        }
    }

    @Override
    public void handleCraftingResponse(CraftingResponsePacket message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                EntityPlayer player = Minecraft.getMinecraft().thePlayer;
                if (player == null) return;

                String itemName = message.itemName != null ? message.itemName : "???";

                switch (message.resultType) {
                    case CraftingResponsePacket.RESULT_SUCCESS:
                        player.addChatMessage(
                            new ChatComponentTranslation(
                                "ae2qol.extract.success",
                                EnumChatFormatting.GREEN + itemName + EnumChatFormatting.WHITE));
                        break;
                    case CraftingResponsePacket.RESULT_NO_ITEMS:
                        player.addChatMessage(
                            new ChatComponentTranslation(
                                "ae2qol.extract.no_items",
                                EnumChatFormatting.RED + itemName + EnumChatFormatting.WHITE));
                        break;
                    case CraftingResponsePacket.RESULT_NOT_CRAFTABLE:
                        player.addChatMessage(
                            new ChatComponentTranslation(
                                "ae2qol.extract.not_craftable",
                                EnumChatFormatting.RED + itemName + EnumChatFormatting.WHITE));
                        break;
                    case CraftingResponsePacket.RESULT_INVENTORY_FULL:
                        player.addChatMessage(
                            new ChatComponentTranslation(
                                "ae2qol.extract.inventory_full",
                                EnumChatFormatting.RED + itemName + EnumChatFormatting.WHITE));
                        break;
                }
            });
    }

    @Override
    public void handleCraftingComplete(final CraftingCompletePacket message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                if (message.stack == null) return;
                // 3.15.0：改用 AE2 原生通知渲染（GuiNotification，原版成就横幅样式）——
                // 标题/描述/耗时格式全部复用 AE2 lang key，自动显示中文「自动合成完成 / N 物品, 耗时 HH:mm:ss」
                try {
                    String elapsedText = org.apache.commons.lang3.time.DurationFormatUtils.formatDuration(
                        Math.max(0L, message.elapsedTimeMillis),
                        net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.ETAFormat"));
                    String description = net.minecraft.util.StatCollector.translateToLocalFormatted(
                        "chat.appliedenergistics2.CraftComplete.tip",
                        message.amount,
                        message.stack.getDisplayName(),
                        elapsedText);
                    appeng.api.storage.data.IAEItemStack icon = appeng.util.item.AEItemStack.create(message.stack);
                    if (icon == null) return;
                    appeng.client.render.notification.NotificationManager.getGuiNotification()
                        .queueNotification(
                            new appeng.client.render.notification.Notification(
                                icon,
                                net.minecraft.util.StatCollector
                                    .translateToLocal("chat.appliedenergistics2.CraftComplete"),
                                description));
                    if (Minecraft.getMinecraft().theWorld != null && Minecraft.getMinecraft().thePlayer != null) {
                        Minecraft.getMinecraft().theWorld
                            .playSound(
                                Minecraft.getMinecraft().thePlayer.posX,
                                Minecraft.getMinecraft().thePlayer.posY,
                                Minecraft.getMinecraft().thePlayer.posZ,
                                "random.levelup",
                                0.25f,
                                1,
                                false);
                    }
                } catch (Throwable t) {
                    com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] crafting notification failed: " + t);
                }
            });
    }

    @Override
    public void handleConfigUpdate(final ConfigUpdatePacket message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                try {
                    // overlay 字段仅作协议兼容保留：NEI 叠加层为纯客户端渲染开关，
                    // 不随服务端同步覆盖客户端本地值（#48）
                    Config.applyAll(message.io, message.rounds);
                } catch (Throwable t) {
                    MyMod.LOG.error("Config update apply failed", t);
                }
            });
    }

    @Override
    public void handleMergedTerminalResult(final MergedTerminalResultPacket message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                ClientState.mergedMachineName = message.machineName;
                if (message.needsMapping) {
                    // 无中文映射：弹出供应器选择/映射页，引导玩家为 recipeMap 命名
                    if (message.recipeMap != null && !message.recipeMap.isEmpty()) {
                        ClientState.lastRecipeMap = message.recipeMap;
                        RecipeNameUtil.setLastRawRecipeId(message.recipeMap);
                        ModNetwork.CHANNEL.sendToServer(new RequestProvidersListPacket(message.recipeMap, true));
                    }
                } else {
                    // 编码成功后自动把机器名填入搜索框，过滤出刚编码的机器
                    GuiMergedTerminal.setSearchFieldText(message.machineName);
                }
            });
    }

    @Override
    public void handleMergedTerminalBlankCount(final MergedTerminalBlankCountPacket message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> ClientState.mergedBlankCount = message.count);
    }

    @Override
    public void handleReplaceCandidates(final ReplaceCandidatesPacket message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                ClientState.replaceCandidates = message.candidates;
                ClientState.replaceCurrentIndex = message.currentIndex;
            });
    }

    /**
     * fix41：样板上传失败/被拒绝时把原因显示到聊天栏。
     * 旧实现只写服务端日志，玩家看到的是「点了没反应」，无从判断是目标被拆了、槽位满了还是没权限。
     */
    @Override
    public void handleUploadFeedback(final UploadFeedbackPacket message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                if (Minecraft.getMinecraft().thePlayer == null || message == null
                    || message.messageKey == null
                    || message.messageKey.isEmpty()) {
                    return;
                }
                Minecraft.getMinecraft().thePlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        net.minecraft.util.EnumChatFormatting.YELLOW + net.minecraft.util.StatCollector.translateToLocal(message.messageKey)));
            });
    }

    @SideOnly(Side.CLIENT)
    public static class ClientRenderEventHandler {

        // 3.15.0：旧自绘横幅（CraftingNotificationOverlay）已由 AE2 原生 GuiNotification 取代，
        // 其渲染由 AE2 自己的 NotificationManager（RenderTickEvent）驱动，此处不再订阅。

        /**
         * P1-023/P1-024：离开世界（退回主菜单、断开服务器、切换存档）时清理所有客户端静态缓存。
         * 否则旧的无线高亮坐标、自适应仓列表与 NEI 网络库存会在新存档里继续显示最多 5 分钟。
         */
        @SubscribeEvent
        public void onWorldUnload(net.minecraftforge.event.world.WorldEvent.Unload event) {
            try {
                ClientState.highlightEnabled = false;
                ClientState.highlightPositions = new java.util.ArrayList<int[]>();
                ClientState.adaptiveHighlightPositions = new java.util.ArrayList<int[]>();
                ClientState.adaptiveHighlightExpiryTick = 0;
                ClientState.hatchListCache = null;
                com.wztwzt.ae2_qof.client.NetworkInventoryCache.clear();
            } catch (Throwable ignored) {}
        }
    }
}
