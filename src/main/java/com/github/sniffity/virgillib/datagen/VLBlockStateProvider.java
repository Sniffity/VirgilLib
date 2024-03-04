package com.github.sniffity.virgillib.datagen;

import com.github.sniffity.virgillib.VirgilLib;
import com.github.sniffity.virgillib.registry.VLBlocks;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class VLBlockStateProvider extends BlockStateProvider {

    public VLBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, VirgilLib.MODID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        this.simpleBlockWithItem(
                VLBlocks.GRID_BLOCK.get(),
                models().cubeAll(VLBlocks.GRID_BLOCK.getId().getPath(), modLoc("block/"+VLBlocks.GRID_BLOCK.getId().getPath())));
    }
}