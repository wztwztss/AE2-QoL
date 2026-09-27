package com.wztwzt.ae2_qof.generator;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.wztwzt.ae2_qof.AE2QoLCreativeTab;
import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 「批量样板生成器」物品（3.23.0，吞并 AE2PatternGen 的主要功能入口）。
 *
 * <p>右键打开 MUI2 界面（{@link GeneratorPanel}）：选 RecipeMap、填过滤器、点生成，
 * 服务端扫描该 RecipeMap 并按过滤器产出普通样板，产物进背包（放不下掉脚下），
 * 聊天栏与日志给出全量计数。
 */
public class ItemSmartPatternGenerator extends Item
    implements com.cleanroommc.modularui.api.IGuiHolder<PlayerInventoryGuiData> {

    public ItemSmartPatternGenerator() {
        setUnlocalizedName("ae2_qof.smart_pattern_generator");
        setMaxStackSize(1);
        setCreativeTab(AE2QoLCreativeTab.INSTANCE);
        // 沿用 AE2 原版编码样板贴图（运行时按名引用，不再分发其素材），用青色染色与通配样板（绿）区分
        setTextureName("appliedenergistics2:ItemEncodedPattern");
    }

    @Override
    public int getColorFromItemStack(ItemStack stack, int renderPass) {
        return 0x5CE6E6;
    }

    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData data, PanelSyncManager syncManager,
        com.cleanroommc.modularui.screen.UISettings settings) {
        return GeneratorPanel.build(data, syncManager);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean advanced) {
        lines.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("ae2_qof.generator.tip"));
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        try {
            if (!world.isRemote && player != null) {
                PlayerInventoryGuiFactory.INSTANCE.openFromMainHand(player);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 打开样板生成器界面失败", t);
        }
        return stack;
    }

    public ItemSmartPatternGenerator register() {
        GameRegistry.registerItem(this, "smart_pattern_generator", MyMod.MODID);
        // 合成：1 张 AE2 空白样板 → 1 个生成器（与智能通配样板同思路，注册失败留日志、物品仍可从创造标签取）
        try {
            ItemStack blank = appeng.api.AEApi.instance()
                .definitions()
                .materials()
                .blankPattern()
                .maybeStack(1)
                .get();
            if (blank != null) {
                GameRegistry.addShapelessRecipe(new ItemStack(this), blank);
                MyMod.LOG.info("[AE2QoL] 样板生成器配方已注册：AE2 空白样板 → 批量样板生成器");
            } else {
                MyMod.LOG.warn("[AE2QoL] 样板生成器配方注册失败：取不到 AE2 空白样板");
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 样板生成器配方注册异常（物品仍可从创造标签取出）", t);
        }
        return this;
    }
}
