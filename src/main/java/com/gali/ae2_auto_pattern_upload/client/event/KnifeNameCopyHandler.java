package com.gali.ae2_auto_pattern_upload.client.event;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import appeng.api.parts.IPart;
import appeng.helpers.ICustomNameObject;
import appeng.tile.AEBaseTile;
import appeng.tile.networking.TileCableBus;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class KnifeNameCopyHandler {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new KnifeNameCopyHandler());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        EntityPlayer player = (EntityPlayer) event.entity;
        if (player == null || !player.isSneaking()) {
            return;
        }

        ItemStack held = player.inventory.getCurrentItem();
        if (held == null) {
            return;
        }

        if (!isQuartzCuttingKnife(held)) {
            return;
        }

        World world = event.world;
        if (world == null) {
            return;
        }

        TileEntity te = world.getTileEntity(event.x, event.y, event.z);
        if (te == null) {
            return;
        }

        String name = null;

        // AE2 线缆部件
        if (te instanceof TileCableBus) {
            TileCableBus cableBus = (TileCableBus) te;
            ForgeDirection side = ForgeDirection.getOrientation(event.face);
            IPart part = cableBus.getPart(side);
            if (part instanceof ICustomNameObject) {
                ICustomNameObject named = (ICustomNameObject) part;
                name = named.getCustomName();
            }
            if (name == null || name.isEmpty()) {
                name = getPartDisplayName(part);
            }
        }
        // AE2 方块
        else if (te instanceof AEBaseTile) {
            AEBaseTile aeTile = (AEBaseTile) te;
            if (aeTile instanceof ICustomNameObject) {
                name = ((ICustomNameObject) aeTile).getCustomName();
            }
            if (name == null || name.isEmpty()) {
                name = aeTile.getClass()
                    .getSimpleName();
            }
        }
        // GT 机器
        else if (isGTTileEntity(te)) {
            name = getGTName(te);
        }
        // 其他方块
        else {
            name = te.getBlockType()
                .getLocalizedName();
        }

        if (name == null || name.isEmpty()) {
            return;
        }

        // 写入刀的显示名称
        held.setStackDisplayName(name);

        // 复制到剪贴板
        copyToClipboard(name);

        // 发送聊天提示
        if (player.worldObj.isRemote) {
            String msg = EnumChatFormatting.AQUA + "[Knife] "
                + EnumChatFormatting.GREEN
                + name
                + EnumChatFormatting.GRAY
                + " (copied)";
            player.addChatMessage(new ChatComponentText(msg));
        }
    }

    /**
     * 在 GUI 打开时拦截重命名界面。
     * 因为 PlayerInteractEvent.setCanceled(true) 无法阻止 AE2 在服务端通过
     * onItemUse → Platform.openGUI 打开 GuiRenamer，所以改用 GuiOpenEvent 在客户端拦截。
     */
    @SubscribeEvent
    public void onGuiOpen(net.minecraftforge.client.event.GuiOpenEvent event) {
        if (event.gui == null) {
            return;
        }

        // 检查是否是 AE2 的重命名 GUI (GuiRenamer)
        if (!event.gui.getClass()
            .getName()
            .equals("appeng.client.gui.implementations.GuiRenamer")) {
            return;
        }

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null || !player.isSneaking()) {
            return;
        }

        ItemStack held = player.inventory.getCurrentItem();
        if (held == null || !isQuartzCuttingKnife(held)) {
            return;
        }

        // 取消 GUI 打开
        event.setCanceled(true);
    }

    private boolean isQuartzCuttingKnife(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        String className = stack.getItem()
            .getClass()
            .getName();
        return className.contains("QuartzCuttingKnife") || className.contains("quartzknife");
    }

    private boolean isGTTileEntity(TileEntity te) {
        try {
            return te.getClass()
                .getName()
                .contains("gregtech");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 获取 GT 机器名称。
     * 单方块：仅返回简短中文名（去掉"基础""进阶"等前缀和罗马数字）。
     * 多方块：格式 "运行模式名-主机名"（如"简易洗矿池-大型蒸汽洗矿机"）。
     */
    private String getGTName(TileEntity te) {
        try {
            Object mte = getGTMetaTileEntity(te);
            if (mte == null) {
                return getGTViaInventoryName(te);
            }

            boolean isMultiBlock = isGTMultiBlock(mte);

            if (isMultiBlock) {
                return getGTMultiBlockName(mte, te);
            } else {
                return getGTSingleBlockName(mte, te);
            }
        } catch (Throwable t) {
            return te.getBlockType()
                .getLocalizedName();
        }
    }

    /**
     * 多方块机器：格式 "运行模式名-主机名"
     * 如 "简易洗矿池-大型蒸汽洗矿机"
     */
    private String getGTMultiBlockName(Object mte, TileEntity te) {
        // 获取运行模式名
        String modeName = getModeNameViaHierarchy(mte);
        // 获取主机名
        String machineName = getGTLocalizedName(mte);
        if (machineName == null || machineName.isEmpty()) {
            machineName = getGTViaInventoryName(te);
        }

        if (modeName != null && !modeName.isEmpty() && !modeName.equals(machineName)) {
            return modeName + "-" + machineName;
        }
        return machineName;
    }

    /**
     * 遍历整个类层次结构（类→父类→接口→父接口）查找 getMachineModeKey/getMachineModeName。
     * 因为这两个方法是接口 default 方法，Java 8 的 Class.getMethod() 只查类和父类，不查接口。
     */
    private String getModeNameViaHierarchy(Object mte) {
        // 优先尝试 getMachineModeKey() → 翻译
        String key = findAndInvokeMethod(mte, "getMachineModeKey");
        if (key != null && !key.isEmpty()) {
            String translated = StatCollector.translateToLocal(key);
            if (translated != null && !translated.equals(key)) {
                return translated;
            }
        }
        // fallback：尝试 getMachineModeName()（某些子类直接 override 返回已翻译名）
        String name = findAndInvokeMethod(mte, "getMachineModeName");
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return null;
    }

    /**
     * 从对象的运行时类向上遍历整个继承层次（类→父类→所有接口及父接口），
     * 找到第一个声明了指定方法的 Class/Interface 并调用它。
     */
    private String findAndInvokeMethod(Object obj, String methodName) {
        try {
            java.lang.reflect.Method method = findMethodInHierarchy(obj.getClass(), methodName);
            if (method != null) {
                Object result = method.invoke(obj);
                return result instanceof String ? (String) result : null;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private java.lang.reflect.Method findMethodInHierarchy(Class<?> clazz, String methodName) {
        // 遍历类和父类
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {}
            current = current.getSuperclass();
        }
        // 遍历所有接口及父接口（BFS）
        java.util.Queue<Class<?>> queue = new java.util.LinkedList<>();
        for (Class<?> iface : clazz.getInterfaces()) {
            queue.add(iface);
        }
        while (!queue.isEmpty()) {
            Class<?> iface = queue.poll();
            try {
                return iface.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {}
            for (Class<?> parent : iface.getInterfaces()) {
                queue.add(parent);
            }
        }
        return null;
    }

    /**
     * 单方块机器：去掉"基础""进阶""精英""史诗""终极"前缀和罗马数字，仅保留配方类型名。
     * 如 "基础冲压机床 III" → "冲压机床"
     * 如 "进阶线材轧机 II" → "线材轧机"
     */
    private String getGTSingleBlockName(Object mte, TileEntity te) {
        String localizedName = getGTLocalizedName(mte);
        if (localizedName == null || localizedName.isEmpty()) {
            localizedName = getGTViaInventoryName(te);
        }

        // 去掉前缀
        String cleaned = localizedName;
        cleaned = cleaned.replaceAll("^(基础|进阶|精英|史诗|终极)", "");
        // 去掉尾部罗马数字（I, II, III, IV, V, VI, VII, VIII, IX, X, XI, XII）
        cleaned = cleaned.replaceAll("\\s+(I{1,3}|IV|V|VI{0,3}|IX|X|XI{0,3})\\s*$", "");
        // 去掉尾部阿拉伯数字
        cleaned = cleaned.replaceAll("\\s+\\d+\\s*$", "");
        // 去掉多余空格
        cleaned = cleaned.trim();

        if (!cleaned.isEmpty()) {
            return cleaned;
        }
        return localizedName;
    }

    /**
     * 通过 MTE 的 mName 构造本地化 key 并翻译。
     * key 格式: gt.blockmachines.<mName>.name
     */
    private String getGTLocalizedName(Object mte) {
        try {
            java.lang.reflect.Field mNameField = mte.getClass()
                .getField("mName");
            String mName = (String) mNameField.get(mte);
            if (mName != null && !mName.isEmpty()) {
                String key = "gt.blockmachines." + mName + ".name";
                String translated = StatCollector.translateToLocal(key);
                if (translated != null && !translated.equals(key)) {
                    return translated;
                }
            }
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Method getInvName = mte.getClass()
                .getMethod("getInventoryName");
            Object result = getInvName.invoke(mte);
            if (result instanceof String && !((String) result).isEmpty()) {
                return (String) result;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private String getGTViaInventoryName(TileEntity te) {
        try {
            if (te instanceof net.minecraft.inventory.IInventory) {
                String invName = ((net.minecraft.inventory.IInventory) te).getInventoryName();
                if (invName != null && !invName.isEmpty()) {
                    return invName;
                }
            }
        } catch (Throwable ignored) {}
        return te.getBlockType()
            .getLocalizedName();
    }

    private boolean isGTMultiBlock(Object mte) {
        try {
            Class<?> mteClass = Class.forName("gregtech.api.metatileentity.implementations.MTEMultiBlockBase");
            return mteClass.isInstance(mte);
        } catch (Throwable t) {
            return false;
        }
    }

    private String getGTStringMethod(Object obj, String methodName) {
        try {
            java.lang.reflect.Method method = obj.getClass()
                .getMethod(methodName);
            Object result = method.invoke(obj);
            return result instanceof String ? (String) result : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private Object getGTMetaTileEntity(TileEntity te) {
        try {
            java.lang.reflect.Method getMTE = te.getClass()
                .getMethod("getMetaTileEntity");
            return getMTE.invoke(te);
        } catch (Throwable t) {
            return null;
        }
    }

    private String getPartDisplayName(IPart part) {
        try {
            java.lang.reflect.Method getStack = part.getClass()
                .getMethod("getItemStack");
            Object result = getStack.invoke(part);
            if (result instanceof ItemStack) {
                return ((ItemStack) result).getDisplayName();
            }
        } catch (Throwable ignored) {}
        return part.getClass()
            .getSimpleName();
    }

    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit()
                .getSystemClipboard();
            clipboard.setContents(selection, null);
        } catch (Throwable ignored) {}
    }
}
