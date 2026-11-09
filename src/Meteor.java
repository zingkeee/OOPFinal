import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** A flaming rock that streaks diagonally across the sky. One bullet destroys it. */
public class Meteor extends Hazard {
    private static final String[] ROWS = {
        "....NNNN....",
        "..NNOOOONN..",
        ".NOOOYOOOON.",
        ".NOYYOOORON.",
        "NNOOOORROONN",
        "NORROOOOOYON",
        "NOORROOOOOON",
        ".NOOOOOORON.",
        ".NNOOOOOONN.",
        "..NNOOOONN..",
        "....NNNN...."
    };
    private static final BufferedImage SPRITE = PixelArt.create(ROWS);
    private static final int SCALE = 4;

    public static final int W = SPRITE.getWidth() * SCALE;
    public static final int H = SPRITE.getHeight() * SCALE;

    private final double vx, vy;

    public Meteor(double x, double y, double vx, double vy) {
        super(x, y, W, H, 1);
        this.vx = vx;
        this.vy = vy;
    }

    @Override
    public void update(double dt) {
        x += vx * dt;
        y += vy * dt;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle((int) x + 6, (int) y + 6, width - 12, height - 12);
    }

    @Override
    public boolean isOffScreen() {
        return x + width < -40 || y > GamePanel.HEIGHT;
    }

    @Override
    public void draw(Graphics2D g) {
        double len = Math.hypot(vx, vy);
        double ux = -vx / len;
        double uy = -vy / len;
        double cx = x + width / 2.0;
        double cy = y + height / 2.0;

        // Fiery trail made of square "pixels"
        for (int i = 1; i <= 7; i++) {
            int size = Math.max(4, 22 - i * 3);
            double d = 14 + i * 9;
            int px = (int) (cx + ux * d) - size / 2;
            int py = (int) (cy + uy * d) - size / 2;
            g.setColor(i < 3 ? new Color(255, 220, 70, 200) : new Color(255, 120, 30, 200 - i * 20));
            g.fillRect(px, py, size, size);
        }

        g.drawImage(SPRITE, (int) x, (int) y, width, height, null);
    }
}
