package com.github.sniffity.virgillib.datagen;

import com.github.sniffity.virgillib.VirgilLib;
import com.github.sniffity.virgillib.registry.VLItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class VLItemModelProvider extends ItemModelProvider {
    public VLItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, VirgilLib.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        this.basicItem(VLItems.VL_INITIALIZER.get());
    }
}
