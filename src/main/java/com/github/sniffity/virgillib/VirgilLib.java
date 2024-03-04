package com.github.sniffity.virgillib;

import com.github.sniffity.virgillib.datagen.VLBlockStateProvider;
import com.github.sniffity.virgillib.registry.VLBlocks;
import com.github.sniffity.virgillib.registry.VLCreativeModeTab;
import com.github.sniffity.virgillib.registry.VLItems;
import com.mojang.logging.LogUtils;
import net.minecraft.data.DataGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.slf4j.Logger;

@Mod(VirgilLib.MODID)
public class VirgilLib
{
    public static final String MODID = "virgillib";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VirgilLib(IEventBus modEventBus)
    {
        modEventBus.addListener(this::commonSetup);
        VLBlocks.VL_BLOCKS.register(modEventBus);
        VLItems.VL_ITEMS.register(modEventBus);
        VLCreativeModeTab.VL_CREATIVE_MODE_TABS.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {

    }

    @SubscribeEvent
    public void gatherData(GatherDataEvent event)
    {
        DataGenerator generator = event.getGenerator();
        ExistingFileHelper efh = event.getExistingFileHelper();

        generator.<VLBlockStateProvider>addProvider(
                event.includeClient(),
                output -> new VLBlockStateProvider(output, efh)
        );
    }
}