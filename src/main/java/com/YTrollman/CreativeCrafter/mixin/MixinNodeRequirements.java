package com.YTrollman.CreativeCrafter.mixin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.YTrollman.CreativeCrafter.util.IMultipleRequirements;
import com.refinedmods.refinedstorage.api.util.IStackList;
import com.refinedmods.refinedstorage.api.util.StackListEntry;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.NodeRequirements;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

@Mixin(NodeRequirements.class)
public class MixinNodeRequirements implements IMultipleRequirements {

    @Final
    @Shadow
    private Map<Integer, IStackList<ItemStack>> itemRequirements = new LinkedHashMap<>();

    @Final
    @Shadow
    private Map<Integer, Integer> itemsNeededPerCraft = new LinkedHashMap<>();

    @Unique
    @Override
    public List<ItemStack> getMultipleRequirementSets(int numOfRequirements) {

        List<ItemStack> toReturn = new ArrayList<>();

        for (int i = 0; i < itemRequirements.size(); i++) {
            int needed = itemsNeededPerCraft.get(i) * numOfRequirements;

            if (!itemRequirements.get(i).isEmpty()) {
                Iterator<StackListEntry<ItemStack>> it = itemRequirements.get(i).getStacks().iterator();

                while (needed > 0 && it.hasNext()) {
                    ItemStack toUse = it.next().getStack();

                    if (needed < toUse.getCount()) {
                        toReturn.add(ItemHandlerHelper.copyStackWithSize(toUse, needed));

                        needed = 0;
                    } else {

                        toReturn.add(toUse);

                        needed -= toUse.getCount();
                    }
                }
            } else {
                throw new IllegalStateException("Bad!");
            }
        }

        return toReturn;
    }
}
