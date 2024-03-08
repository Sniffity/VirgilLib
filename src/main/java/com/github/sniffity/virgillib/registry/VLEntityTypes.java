package com.github.sniffity.virgillib.registry;

import com.github.sniffity.virgillib.VirgilLib;
import com.github.sniffity.virgillib.entity.VLEntity1x1;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class VLEntityTypes {

    public static final DeferredRegister<EntityType<?>> VL_ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, VirgilLib.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<VLEntity1x1>> VL_ENTITY_1x_1x = VL_ENTITY_TYPES.register(
            "vlentity_1x1",
            () -> EntityType.Builder.of(VLEntity1x1::new, MobCategory.CREATURE)
                    .sized(1.0F, 1.0F)
                    .build(new ResourceLocation(VirgilLib.MODID, "vlentity_1x1").toString()));
}