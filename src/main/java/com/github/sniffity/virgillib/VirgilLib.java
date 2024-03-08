package com.github.sniffity.virgillib;

import com.github.sniffity.virgillib.registry.VLBlocks;
import com.github.sniffity.virgillib.registry.VLCreativeModeTab;
import com.github.sniffity.virgillib.registry.VLItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(VirgilLib.MODID)
public class VirgilLib
{
    public static final String MODID = "virgillib";
    private static final Logger LOGGER = LogUtils.getLogger();

    //ToDo: Caching
    public VirgilLib(IEventBus modEventBus)
    {
        modEventBus.addListener(this::commonSetup);
        VLBlocks.VL_BLOCKS.register(modEventBus);
        VLItems.VL_ITEMS.register(modEventBus);
        VLCreativeModeTab.VL_CREATIVE_MODE_TABS.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {

    }
}