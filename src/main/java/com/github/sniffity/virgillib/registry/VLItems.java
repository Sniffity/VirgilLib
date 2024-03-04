package com.github.sniffity.virgillib.registry;

import com.github.sniffity.virgillib.VirgilLib;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class VLItems {
    public static final DeferredRegister.Items VL_ITEMS = DeferredRegister.createItems(VirgilLib.MODID);

    public static DeferredItem<BlockItem> GRID_BLOCK = VL_ITEMS.registerSimpleBlockItem(VLBlocks.GRID_BLOCK);
}
