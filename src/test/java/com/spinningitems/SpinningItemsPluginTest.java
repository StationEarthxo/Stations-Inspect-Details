package com.spinningitems;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.junit.Assert;
import org.junit.Test;

public class SpinningItemsPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(SpinningItemsPlugin.class);
        RuneLite.main(args);
    }

    @Test
    public void anglesWrapInRuneScapeUnits()
    {
        Assert.assertEquals(48, SpinningItemsPlugin.spunAngle(0, 48));
        Assert.assertEquals(16, SpinningItemsPlugin.spunAngle(2000, 64));
    }

    @Test
    public void inspectionZoomIsBoundedAndScalesWithModelSize()
    {
        Assert.assertEquals(5000, ItemInspectController.calculateZoom(1));
        Assert.assertEquals(968, ItemInspectController.calculateZoom(160));
        Assert.assertEquals(128, ItemInspectController.calculateZoom(5000));
    }

    @Test
    public void parsesCollectionLogMessagesWithFormatting()
    {
        Assert.assertEquals("Dragon pickaxe",
            CollectionLogInspectManager.parseCollectionItem(
                "New item added to your collection log: <col=ff0000>Dragon pickaxe</col>"));
        Assert.assertNull(CollectionLogInspectManager.parseCollectionItem("You receive a drop: Dragon pickaxe"));
    }

    @Test
    public void classifiesCommonInventoryCategories()
    {
        Assert.assertEquals(SpinCategory.FOOD,
            SpinningItemsPlugin.classify(new String[]{"Eat", null, null}, "Shark"));
        Assert.assertEquals(SpinCategory.POTIONS,
            SpinningItemsPlugin.classify(new String[]{"Drink", null, null}, "Prayer potion(4)"));
        Assert.assertEquals(SpinCategory.WEAPONS,
            SpinningItemsPlugin.classify(new String[]{"Wield", null, null}, "Dragon scimitar"));
        Assert.assertEquals(SpinCategory.ARMOUR,
            SpinningItemsPlugin.classify(new String[]{"Wear", null, null}, "Rune platebody"));
        Assert.assertEquals(SpinCategory.TOOLS,
            SpinningItemsPlugin.classify(new String[]{"Use", null, null}, "Dragon pickaxe"));
        Assert.assertEquals(SpinCategory.OTHER,
            SpinningItemsPlugin.classify(new String[]{"Use", null, null}, "Coins"));
    }

    @Test
    public void onlyNotedItemTemplatesAreExcludedFromSpinning()
    {
        Assert.assertFalse(SpinningItemsPlugin.isNoted(-1));
        Assert.assertTrue(SpinningItemsPlugin.isNoted(799));
    }
}
