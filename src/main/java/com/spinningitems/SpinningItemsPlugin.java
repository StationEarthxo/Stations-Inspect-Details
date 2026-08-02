package com.spinningitems;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "Station's Inspect Details",
    description = "Spin hovered inventory items and inspect their models, stats, and collection-log unlocks",
    tags = {"inventory", "item", "model", "spin", "inspect", "collection log", "cosmetic"}
)
public class SpinningItemsPlugin extends Plugin
{
    private static final int ANGLE_MASK = 2047;
    private static final int UPDATE_INTERVAL = 2;
    private static final String[] TOOL_WORDS = {
        "axe", "pickaxe", "hammer", "chisel", "knife", "saw", "rake", "spade",
        "secateurs", "tinderbox", "pestle", "needle", "shears", "fishing rod",
        "harpoon", "lobster pot", "mortar", "watering can"
    };

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private SpinningItemsOverlay overlay;

    @Inject
    private ItemInspectOverlay inspectOverlay;

    @Inject
    private SpinningItemsConfig config;

    @Inject
    private ItemInspectController itemInspector;

    @Inject
    private CollectionLogInspectManager collectionLogInspector;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private KeyManager keyManager;

    private SpinningItemsOverlay.HoveredSlot activeSlot;
    private int spinOffset;
    private int updateCounter;

    @Provides
    SpinningItemsConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(SpinningItemsConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        overlayManager.add(inspectOverlay);
        mouseManager.registerMouseListener(itemInspector);
        mouseManager.registerMouseWheelListener(itemInspector);
        keyManager.registerKeyListener(itemInspector);
    }

    @Override
    protected void shutDown()
    {
        keyManager.unregisterKeyListener(itemInspector);
        mouseManager.unregisterMouseWheelListener(itemInspector);
        mouseManager.unregisterMouseListener(itemInspector);
        itemInspector.close();
        collectionLogInspector.reset();
        overlayManager.remove(inspectOverlay);
        overlayManager.remove(overlay);
        clearActiveSlot();
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        itemInspector.update();
        collectionLogInspector.update();
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            clearActiveSlot();
            return;
        }

        if (itemInspector.isOpen())
        {
            clearActiveSlot();
            return;
        }

        SpinningItemsOverlay.HoveredSlot hovered = overlay.getHoveredSlot(client.getGameCycle());
        if (hovered == null)
        {
            clearActiveSlot();
            return;
        }

        ItemComposition composition = client.getItemDefinition(hovered.itemId);
        SpinCategory category = composition == null
            ? SpinCategory.OTHER
            : classify(composition.getInventoryActions(), composition.getName());
        if (composition == null || !isEnabled(category))
        {
            clearActiveSlot();
            return;
        }

        if (!hovered.equals(activeSlot))
        {
            activeSlot = hovered;
            spinOffset = 0;
            updateCounter = UPDATE_INTERVAL;
        }

        if (++updateCounter >= UPDATE_INTERVAL)
        {
            updateCounter = 0;
            spinOffset = (spinOffset + config.spinSpeed()) & ANGLE_MASK;
            renderSlot(composition, hovered, category);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        collectionLogInspector.onChatMessage(event);
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        MenuEntry original = event.getMenuEntry();
        if (!config.itemInspection()
            || (config.requireShiftForInspect() && !client.isKeyPressed(KeyCode.KC_SHIFT))
            || !"Examine".equalsIgnoreCase(event.getOption()))
        {
            return;
        }

        int itemId = event.getItemId();
        if (itemId < 0)
        {
            itemId = original.getItemId();
        }
        if (itemId < 0 && original.getWidget() != null)
        {
            itemId = original.getWidget().getItemId();
        }
        if (itemId < 0)
        {
            return;
        }

        final int inspectedItemId = itemId;
        client.createMenuEntry(-1)
            .setOption("Inspect")
            .setTarget(original.getTarget())
            .setType(MenuAction.RUNELITE)
            .setItemId(itemId)
            .onClick(entry -> itemInspector.open(inspectedItemId));
    }

