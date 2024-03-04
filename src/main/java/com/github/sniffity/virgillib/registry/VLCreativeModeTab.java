package com.github.sniffity.virgillib.registry;

import com.github.sniffity.virgillib.VirgilLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.Field;

public class VLCreativeModeTab {
    public static final DeferredRegister<CreativeModeTab> VL_CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VirgilLib.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> VIRGIL_CREATIVE_MODE_TAB = VL_CREATIVE_MODE_TABS.register("virgillib_tab",
            () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.virgillib"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> Items.TARGET.getDefaultInstance())
            .displayItems((parameters, output) ->
            {
                for (Field field : VLItems.class.getFields())
                {
                    if (field.getType() != Item.class) continue;

                    try
                    {
                        Item item = (Item) field.get(null);

                        if (item == null)
                            throw new IllegalStateException("Field " + field.getName() + " cannot be null!");
                        output.accept(new ItemStack(item));
                    }
                    catch (IllegalAccessException e)
                    {
                    }
                }

            }).build());
}