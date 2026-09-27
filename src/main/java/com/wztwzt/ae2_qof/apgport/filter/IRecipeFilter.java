/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.filter;

import com.wztwzt.ae2_qof.apgport.recipe.RecipeEntry;

/**
 * 配方过滤器接口
 */
public interface IRecipeFilter {

    /**
     * 测试配方是否满足过滤条件
     *
     * @param recipe 配方
     * @return true 表示保留该配方
     */
    boolean matches(RecipeEntry recipe);

    /**
     * 过滤器描述（用于 GUI 显示）
     */
    String getDescription();
}