    private void renderSlot(ItemComposition composition, SpinningItemsOverlay.HoveredSlot slot,
        SpinCategory category)
    {
        int originalPitch = composition.getXan2d();
        int originalYaw = composition.getYan2d();
        int originalRoll = composition.getZan2d();
        SpritePixels original = client.createItemSprite(slot.itemId, slot.quantity, 1,
            SpritePixels.DEFAULT_SHADOW_COLOR, quantityMode(slot), false, Constants.CLIENT_DEFAULT_ZOOM);
        if (original == null)
        {
            overlay.clearRenderedSlot();
            return;
        }

        SpritePixels spinning = null;
        try
        {
            applyRotation(composition, category, originalPitch, originalYaw, spinOffset);
            client.getItemSpriteCache().reset();
            spinning = client.createItemSprite(slot.itemId, slot.quantity, 1,
                SpritePixels.DEFAULT_SHADOW_COLOR, quantityMode(slot), false, Constants.CLIENT_DEFAULT_ZOOM);
        }
        finally
        {
            composition.setXan2d(originalPitch);
            composition.setYan2d(originalYaw);
            composition.setZan2d(originalRoll);
            client.getItemSpriteCache().reset();
        }

        if (spinning != null)
        {
            BufferedImage originalImage = original.toBufferedImage();
            BufferedImage spinningImage = spinning.toBufferedImage();
            overlay.setRenderedSlot(slot, originalImage, spinningImage);
        }
    }

    private void applyRotation(ItemComposition composition, SpinCategory category,
        int originalPitch, int originalYaw, int offset)
    {
        boolean equipment = category == SpinCategory.ARMOUR || category == SpinCategory.WEAPONS;
        if (!equipment || config.equipmentSpinStyle() == EquipmentSpinStyle.HORIZONTAL_TUMBLE)
        {
            composition.setYan2d(spunAngle(originalYaw, offset));
            return;
        }

        composition.setXan2d(spunAngle(originalPitch, offset));
        if (config.equipmentSpinStyle() == EquipmentSpinStyle.MIXED)
        {
            composition.setYan2d(spunAngle(originalYaw, offset / 2));
        }
    }

    private static int quantityMode(SpinningItemsOverlay.HoveredSlot slot)
    {
        if (slot.quantityMode == ItemQuantityMode.NEVER
            || slot.quantityMode == ItemQuantityMode.ALWAYS
            || slot.quantityMode == ItemQuantityMode.STACKABLE)
        {
            return slot.quantityMode;
        }
        return ItemQuantityMode.STACKABLE;
    }

    private boolean isEnabled(SpinCategory category)
    {
        switch (category)
        {
            case FOOD:
                return config.spinFood();
            case POTIONS:
                return config.spinPotions();
            case ARMOUR:
                return config.spinArmour();
            case WEAPONS:
                return config.spinWeapons();
            case TOOLS:
                return config.spinTools();
            default:
                return config.spinOther();
        }
    }

    private void clearActiveSlot()
    {
        activeSlot = null;
        spinOffset = 0;
        updateCounter = 0;
        overlay.clearRenderedSlot();
    }

    static SpinCategory classify(String[] actions, String name)
    {
        if (hasAction(actions, "Drink", "Sip"))
        {
            return SpinCategory.POTIONS;
        }
        if (hasAction(actions, "Eat", "Consume", "Bite", "Chew"))
        {
            return SpinCategory.FOOD;
        }
        if (hasAction(actions, "Wield"))
        {
            return SpinCategory.WEAPONS;
        }
        if (hasAction(actions, "Wear", "Equip"))
        {
            return SpinCategory.ARMOUR;
        }

        String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (Arrays.stream(TOOL_WORDS).anyMatch(lowerName::contains))
        {
            return SpinCategory.TOOLS;
        }
        return SpinCategory.OTHER;
    }

    private static boolean hasAction(String[] actions, String... expected)
    {
        if (actions == null)
        {
            return false;
        }
        for (String action : actions)
        {
            if (action == null)
            {
                continue;
            }
            for (String value : expected)
            {
                if (value.equalsIgnoreCase(action))
                {
                    return true;
                }
            }
        }
        return false;
    }

    static int spunAngle(int original, int offset)
    {
        return (original + offset) & ANGLE_MASK;
    }
}
