package com.YTrollman.CreativeCrafter.mixin;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.YTrollman.CreativeCrafter.CreativeCrafter;
import com.YTrollman.CreativeCrafter.node.CreativeCrafterNetworkNode;
import com.YTrollman.CreativeCrafter.util.IMultipleRequirements;
import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern;
import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.storage.disk.IStorageDisk;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.IoUtil;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.CraftingNode;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.Node;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.NodeList;
import com.refinedmods.refinedstorage.apiimpl.autocrafting.task.v6.node.NodeListener;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

@Mixin(CraftingNode.class)
public abstract class MixinCraftingNode extends Node {

    @Shadow
    @Final
    private NonNullList<ItemStack> recipe;

    public MixinCraftingNode(ICraftingPattern pattern, boolean root, NonNullList<ItemStack> recipe) {
        super(pattern, root);
        this.recipe = recipe;
    }

    @Inject(method = "update", at = @At("HEAD"), remap = false, cancellable = true)
    public void update(INetwork network, int ticks, NodeList nodes, IStorageDisk<ItemStack> internalStorage,
            IStorageDisk<FluidStack> internalFluidStorage, NodeListener listener, CallbackInfo ci) {
        CreativeCrafter.LOGGER.info("Hello from mixin land!");

        // It's not good we have to iterate twice over this, but I couldn't think of anything else
        var containers = network.getCraftingManager().getAllContainers(getPattern());
        var hasCreativeCrafter = containers.stream().anyMatch((container) -> {
            return container instanceof CreativeCrafterNetworkNode;
        });

        if(hasCreativeCrafter) {
            Set<ICraftingPatternContainer> networkContainerPatterns = network.getCraftingManager().getAllContainers(getPattern());
            for (ICraftingPatternContainer container : networkContainerPatterns) {
                int interval = container.getUpdateInterval();
                if (interval < 0) {
                    throw new IllegalStateException(container + " has an update interval of < 0");
                }

                if (interval == 0 || ticks % interval == 0) {
                    for (int i = 0; i < container.getMaximumSuccessfulCraftingUpdates(); i++) {
                        var mRequirements = (IMultipleRequirements)requirements;

                        if (IoUtil.extractFromInternalItemStorage(requirements.getSingleItemRequirementSet(true), internalStorage, Action.SIMULATE) != null) {
                            List<ItemStack> requirementSet;
                            if(quantity == 1) {
                                requirementSet = requirements.getSingleItemRequirementSet(false);
                            } else {
                                requirementSet = mRequirements.getMultipleRequirementSets(quantity);
                            }
                            
                            IoUtil.extractFromInternalItemStorage(requirementSet, internalStorage, Action.PERFORM);

                            ItemStack output = getPattern().getOutput(recipe);
                            output.setCount(output.getCount() * quantity);

                            if (!isRoot()) {
                                internalStorage.insert(output, output.getCount(), Action.PERFORM);
                            } else {
                                ItemStack remainder = network.insertItem(output, output.getCount(), Action.PERFORM);

                                internalStorage.insert(remainder, remainder.getCount() * quantity, Action.PERFORM);
                            }

                            // Byproducts need to always be inserted in the internal storage for later reuse further in the task.
                            // Regular outputs can be inserted into the network *IF* it's a root since it's *NOT* expected to be used later on.
                            NonNullList<ItemStack> byproducts = getPattern().getByproducts(recipe);
                            for (ItemStack byp : byproducts) {
                                internalStorage.insert(byp, byp.getCount(), Action.PERFORM);
                            }

                            for(; i < quantity; i++) {
                                next();
                                listener.onSingleDone(this);
                            }

                            if (getQuantity() <= 0) {
                                listener.onAllDone(this);
                                return;
                            }
                        } else {
                            break;
                        }
                    }
                }
            }

            ci.cancel();
        }
    }
}
