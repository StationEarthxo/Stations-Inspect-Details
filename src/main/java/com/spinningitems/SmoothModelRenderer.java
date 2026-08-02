package com.spinningitems;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

final class SmoothModelRenderer
{
    private static final int ANGLE_UNITS = 2048;
    private static final int SUPERSAMPLE = 2;

    private SmoothModelRenderer()
    {
    }

    static BufferedImage render(ItemInspectController.ModelSnapshot model, int outputSize,
        int pitch, int yaw, int roll, int orbitPitch, int orbitYaw, double zoomScale)
    {
        int renderSize = Math.min(768, Math.max(outputSize, outputSize * SUPERSAMPLE));
        int vertexCount = model.x.length;
        double[] transformedX = new double[vertexCount];
        double[] transformedY = new double[vertexCount];
        double[] transformedZ = new double[vertexCount];
        transform(model, pitch, yaw, roll, orbitPitch, orbitYaw,
            transformedX, transformedY, transformedZ);

        double extent = 1.0;
        for (int i = 0; i < vertexCount; i++)
        {
            extent = Math.max(extent, Math.max(Math.abs(transformedX[i]), Math.abs(transformedY[i])));
        }
        double scale = renderSize * 0.41 * zoomScale / extent;
        double center = renderSize * 0.5;

        BufferedImage image = new BufferedImage(renderSize, renderSize, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        double[] depths = new double[pixels.length];
        Arrays.fill(depths, Double.NEGATIVE_INFINITY);

        int faceCount = Math.min(model.face1.length,
            Math.min(model.face2.length, Math.min(model.face3.length, model.color1.length)));
        for (int face = 0; face < faceCount; face++)
        {
            int a = model.face1[face];
            int b = model.face2[face];
            int c = model.face3[face];
            if (a < 0 || b < 0 || c < 0 || a >= vertexCount || b >= vertexCount || c >= vertexCount
                || model.color1[face] == -2)
            {
                continue;
            }

            int alpha = model.transparencies == null || face >= model.transparencies.length
                ? 255 : 255 - (model.transparencies[face] & 0xFF);
            if (alpha <= 0)
            {
                continue;
            }

            double x0 = center + transformedX[a] * scale;
            double y0 = center + transformedY[a] * scale;
            double x1 = center + transformedX[b] * scale;
            double y1 = center + transformedY[b] * scale;
            double x2 = center + transformedX[c] * scale;
            double y2 = center + transformedY[c] * scale;
            rasterTriangle(pixels, depths, renderSize,
                x0, y0, transformedZ[a], x1, y1, transformedZ[b], x2, y2, transformedZ[c],
                vertexColor(model, face, 0), vertexColor(model, face, 1), vertexColor(model, face, 2), alpha);
        }

        if (renderSize == outputSize)
        {
            return image;
        }
        BufferedImage downsampled = new BufferedImage(outputSize, outputSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = downsampled.createGraphics();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, outputSize, outputSize, null);
        }
        finally
        {
            graphics.dispose();
        }
        return downsampled;
    }

    private static void transform(ItemInspectController.ModelSnapshot model, int pitch, int yaw, int roll,
        int orbitPitch, int orbitYaw,
        double[] outX, double[] outY, double[] outZ)
    {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < model.x.length; i++)
        {
            minX = Math.min(minX, model.x[i]); maxX = Math.max(maxX, model.x[i]);
            minY = Math.min(minY, model.y[i]); maxY = Math.max(maxY, model.y[i]);
            minZ = Math.min(minZ, model.z[i]); maxZ = Math.max(maxZ, model.z[i]);
        }
        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;
        double rx = pitch * Math.PI * 2.0 / ANGLE_UNITS;
        double ry = yaw * Math.PI * 2.0 / ANGLE_UNITS;
        double rz = roll * Math.PI * 2.0 / ANGLE_UNITS;
        double sinX = Math.sin(rx), cosX = Math.cos(rx);
        double sinY = Math.sin(ry), cosY = Math.cos(ry);
        double sinZ = Math.sin(rz), cosZ = Math.cos(rz);
        double orbitX = orbitPitch * Math.PI * 2.0 / ANGLE_UNITS;
        double orbitY = orbitYaw * Math.PI * 2.0 / ANGLE_UNITS;
        double sinOrbitX = Math.sin(orbitX), cosOrbitX = Math.cos(orbitX);
        double sinOrbitY = Math.sin(orbitY), cosOrbitY = Math.cos(orbitY);

