package com.spinningitems;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(SpinningItemsConfig.GROUP)
public interface SpinningItemsConfig extends Config
{
    String GROUP = "spinningitems";

    @ConfigItem(
        keyName = "itemInspection",
        name = "Item inspection",
        description = "Add an Inspect option when right-clicking an item"
    )
    default boolean itemInspection()
    {
        return true;
    }

    @ConfigItem(
        keyName = "requireShiftForInspect",
        name = "Require Shift for Inspect",
        description = "Only show the inventory Inspect option while Shift is held"
    )
    default boolean requireShiftForInspect()
    {
        return false;
    }

    @Range(min = 8, max = 256)
    @ConfigItem(
        keyName = "spinSpeed",
        name = "Spin speed",
        description = "How quickly hovered inventory items rotate"
    )
    default int spinSpeed()
    {
        return 48;
    }

    @ConfigItem(
        keyName = "equipmentSpinStyle",
        name = "Equipment rotation",
        description = "Choose left-to-right rotation, a forward tumble, or a mixed spin"
    )
    default EquipmentSpinStyle equipmentSpinStyle()
    {
        return EquipmentSpinStyle.HORIZONTAL_TUMBLE;
    }

    @ConfigItem(
        keyName = "spinFood",
        name = "Food",
        description = "Spin edible items such as food"
    )
    default boolean spinFood()
    {
        return true;
    }

    @ConfigItem(
        keyName = "spinPotions",
        name = "Potions and drinks",
        description = "Spin potions and other drinkable items"
    )
    default boolean spinPotions()
    {
        return true;
    }

    @ConfigItem(
        keyName = "spinArmour",
        name = "Armour and clothing",
        description = "Spin wearable armour, clothing, and jewellery"
    )
    default boolean spinArmour()
    {
        return true;
    }

    @ConfigItem(
        keyName = "spinWeapons",
        name = "Weapons",
        description = "Spin wieldable weapons"
    )
    default boolean spinWeapons()
    {
        return true;
    }

    @ConfigItem(
        keyName = "spinTools",
        name = "Tools",
        description = "Spin common skilling and utility tools"
    )
    default boolean spinTools()
    {
        return true;
    }

    @ConfigItem(
        keyName = "spinOther",
        name = "Other items",
        description = "Spin items which do not match another category"
    )
    default boolean spinOther()
    {
        return true;
    }
}
