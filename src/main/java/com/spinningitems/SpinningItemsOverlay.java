package com.spinningitems;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

@Singleton
final class SpinningItemsOverlay extends WidgetItemOverlay
{
    private static final int MAX_STALE_CYCLES = 3;
    private static final int SPRITE_WIDTH = 36;
    private static final int SPRITE_HEIGHT = 32;
    private static final int SEARCH_RADIUS = 6;

    private final Client client;
    private volatile HoveredSlot hoveredSlot;
    private volatile int hoverCycle = Integer.MIN_VALUE;
    private volatile RenderedSlot renderedSlot;

    @Inject
    private SpinningItemsOverlay(Client client)
    {
        this.client = client;
        showOnInterfaces(InterfaceID.INVENTORY);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        hoveredSlot = null;
        hoverCycle = client.getGameCycle();
        return super.render(graphics);
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem item)
    {
        if (item.getDraggingCanvasBounds() != null)
        {
            return;
        }

        Point mouse = client.getMouseCanvasPosition();
        Rectangle bounds = item.getCanvasBounds();
        if (mouse == null || bounds == null || !bounds.contains(mouse.getX(), mouse.getY()))
        {
            return;
        }

        HoveredSlot slot = new HoveredSlot(itemId, item.getQuantity(), item.getWidget().getItemQuantityMode());
        hoveredSlot = slot;

        RenderedSlot rendered = renderedSlot;
        if (rendered != null && rendered.slot.equals(slot))
        {
            eraseOriginalIcon(graphics, bounds.x, bounds.y, rendered.originalSprite);
            graphics.drawImage(rendered.spinningSprite, bounds.x, bounds.y, null);
        }
    }

    HoveredSlot getHoveredSlot(int currentCycle)
    {
        int age = currentCycle - hoverCycle;
        return age >= 0 && age <= MAX_STALE_CYCLES ? hoveredSlot : null;
    }

    void setRenderedSlot(HoveredSlot slot, BufferedImage originalSprite, BufferedImage spinningSprite)
    {
        renderedSlot = new RenderedSlot(slot, originalSprite, spinningSprite);
    }

    void clearRenderedSlot()
    {
        renderedSlot = null;
    }

    private void eraseOriginalIcon(Graphics2D graphics, int canvasX, int canvasY, BufferedImage original)
    {
        BufferProvider provider = client.getBufferProvider();
        if (provider == null || original == null)
        {
            return;
        }

        int[] frame = provider.getPixels();
        int frameWidth = provider.getWidth();
        int frameHeight = provider.getHeight();
        if (frame == null || frameWidth <= 0 || frameHeight <= 0)
        {
            return;
        }

        BufferedImage patch = new BufferedImage(SPRITE_WIDTH, SPRITE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SPRITE_HEIGHT; y++)
        {
            for (int x = 0; x < SPRITE_WIDTH; x++)
            {
                if ((original.getRGB(x, y) >>> 24) == 0)
                {
                    continue;
                }

                int replacement = findNearbyBackground(frame, frameWidth, frameHeight, original, canvasX, canvasY, x, y);
                patch.setRGB(x, y, 0xFF000000 | replacement);
            }
        }
        graphics.drawImage(patch, canvasX, canvasY, null);
    }

    private static int findNearbyBackground(int[] frame, int frameWidth, int frameHeight,
        BufferedImage mask, int canvasX, int canvasY, int x, int y)
    {
        for (int radius = 1; radius <= SEARCH_RADIUS; radius++)
        {
            for (int dy = -radius; dy <= radius; dy++)
            {
                for (int dx = -radius; dx <= radius; dx++)
                {
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius)
                    {
                        continue;
                    }

                    int maskX = x + dx;
                    int maskY = y + dy;
                    int sampleX = canvasX + maskX;
                    int sampleY = canvasY + maskY;
                    if (maskX < 0 || maskX >= SPRITE_WIDTH || maskY < 0 || maskY >= SPRITE_HEIGHT
                        || sampleX < 0 || sampleX >= frameWidth || sampleY < 0 || sampleY >= frameHeight)
                    {
                        continue;
                    }
                    if ((mask.getRGB(maskX, maskY) >>> 24) == 0)
                    {
                        return frame[sampleY * frameWidth + sampleX] & 0xFFFFFF;
                    }
                }
            }
        }

        int sampleX = Math.max(0, Math.min(frameWidth - 1, canvasX));
        int sampleY = Math.max(0, Math.min(frameHeight - 1, canvasY));
        return frame[sampleY * frameWidth + sampleX] & 0xFFFFFF;
    }

    static final class HoveredSlot
    {
        final int itemId;
        final int quantity;
        final int quantityMode;

        private HoveredSlot(int itemId, int quantity, int quantityMode)
        {
            this.itemId = itemId;
            this.quantity = quantity;
            this.quantityMode = quantityMode;
        }

        @Override
        public boolean equals(Object object)
        {
            if (this == object)
            {
                return true;
            }
            if (!(object instanceof HoveredSlot))
            {
                return false;
            }
            HoveredSlot other = (HoveredSlot) object;
            return itemId == other.itemId && quantity == other.quantity && quantityMode == other.quantityMode;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(itemId, quantity, quantityMode);
        }
    }

    private static final class RenderedSlot
    {
        private final HoveredSlot slot;
        private final BufferedImage originalSprite;
        private final BufferedImage spinningSprite;

        private RenderedSlot(HoveredSlot slot, BufferedImage originalSprite, BufferedImage spinningSprite)
        {
            this.slot = slot;
            this.originalSprite = originalSprite;
            this.spinningSprite = spinningSprite;
        }
    }
}
