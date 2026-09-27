/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import com.wztwzt.ae2_qof.apgport.storage.PatternStorage;

/**
 * 仓储 GUI 的 Container — 无槽位，仅传递摘要信息
 */
public class ContainerPatternStorage extends Container {

    public final int patternCount;
    public final String source;
    public final long timestamp;
    public final List<String> previews;

    public ContainerPatternStorage(EntityPlayer player) {
        PatternStorage.StorageSummary summary = PatternStorage.getSummary(player.getUniqueID());
        this.patternCount = summary.count;
        this.source = summary.source;
        this.timestamp = summary.timestamp;
        this.previews = summary.previews;
    }

    /** 客户端用构造 */
    public ContainerPatternStorage(int patternCount, String source, long timestamp, List<String> previews) {
        this.patternCount = patternCount;
        this.source = source;
        this.timestamp = timestamp;
        this.previews = previews != null ? previews : new ArrayList<String>();
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
