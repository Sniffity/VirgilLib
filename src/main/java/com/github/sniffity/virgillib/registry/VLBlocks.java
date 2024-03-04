package com.github.sniffity.virgillib.registry;

import com.github.sniffity.virgillib.VirgilLib;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class VLBlocks {
    public static final DeferredRegister.Blocks VL_BLOCKS = DeferredRegister.createBlocks(VirgilLib.MODID);

    public static final DeferredBlock<Block> GRID_BLOCK = VL_BLOCKS.registerSimpleBlock(
            "grid_block",
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .strength(3.0F,3.0F)
                    .lightLevel((blockState)->15));

    /*
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "examplemod:example_block", combining the namespace and path

*/
}

