package com.wztwzt.ae2_qof.mixin.gt;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.hatch.thread.Ae2qolThreadEngine;
import com.wztwzt.ae2_qof.network.ThreadStatusPacket;

import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * 3.25.0（提交 2/2）：把线程进度追进 **WAILA**。
 *
 * <h2>注入点为什么是这里（证据）</h2>
 * GT 自己的 Waila 提供器 {@code gregtech/crossmod/waila/GregtechTEWailaDataProvider} 的
 * {@code getWailaBody(itemStack, currenttip, accessor, config)} 会调用
 * {@code ((IGregtechWailaProvider) tile).getWailaBody(...)}，而 {@code tile} 就是
 * {@code BaseMetaTileEntity}（其 {@code getWailaBody} 在源码 :617）⇒ 在它的 RETURN 追加行即可覆盖
 * **所有 GT 机器**（含第三方：它们同样被 BaseMetaTileEntity 包着）。
 *
 * <h2>为什么不引用 waila 类型</h2>
 * 本模组的编译依赖里**没有 waila**。这里只声明目标参数的前两个（{@code ItemStack} 与
 * {@code List<String>}，都是原版/JDK 类型）加 {@code CallbackInfo} —— Mixin 允许省略尾部参数，
 * 因此既不需要 {@code IWailaDataAccessor}，也能直接改那条 tip 列表。
 * 潜行展开同理：客户端玩家自己就能判断，不需要 accessor。
 *
 * <h2>显示口径（线框稿已确认）</h2>
 * 常态：1 行汇总 + **最慢的 2 条**；潜行（Shift）：展开到最多 **8 条**（发送侧上限也是 8）。
 * 取不到快照（机器没装我们的维护仓 / 线程=1 / 停转超过 2 秒）时**一行都不追加**，不影响其它信息。
 */
@Mixin(value = BaseMetaTileEntity.class, remap = false)
public abstract class MixinBaseMetaTileEntityWaila {

    /** 常态显示几条线程明细。 */
    private static final int NORMAL_ROWS = 2;
    /** 潜行展开几条（与 {@link ThreadStatusPacket#MAX_ROWS} 一致）。 */
    private static final int SNEAK_ROWS = ThreadStatusPacket.MAX_ROWS;

    /**
     * 3.25.0-fix5：**再次停用注入 —— 这次是因为它让游戏启动即崩**。
     * <p>实测崩溃（`crash-2026-10-05_13.51.01-client.txt`，Description: Initializing game）：
     * <pre>
     * InjectionError: Critical injection failure: Callback method ae2qol$appendThreadLines(...)
     * Suppressed: SugarApplicationException: Failed to validate sugar @Local(argsOnly = true, index = 1) List
     * → LoaderException: NoClassDefFoundError: gregtech/api/metatileentity/BaseMetaTileEntity
     * </pre>
     * 即：本包的 unimixins 0.3.1 **不接受这种 `@Local` 写法**，而注入失败被当作**致命**错误
     * ⇒ `BaseMetaTileEntity` 的类转换中断 ⇒ 连 GT 的类都加载不了 ⇒ 启动崩溃。
     * <p>因此这里恢复成"**不注入**"（方法体保留作参考，不再声明 {@code @Inject}）。
     * WAILA 线程行的正式方案改为**加 compileOnly 的 waila 依赖 + 写全 4 个参数**
     * （不使用 MixinExtras 的 sugar），等下一轮实施；在那之前 WAILA 只是**少几行**，不影响启动。
     */
    @SuppressWarnings("unused")
    private void ae2qol$appendThreadLines(ItemStack itemStack, List<String> currentTip) {
        TileEntity self = (TileEntity) (Object) this;
        World world = self.getWorldObj();
        if (world == null || !world.isRemote) return;

        ThreadStatusPacket packet = ThreadStatusPacket
            .lookup(world, self.xCoord, self.yCoord, self.zCoord);
        if (packet == null) return;

        currentTip.add(
            "\u00a7b" + tr("ae2_qof.threads.waila.summary") + " " + packet.active + "/" + packet.total + "  " + packet.parallelSum + "  " + packet.powerSum + " EU/t");
        if (packet.active <= 0) return;

        boolean sneaking = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            sneaking = mc.thePlayer.isSneaking();
        }
        int show = Math.min(sneaking ? SNEAK_ROWS : NORMAL_ROWS, packet.rowIndex.length);
        for (int i = 0; i < show; i++) {
            String name = packet.rowName[i] == null || packet.rowName[i].isEmpty() ? "-" : packet.rowName[i];
            currentTip.add(
                "\u00a7b#" + packet.rowIndex[i] + " \u00a7r" + name + "  " + packet.rowPercent[i] + "%"
                    + (packet.rowRemain[i] > 0 ? " (" + tr("ae2_qof.threads.remain_short") + packet.rowRemain[i] + "t)" : "")
                    + "  \u00a79" + packet.rowParallel[i]
                    + " \u00a77"
                    + tr(stateKey(packet.rowState[i])));
        }
    }

    private static String stateKey(int state) {
        if (state == Ae2qolThreadEngine.ST_RUNNING) return "ae2_qof.threads.state.running";
        if (state == Ae2qolThreadEngine.ST_STARVED) return "ae2_qof.threads.state.starved";
        if (state == Ae2qolThreadEngine.ST_POWER) return "ae2_qof.threads.state.power_down";
        if (state == Ae2qolThreadEngine.ST_OUTPUT_FULL) return "ae2_qof.threads.state.output_full";
        if (state == Ae2qolThreadEngine.ST_OFF) return "ae2_qof.threads.state.off";
        return "ae2_qof.threads.state.idle";
    }

    private static String tr(String key) {
        return net.minecraft.util.StatCollector.translateToLocal(key);
    }
}
