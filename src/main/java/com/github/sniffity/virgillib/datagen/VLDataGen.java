package com.github.sniffity.virgillib.datagen;

import com.github.sniffity.virgillib.VirgilLib;
import net.minecraft.data.DataGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
@Mod.EventBusSubscriber(modid = VirgilLib.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class VLDataGen {

    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        ExistingFileHelper efh = event.getExistingFileHelper();

        generator.<VLBlockStateProvider>addProvider(
                event.includeClient(),
                output -> new VLBlockStateProvider(output, efh)
        );


        generator.<VLItemModelProvider>addProvider(
                event.includeClient(),
                output -> new VLItemModelProvider(output, efh)
        );
    }
}