package com.wztwzt.ae2_qof.productionline.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.productionline.MachineConsumptionManager;
import com.wztwzt.ae2_qof.productionline.ProductionLineRecipe;
import com.wztwzt.ae2_qof.productionline.ProductionLineRecipeLoader;

/**
 * 产线聚合器控制器GUI容器
 */
public class ProductionLineControllerGUI extends Container {

    /** 当前配方 */
    private ProductionLineRecipe currentRecipe;
    
    /** 所需机器槽位 */
    private final List<Slot> machineSlots = new ArrayList<>();
    
    /** 是否已激活 */
    private boolean isActivated;
    
    /** 玩家 */
    private final EntityPlayer player;

    public ProductionLineControllerGUI(InventoryPlayer aPlayerInventory, EntityPlayer aPlayer) {
        this.player = aPlayer;
        
        // 添加机器槽位（最多6个）
        for (int i = 0; i < 6; i++) {
            int x = 10 + (i % 3) * 60;
            int y = 40 + (i / 3) * 40;
            
            Slot slot = new Slot(null, i, x, y) {
                @Override
                public boolean isItemValid(ItemStack stack) {
                    return true;
                }
            };
            addSlotToContainer(slot);
            machineSlots.add(slot);
        }
        
        // 添加玩家背包槽位
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlotToContainer(new Slot(aPlayerInventory, j + i * 9 + 9, 8 + j * 18, 166 + i * 18));
            }
        }
        
        // 添加玩家快捷栏槽位
        for (int i = 0; i < 9; i++) {
            addSlotToContainer(new Slot(aPlayerInventory, i, 8 + i * 18, 224));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer aPlayer) {
        return true;
    }

    /**
     * 设置当前配方
     */
    public void setCurrentRecipe(ProductionLineRecipe recipe) {
        this.currentRecipe = recipe;
        this.isActivated = false;
        
        // 检查是否已激活
        if (recipe != null) {
            MachineConsumptionManager manager = MachineConsumptionManager.get(player.worldObj);
            this.isActivated = manager.isMachineConsumed(recipe.getId());
        }
    }

    /**
     * 检查是否插入了所有所需机器
     */
    private boolean checkRequiredMachines() {
        if (currentRecipe == null) {
            return false;
        }
        
        // 统计每个槽位的机器
        java.util.Map<String, Integer> machineCounts = new java.util.HashMap<>();
        for (Slot slot : machineSlots) {
            ItemStack stack = slot.getStack();
            if (stack != null) {
                String machineId = stack.getItem().getUnlocalizedName(stack);
                machineCounts.merge(machineId, stack.stackSize, Integer::sum);
            }
        }
        
        // 检查是否满足所有所需机器
        for (ProductionLineRecipe.RequiredMachine machine : currentRecipe.getRequiredMachines()) {
            int required = machine.getCount();
            int provided = machineCounts.getOrDefault(machine.getMachineId(), 0);
            if (provided < required) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * 消耗所需机器
     */
    private void consumeRequiredMachines() {
        if (currentRecipe == null) {
            return;
        }
        
        // 消耗所需机器
        for (ProductionLineRecipe.RequiredMachine machine : currentRecipe.getRequiredMachines()) {
            int remaining = machine.getCount();
            
            for (Slot slot : machineSlots) {
                ItemStack stack = slot.getStack();
                if (stack != null) {
                    String machineId = stack.getItem().getUnlocalizedName(stack);
                    if (machineId.equals(machine.getMachineId())) {
                        int consume = Math.min(remaining, stack.stackSize);
                        stack.stackSize -= consume;
                        remaining -= consume;
                        
                        if (stack.stackSize <= 0) {
                            slot.putStack(null);
                        }
                        
                        if (remaining <= 0) {
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * 激活配方
     */
    public boolean activateRecipe() {
        if (currentRecipe == null || isActivated) {
            return false;
        }
        
        // 检查是否插入了所有所需机器
        if (!checkRequiredMachines()) {
            return false;
        }
        
        // 消耗机器
        consumeRequiredMachines();
        
        // 标记配方已激活
        MachineConsumptionManager manager = MachineConsumptionManager.get(player.worldObj);
        manager.markMachineConsumed(currentRecipe.getId());
        
        isActivated = true;
        return true;
    }

    /**
     * 获取当前配方
     */
    public ProductionLineRecipe getCurrentRecipe() {
        return currentRecipe;
    }

    /**
     * 是否已激活
     */
    public boolean isActivated() {
        return isActivated;
    }
}
