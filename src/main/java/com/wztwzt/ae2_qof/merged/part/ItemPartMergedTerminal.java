package com.wztwzt.ae2_qof.merged.part;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;

/**
 * 二合一终端的线缆面板部件物品。
 * <p>
 * 实现 {@link IPartItem} 后由 AE2 线缆系统接管放置交互：
 * onItemUse → partHelper.placeBus → 宿主 addPart（识别线缆/泛用线缆 FMP），
 * 放置、拆除掉落、多人同步全部复用 AE2 原生管线。
 */
public class ItemPartMergedTerminal extends Item implements IPartItem {

    public ItemPartMergedTerminal() {
        setUnlocalizedName("merged_terminal_part");
        setTextureName("appliedenergistics2:ItemPart.Terminal");
        setCreativeTab(com.wztwzt.ae2_qof.AE2QoLCreativeTab.INSTANCE);
        setMaxStackSize(64);
        // 注册 AE2 总线渲染器（物品在手中的 part 形态渲染）；AE2 自身 ItemMultiPart 同样在构造器调用
        AEApi.instance()
            .partHelper()
            .setItemBusRenderer(this);
    }

    @Override
    public IPart createPartFromItemStack(ItemStack is) {
        return new PartMergedTerminal(is);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, java.util.List list, boolean advanced) {
        for (int i = 1; i <= 4; i++) {
            String key = "item.merged_terminal_part.tooltip." + i;
            String line = net.minecraft.util.StatCollector.translateToLocal(key);
            if (line != null && !line.isEmpty() && !line.equals(key)) {
                list.add(net.minecraft.util.EnumChatFormatting.GRAY + line);
            }
        }
        list.add(net.minecraft.util.EnumChatFormatting.DARK_GRAY + "ae2qof");
    }

    @Override
    public boolean onItemUse(ItemStack is, EntityPlayer player, World world, int x, int y, int z, int side, float hitX,
        float hitY, float hitZ) {
        return AEApi.instance()
            .partHelper()
            .placeBus(is, x, y, z, side, player, world);
    }
}
