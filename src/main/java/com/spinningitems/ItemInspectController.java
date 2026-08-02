package com.spinningitems;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Model;
import net.runelite.api.SpritePixels;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseWheelListener;

@Singleton
final class ItemInspectController implements MouseListener, MouseWheelListener, KeyListener
{
    private static final int ANGLE_MASK = 2047;
    private static final int MIN_SPRITE_ZOOM = 256;
    private static final int MAX_SPRITE_ZOOM = 1024;
    private final Client client;
    private final ClientThread clientThread;
    private final ItemManager itemManager;
    private volatile boolean open;
    private volatile boolean dragging;
    private volatile boolean refreshQueued;
    private volatile BufferedImage itemImage;
    private volatile ModelSnapshot modelSnapshot;
    private volatile boolean celebration;
    private volatile List<String> statLines = Collections.emptyList();
    private volatile String itemName = "";
    private volatile Rectangle panelBounds = new Rectangle();
    private int itemId = -1;
    private int dragX;
    private int dragY;
    private int yaw;
    private int pitch;
    private int roll;
    private int orbitYaw;
    private int orbitPitch;
    private int spriteZoom = Constants.CLIENT_DEFAULT_ZOOM;

    @Inject
    private ItemInspectController(Client client, ClientThread clientThread, ItemManager itemManager)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.itemManager = itemManager;
    }

    boolean isOpen() { return open; }
    BufferedImage getItemImage() { return itemImage; }
    String getItemName() { return itemName; }
    ModelSnapshot getModelSnapshot() { return modelSnapshot; }
    int getYaw() { return yaw; }
    int getPitch() { return pitch; }
    int getRoll() { return roll; }
    int getOrbitYaw() { return orbitYaw; }
    int getOrbitPitch() { return orbitPitch; }
    double getZoomScale() { return spriteZoom / (double) Constants.CLIENT_DEFAULT_ZOOM; }
    boolean isCelebration() { return celebration; }
    List<String> getStatLines() { return statLines; }
    void setPanelBounds(Rectangle bounds) { panelBounds = bounds == null ? new Rectangle() : new Rectangle(bounds); }

    void open(int inspectedItemId)
    {
        open(inspectedItemId, false);
    }

    void open(int inspectedItemId, boolean celebrate)
    {
        if (inspectedItemId < 0 || client.getGameState() != GameState.LOGGED_IN) return;
        ItemComposition composition = client.getItemDefinition(inspectedItemId);
        if (composition == null) return;
        itemId = inspectedItemId;
        itemName = composition.getName();
        pitch = composition.getXan2d() & ANGLE_MASK;
        yaw = composition.getYan2d() & ANGLE_MASK;
        roll = composition.getZan2d() & ANGLE_MASK;
        orbitYaw = 0;
        orbitPitch = 0;
        spriteZoom = Constants.CLIENT_DEFAULT_ZOOM;
        dragging = false;
        celebration = celebrate;
        statLines = buildStatLines(inspectedItemId, composition);
        open = true;
        modelSnapshot = snapshotModel(composition);
        refreshImage();
    }

    void close()
    {
        open = false;
        dragging = false;
        refreshQueued = false;
        itemImage = null;
        modelSnapshot = null;
        celebration = false;
        statLines = Collections.emptyList();
        itemId = -1;
    }

    void update()
    {
        if (open && client.getGameState() != GameState.LOGGED_IN)
        {
            close();
        }
    }

    private void queueImageRefresh()
    {
        if (refreshQueued) return;
        refreshQueued = true;
        clientThread.invokeLater(() ->
        {
            refreshQueued = false;
            refreshImage();
        });
    }

    private void refreshImage()
    {
        if (!open || itemId < 0) return;
        ItemComposition composition = client.getItemDefinition(itemId);
        if (composition == null)
        {
            close();
            return;
        }
        int originalPitch = composition.getXan2d();
        int originalYaw = composition.getYan2d();
        int originalRoll = composition.getZan2d();
        try
        {
            composition.setXan2d((pitch + orbitPitch) & ANGLE_MASK);
            composition.setYan2d((yaw + orbitYaw) & ANGLE_MASK);
            composition.setZan2d(roll & ANGLE_MASK);
            client.getItemSpriteCache().reset();
            SpritePixels sprite = client.createItemSprite(itemId, 1, 1,
                SpritePixels.DEFAULT_SHADOW_COLOR, ItemQuantityMode.NEVER, false, spriteZoom);
            itemImage = sprite == null ? null : sprite.toBufferedImage();
        }
        finally
        {
            composition.setXan2d(originalPitch);
            composition.setYan2d(originalYaw);
            composition.setZan2d(originalRoll);
            client.getItemSpriteCache().reset();
        }
    }

    private ModelSnapshot snapshotModel(ItemComposition composition)
    {
        short[] replace = composition.getColorToReplace();
        short[] replaceWith = composition.getColorToReplaceWith();
        Model model = replace != null && replaceWith != null && replace.length == replaceWith.length
            ? client.loadModel(composition.getInventoryModel(), replace, replaceWith)
            : client.loadModel(composition.getInventoryModel());
        if (model == null || model.getVerticesCount() <= 0 || model.getFaceCount() <= 0)
        {
            return null;
        }
        if (model.getFaceColors1() == null || model.getFaceColors2() == null || model.getFaceColors3() == null)
        {
            return null;
        }
        return new ModelSnapshot(
            model.getVerticesX().clone(), model.getVerticesY().clone(), model.getVerticesZ().clone(),
            model.getFaceIndices1().clone(), model.getFaceIndices2().clone(), model.getFaceIndices3().clone(),
            model.getFaceColors1().clone(), model.getFaceColors2().clone(), model.getFaceColors3().clone(),
            model.getFaceTransparencies() == null ? null : model.getFaceTransparencies().clone());
    }

    private List<String> buildStatLines(int inspectedItemId, ItemComposition composition)
    {
        List<String> lines = new ArrayList<>();
        List<String> traits = new ArrayList<>();
        if (composition.isMembers()) traits.add("Members");
        if (composition.isTradeable()) traits.add("Tradeable");
        if (composition.isStackable()) traits.add("Stackable");
        if (!traits.isEmpty()) lines.add(String.join("  •  ", traits));

        ItemStats stats = itemManager.getItemStats(inspectedItemId);
        if (stats == null)
        {
            if (composition.getPrice() > 0) lines.add("Guide price  " + formatNumber(composition.getPrice()) + " gp");
            return Collections.unmodifiableList(lines);
        }

        ItemEquipmentStats equipment = stats.getEquipment();
        if (equipment != null)
        {
            lines.add("ATK  Stb " + signed(equipment.getAstab()) + "  Sls " + signed(equipment.getAslash())
                + "  Crs " + signed(equipment.getAcrush()) + "  Mag " + signed(equipment.getAmagic())
                + "  Rng " + signed(equipment.getArange()));
            lines.add("DEF  Stb " + signed(equipment.getDstab()) + "  Sls " + signed(equipment.getDslash())
                + "  Crs " + signed(equipment.getDcrush()) + "  Mag " + signed(equipment.getDmagic())
                + "  Rng " + signed(equipment.getDrange()));
            lines.add("POWER  Str " + signed(equipment.getStr()) + "  R.Str " + signed(equipment.getRstr())
                + "  M.Dmg " + signedPercent(equipment.getMdmg()) + "  Pray " + signed(equipment.getPrayer()));
            List<String> equipmentInfo = new ArrayList<>();
            if (equipment.getAspeed() > 0) equipmentInfo.add("Speed " + equipment.getAspeed());
            if (equipment.isTwoHanded()) equipmentInfo.add("Two-handed");
            if (!equipmentInfo.isEmpty()) lines.add(String.join("  •  ", equipmentInfo));
        }
        List<String> general = new ArrayList<>();
        if (Math.abs(stats.getWeight()) > 0.001) general.add(String.format(Locale.ROOT, "Weight %.1f kg", stats.getWeight()));
        if (stats.getGeLimit() > 0) general.add("GE limit " + formatNumber(stats.getGeLimit()));
        if (composition.getPrice() > 0) general.add("Guide " + formatNumber(composition.getPrice()) + " gp");
        if (!general.isEmpty()) lines.add(String.join("  •  ", general));
        return Collections.unmodifiableList(lines);
    }

    private static String signed(int value)
    {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private static String signedPercent(float value)
    {
        return (value >= 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f%%", value);
    }

    private static String formatNumber(int value)
    {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private void closeLater() { clientThread.invokeLater(this::close); }
    private static MouseEvent consume(MouseEvent event) { event.consume(); return event; }

    @Override
    public MouseEvent mousePressed(MouseEvent event)
    {
        if (!open) return event;
        if (event.getButton() == MouseEvent.BUTTON1 && !panelBounds.contains(event.getPoint()))
        {
            closeLater();
            return consume(event);
        }
        if (event.getButton() == MouseEvent.BUTTON1)
        {
            dragging = true;
            dragX = event.getX();
            dragY = event.getY();
        }
        return consume(event);
    }

    @Override public MouseEvent mouseReleased(MouseEvent event) { if (!open) return event; dragging = false; return consume(event); }

    @Override
    public MouseEvent mouseDragged(MouseEvent event)
    {
        if (!open) return event;
        if (dragging)
        {
            int dx = event.getX() - dragX;
            int dy = event.getY() - dragY;
            dragX = event.getX();
            dragY = event.getY();
            orbitYaw = (orbitYaw + dx * 5) & ANGLE_MASK;
            orbitPitch = clamp(orbitPitch + dy * 5, -420, 420);
            queueImageRefresh();
        }
        return consume(event);
    }

    @Override
    public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
    {
        if (!open) return event;
        spriteZoom = clamp(spriteZoom - event.getWheelRotation() * 48, MIN_SPRITE_ZOOM, MAX_SPRITE_ZOOM);
        queueImageRefresh();
        event.consume();
        return event;
    }

    @Override public MouseEvent mouseClicked(MouseEvent event) { return open ? consume(event) : event; }
    @Override public MouseEvent mouseEntered(MouseEvent event) { return event; }
    @Override public MouseEvent mouseExited(MouseEvent event) { dragging = false; return event; }
    @Override public MouseEvent mouseMoved(MouseEvent event) { return event; }

    @Override
    public void keyPressed(KeyEvent event)
    {
        if (open && event.getKeyCode() == KeyEvent.VK_ESCAPE)
        {
            event.consume();
            closeLater();
        }
    }

    @Override public void keyReleased(KeyEvent event) { }
    @Override public void keyTyped(KeyEvent event) { }

    static int calculateZoom(int modelExtent) { return clamp(155_000 / Math.max(1, modelExtent), 128, 5000); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    static final class ModelSnapshot
    {
        final float[] x;
        final float[] y;
        final float[] z;
        final int[] face1;
        final int[] face2;
        final int[] face3;
        final int[] color1;
        final int[] color2;
        final int[] color3;
        final byte[] transparencies;

        private ModelSnapshot(float[] x, float[] y, float[] z, int[] face1, int[] face2, int[] face3,
            int[] color1, int[] color2, int[] color3, byte[] transparencies)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.face1 = face1;
            this.face2 = face2;
            this.face3 = face3;
            this.color1 = color1;
            this.color2 = color2;
            this.color3 = color3;
            this.transparencies = transparencies;
        }
    }
}