        for (int i = 0; i < model.x.length; i++)
        {
            double vx = model.x[i] - centerX;
            double vy = model.y[i] - centerY;
            double vz = model.z[i] - centerZ;
            // Match the inventory renderer: negative Z rotation, then Y rotation,
            // followed by the X camera angle stored in the item composition.
            double x1 = vx * cosZ + vy * sinZ;
            double y1 = vy * cosZ - vx * sinZ;
            double x2 = x1 * cosY + vz * sinY;
            double z2 = vz * cosY - x1 * sinY;
            double y3 = y1 * cosX - z2 * sinX;
            double z3 = y1 * sinX + z2 * cosX;

            // Apply user interaction in view space, independently of the item's
            // authored inventory pose. This makes horizontal and vertical drags
            // behave consistently even when the item starts tilted or rolled.
            double x4 = x2 * cosOrbitY + z3 * sinOrbitY;
            double z4 = z3 * cosOrbitY - x2 * sinOrbitY;
            outX[i] = x4;
            outY[i] = y3 * cosOrbitX - z4 * sinOrbitX;
            outZ[i] = y3 * sinOrbitX + z4 * cosOrbitX;
        }
    }

    private static void rasterTriangle(int[] pixels, double[] depths, int size,
        double x0, double y0, double z0, double x1, double y1, double z1,
        double x2, double y2, double z2, int color0, int color1, int color2, int alpha)
    {
        double area = edge(x0, y0, x1, y1, x2, y2);
        if (Math.abs(area) < 0.0001)
        {
            return;
        }
        int minX = clamp((int) Math.floor(Math.min(x0, Math.min(x1, x2))), 0, size - 1);
        int maxX = clamp((int) Math.ceil(Math.max(x0, Math.max(x1, x2))), 0, size - 1);
        int minY = clamp((int) Math.floor(Math.min(y0, Math.min(y1, y2))), 0, size - 1);
        int maxY = clamp((int) Math.ceil(Math.max(y0, Math.max(y1, y2))), 0, size - 1);

        int r0 = (color0 >> 16) & 255, g0 = (color0 >> 8) & 255, b0 = color0 & 255;
        int r1 = (color1 >> 16) & 255, g1 = (color1 >> 8) & 255, b1 = color1 & 255;
        int r2 = (color2 >> 16) & 255, g2 = (color2 >> 8) & 255, b2 = color2 & 255;
        for (int py = minY; py <= maxY; py++)
        {
            double sampleY = py + 0.5;
            for (int px = minX; px <= maxX; px++)
            {
                double sampleX = px + 0.5;
                double w0 = edge(x1, y1, x2, y2, sampleX, sampleY) / area;
                double w1 = edge(x2, y2, x0, y0, sampleX, sampleY) / area;
                double w2 = 1.0 - w0 - w1;
                if (w0 < -0.00001 || w1 < -0.00001 || w2 < -0.00001)
                {
                    continue;
                }
                int index = py * size + px;
                double depth = w0 * z0 + w1 * z1 + w2 * z2;
                if (depth <= depths[index])
                {
                    continue;
                }
                int red = clamp((int) Math.round(w0 * r0 + w1 * r1 + w2 * r2), 0, 255);
                int green = clamp((int) Math.round(w0 * g0 + w1 * g1 + w2 * g2), 0, 255);
                int blue = clamp((int) Math.round(w0 * b0 + w1 * b1 + w2 * b2), 0, 255);
                int source = (alpha << 24) | (red << 16) | (green << 8) | blue;
                pixels[index] = alpha == 255 ? source : blend(source, pixels[index]);
                depths[index] = depth;
            }
        }
    }

    private static int vertexColor(ItemInspectController.ModelSnapshot model, int face, int vertex)
    {
        int packed = model.color1[face];
        if (vertex == 1 && face < model.color2.length && model.color2[face] >= 0) packed = model.color2[face];
        if (vertex == 2 && face < model.color3.length && model.color3[face] >= 0) packed = model.color3[face];
        int hue = (packed >> 10) & 63;
        int saturation = (packed >> 7) & 7;
        int lightness = packed & 127;
        return hslToRgb(hue / 64.0, saturation / 8.0, lightness / 128.0);
    }

    private static int hslToRgb(double h, double s, double l)
    {
        double r = l, g = l, b = l;
        if (s > 0.0)
        {
            double q = l < 0.5 ? l * (1.0 + s) : l + s - l * s;
            double p = 2.0 * l - q;
            r = hueToRgb(p, q, h + 1.0 / 3.0);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1.0 / 3.0);
        }
        return new Color((float) r, (float) g, (float) b).getRGB() & 0xFFFFFF;
    }

    private static double hueToRgb(double p, double q, double t)
    {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0 / 6.0) return p + (q - p) * 6.0 * t;
        if (t < 0.5) return q;
        if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
        return p;
    }

    private static int blend(int source, int destination)
    {
        int alpha = source >>> 24;
        int inverse = 255 - alpha;
        int red = (((source >> 16) & 255) * alpha + ((destination >> 16) & 255) * inverse) / 255;
        int green = (((source >> 8) & 255) * alpha + ((destination >> 8) & 255) * inverse) / 255;
        int blue = ((source & 255) * alpha + (destination & 255) * inverse) / 255;
        int outAlpha = Math.min(255, alpha + ((destination >>> 24) * inverse) / 255);
        return (outAlpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static double edge(double ax, double ay, double bx, double by, double px, double py)
    {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
