package com.spinningitems;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

@Singleton
final class ItemInspectOverlay extends Overlay
{
    private static final int MAX_PANEL_WIDTH = 360;
    private static final int MAX_PANEL_HEIGHT = 400;
    private final Client client;
    private final ItemInspectController controller;
    private BufferedImage cachedModelImage;
    private ItemInspectController.ModelSnapshot cachedModel;
    private int cachedPitch;
    private int cachedYaw;
    private int cachedRoll;
    private int cachedOrbitPitch;
    private int cachedOrbitYaw;
    private int cachedSize;
    private double cachedZoom;

    @Inject
    private ItemInspectOverlay(Client client, ItemInspectController controller)
    {
        this.client = client;
        this.controller = controller;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(OverlayPriority.HIGHEST);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!controller.isOpen())
        {
            cachedModelImage = null;
            cachedModel = null;
            return null;
        }
        int canvasWidth = client.getCanvasWidth();
        int canvasHeight = client.getCanvasHeight();
        if (canvasWidth <= 0 || canvasHeight <= 0) return null;
        int panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(260, canvasWidth - 36));
        int panelHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(320, canvasHeight - 36));
        int panelX = (canvasWidth - panelWidth) / 2;
        int panelY = (canvasHeight - panelHeight) / 2;
        Rectangle panel = new Rectangle(panelX, panelY, panelWidth, panelHeight);
        controller.setPanelBounds(panel);

        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(new Color(0, 0, 0, 188));
        graphics.fillRect(0, 0, canvasWidth, canvasHeight);
        graphics.setColor(new Color(18, 18, 20, 246));
        graphics.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 18, 18);
        graphics.setStroke(new BasicStroke(2f));
        graphics.setColor(new Color(104, 84, 54, 230));
        graphics.drawRoundRect(panelX, panelY, panelWidth - 1, panelHeight - 1, 18, 18);

        List<String> statLines = controller.getStatLines();
        boolean hasStats = !statLines.isEmpty();
        drawItem(graphics, panel, hasStats);
        if (controller.isCelebration())
        {
            drawCelebration(graphics, panel);
        }
        int nameBaseline = hasStats ? panelY + 247 : panelY + panelHeight - 55;
        drawCenteredText(graphics, controller.getItemName(), new Font(Font.SANS_SERIF, Font.BOLD, 17),
            new Color(255, 152, 31), panelX + panelWidth / 2, nameBaseline);
        if (hasStats)
        {
            drawStats(graphics, statLines, panelX + panelWidth / 2, nameBaseline + 20, panelY + panelHeight - 42);
        }
        drawCenteredText(graphics, "Drag to rotate  •  Scroll to zoom  •  Esc to close",
            new Font(Font.SANS_SERIF, Font.PLAIN, 11), new Color(220, 220, 220),
            panelX + panelWidth / 2, panelY + panelHeight - 24);
        return new Dimension(canvasWidth, canvasHeight);
    }

    private void drawItem(Graphics2D graphics, Rectangle panel, boolean hasStats)
    {
        int availableWidth = panel.width - 42;
        int availableHeight = hasStats ? 220 : panel.height - 108;
        int size = Math.max(150, Math.min(availableWidth, availableHeight));
        int x = panel.x + (panel.width - size) / 2;
        int y = panel.y + 16 + Math.max(0, (availableHeight - size) / 2);
        graphics.setColor(new Color(7, 7, 9, 190));
        graphics.fillOval(x + size / 5, y + size - 38, size * 3 / 5, 35);

        ItemInspectController.ModelSnapshot model = controller.getModelSnapshot();
        if (model != null)
        {
            drawSmoothModel(graphics, model, x, y, size);
            return;
        }

        BufferedImage image = controller.getItemImage();
        if (image == null)
        {
            drawCenteredText(graphics, "Loading item…", new Font(Font.SANS_SERIF, Font.PLAIN, 16),
                Color.LIGHT_GRAY, panel.x + panel.width / 2, y + size / 2);
            return;
        }
        Object oldInterpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(image, x, y, size, size, null);
        if (oldInterpolation != null) graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
    }

    private static void drawStats(Graphics2D graphics, List<String> lines, int centerX, int startY, int bottomY)
    {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
        int baseline = startY;
        for (String line : lines)
        {
            if (baseline > bottomY)
            {
                break;
            }
            drawCenteredText(graphics, line, font, new Color(205, 205, 205), centerX, baseline);
            baseline += 15;
        }
    }

    private void drawSmoothModel(Graphics2D graphics, ItemInspectController.ModelSnapshot model,
        int x, int y, int size)
    {
        int pitch = controller.getPitch();
        int yaw = controller.getYaw();
        int roll = controller.getRoll();
        int orbitPitch = controller.getOrbitPitch();
        int orbitYaw = controller.getOrbitYaw();
        double zoom = controller.getZoomScale();
        if (cachedModelImage == null || cachedModel != model || cachedPitch != pitch || cachedYaw != yaw
            || cachedRoll != roll || cachedOrbitPitch != orbitPitch || cachedOrbitYaw != orbitYaw
            || cachedSize != size || Double.compare(cachedZoom, zoom) != 0)
        {
            cachedModelImage = SmoothModelRenderer.render(model, size, pitch, yaw, roll,
                orbitPitch, orbitYaw, zoom);
            cachedModel = model;
            cachedPitch = pitch;
            cachedYaw = yaw;
            cachedRoll = roll;
            cachedOrbitPitch = orbitPitch;
            cachedOrbitYaw = orbitYaw;
            cachedSize = size;
            cachedZoom = zoom;
        }
        graphics.drawImage(cachedModelImage, x, y, null);
    }

    private static void drawCelebration(Graphics2D graphics, Rectangle panel)
    {
        long now = System.currentTimeMillis();
        double phase = now / 700.0;
        int centerX = panel.x + panel.width / 2;
        int centerY = panel.y + (panel.height - 100) / 2;
        int radiusX = panel.width / 2 - 28;
        int radiusY = (panel.height - 118) / 2;
        Object oldAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < 18; i++)
        {
            double angle = phase * (i % 2 == 0 ? 0.7 : -0.45) + i * Math.PI * 2.0 / 18.0;
            int sparkleX = centerX + (int) Math.round(Math.cos(angle) * radiusX);
            int sparkleY = centerY + (int) Math.round(Math.sin(angle) * radiusY);
            double pulse = 0.5 + 0.5 * Math.sin(phase * 4.0 + i * 1.7);
            int size = 3 + (int) Math.round(pulse * 6.0);
            int alpha = 100 + (int) Math.round(pulse * 155.0);
            Color color = i % 3 == 0 ? new Color(255, 246, 160, alpha)
                : i % 3 == 1 ? new Color(255, 184, 72, alpha)
                : new Color(225, 238, 255, alpha);
            graphics.setColor(color);
            int[] xs = {sparkleX, sparkleX + size / 3, sparkleX + size, sparkleX + size / 3,
                sparkleX, sparkleX - size / 3, sparkleX - size, sparkleX - size / 3};
            int[] ys = {sparkleY - size, sparkleY - size / 3, sparkleY, sparkleY + size / 3,
                sparkleY + size, sparkleY + size / 3, sparkleY, sparkleY - size / 3};
            graphics.fillPolygon(xs, ys, xs.length);
        }
        drawCenteredText(graphics, "NEW COLLECTION LOG ITEM!",
            new Font(Font.SANS_SERIF, Font.BOLD, 13), new Color(255, 218, 96),
            centerX, panel.y + 25);
        if (oldAntialiasing != null)
        {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);
        }
    }

    private static void drawCenteredText(Graphics2D graphics, String text, Font font,
        Color color, int centerX, int baselineY)
    {
        String safeText = text == null ? "" : text;
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics();
        int x = centerX - metrics.stringWidth(safeText) / 2;
        graphics.setColor(new Color(0, 0, 0, 210));
        graphics.drawString(safeText, x + 1, baselineY + 1);
        graphics.setColor(color);
        graphics.drawString(safeText, x, baselineY);
    }
}
