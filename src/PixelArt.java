import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Tiny helper for pixel art. Sprites are written as rows of letters
 * (each letter = one color) and drawn scaled up with sharp edges.
 */
public final class PixelArt {
    private PixelArt() {}

    private static Color colorFor(char c) {
        switch (c) {
            case 'R': return new Color(220, 50, 47);   // red
            case 'W': return new Color(240, 240, 240); // white
            case 'D': return new Color(70, 75, 90);    // dark gray
            case 'G': return new Color(120, 125, 140); // gray
            case 'B': return new Color(110, 200, 255); // light blue
            case 'Y': return new Color(255, 220, 70);  // yellow
            case 'O': return new Color(255, 140, 30);  // orange
            case 'N': return new Color(110, 55, 25);   // brown
            case 'K': return new Color(20, 20, 30);    // near-black
            case 'C': return new Color(80, 230, 230);  // cyan
            case 'U': return new Color(40, 90, 190);   // airline blue
            case 'L': return new Color(200, 205, 215); // light gray
            case 'E': return new Color(90, 230, 120);  // bright green
            case 'e': return new Color(30, 140, 75);   // dark green
            default:  return null;                     // '.' = transparent
        }
    }

    /** Turns rows of letters into a 1-pixel-per-cell image. Scale it up when drawing. */
    public static BufferedImage create(String[] rows) {
        int w = 0;
        for (String r : rows) {
            w = Math.max(w, r.length());
        }
        BufferedImage img = new BufferedImage(w, rows.length, BufferedImage.TYPE_INT_ARGB);
        for (int j = 0; j < rows.length; j++) {
            for (int i = 0; i < rows[j].length(); i++) {
                Color c = colorFor(rows[j].charAt(i));
                if (c != null) {
                    img.setRGB(i, j, c.getRGB());
                }
            }
        }
        return img;
    }

    /** A pixelated ring (used for the shield bubble). */
    public static BufferedImage ring(int size, Color color) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        double c = (size - 1) / 2.0;
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                double d = Math.hypot(i - c, j - c);
                if (d <= c && d >= c - 1.5) {
                    img.setRGB(i, j, new Color(color.getRed(), color.getGreen(), color.getBlue(), 220).getRGB());
                } else if (d < c - 1.5) {
                    img.setRGB(i, j, new Color(color.getRed(), color.getGreen(), color.getBlue(), 45).getRGB());
                }
            }
        }
        return img;
    }
}