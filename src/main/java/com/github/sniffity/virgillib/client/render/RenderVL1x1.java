package com.github.sniffity.virgillib.client.render;

import com.github.sniffity.virgillib.VirgilLib;
import com.github.sniffity.virgillib.client.model.ModelVL1x1;
import com.github.sniffity.virgillib.entity.VLEntity1x1;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class RenderVL1x1 extends MobRenderer<VLEntity1x1,ModelVL1x1> {
    public RenderVL1x1(EntityRendererProvider.Context pContext) {
        super(pContext, new ModelVL1x1(pContext.bakeLayer(ModelVL1x1.LAYER_LOCATION)),0F);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull VLEntity1x1 pEntity) {
        return new ResourceLocation(VirgilLib.MODID, "textures/entity/vl_entity_1x1.png");
    }
}