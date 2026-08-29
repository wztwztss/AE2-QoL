package com.wztwzt.ae2_qof.productionline;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;

/**
 * RecipeMap注册：产线聚合器配方池
 */
public class ProductionLineRecipeMaps {

    /**
     * 产线聚合器配方池
     * 支持6物品输入/4物品输出/2流体输入/2流体输出
     */
    public static final RecipeMap<RecipeMapBackend> productionLineRecipes = 
        RecipeMapBuilder.of("gt.recipe.production_line_aggregator")
            .maxIO(6, 4, 2, 2)
            .minInputs(1, 1)
            .build();
}
