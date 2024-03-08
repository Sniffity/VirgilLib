package com.github.sniffity.virgillib;

import com.github.sniffity.virgillib.client.model.ModelVL1x1;
import com.github.sniffity.virgillib.client.render.RenderVL1x1;
import com.github.sniffity.virgillib.entity.VLEntity;
import com.github.sniffity.virgillib.registry.VLBlocks;
import com.github.sniffity.virgillib.registry.VLCreativeModeTab;
import com.github.sniffity.virgillib.registry.VLEntityTypes;
import com.github.sniffity.virgillib.registry.VLItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
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
        modEventBus.addListener(this::onRegisterRenderes);
        modEventBus.addListener(this::onRegisterLayers);
        modEventBus.addListener(this::onAttributeCreate);
        VLBlocks.VL_BLOCKS.register(modEventBus);
        VLItems.VL_ITEMS.register(modEventBus);
        VLEntityTypes.VL_ENTITY_TYPES.register(modEventBus);
        VLCreativeModeTab.VL_CREATIVE_MODE_TABS.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {

    }

    public void onRegisterRenderes(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(VLEntityTypes.VL_ENTITY_1x_1x.get(), RenderVL1x1::new);
    }

    public void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModelVL1x1.LAYER_LOCATION, ModelVL1x1::createBodyLayer);
    }

    public void onAttributeCreate(EntityAttributeCreationEvent event){
        event.put(VLEntityTypes.VL_ENTITY_1x_1x.get(), VLEntity.setupEntityTypeAttributes().build());
    }
}