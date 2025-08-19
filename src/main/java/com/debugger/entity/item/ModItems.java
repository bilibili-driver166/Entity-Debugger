package com.debugger.entity.item;

import com.debugger.entity.EntityDebugger;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ModItems {

    public static void register(){
        EntityDebugger.LOGGER.info("Entity Debugger>>>Registering Mod Items!");
    }

    public static Item register(String name, Item item){
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.OPERATOR).register(entries -> {
            entries.addAfter(Items.DEBUG_STICK, item); // 放在调试棒后面
        });
        return Registry.register(Registries.ITEM, new Identifier(EntityDebugger.MOD_ID, name), item);
    }

    public static final Item ENTITY_DEBUG_STICK = register("entity_debug_stick", new EntityDebugStick(new FabricItemSettings().rarity(Rarity.EPIC)));
}
