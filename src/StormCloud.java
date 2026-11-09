import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * An angry cloud that drifts with the buildings. It warns, then zaps a lightning
 * bolt straight down to the ground, over and over. Takes 3 bullets to destroy.
 */
public class StormCloud extends Hazard {
    private static final String[] ROWS = {
            "....GGGGG...........",
            "..GGGGGGGGGG..GGG...",
            ".GGGGGGGGGGGGGGGGGG.",
            "GGGGYYGGGGGGGGYYGGGG",
            "GGGDDDGGGGGGDDDGGGGG",
            ".DDDDDDDDDDDDDDDDDD.",
            "..DDDDDDDDDDDDDDDD.."
    };
    private static final BufferedImage SPRITE = PixelArt.create(ROWS);
    private static final int SCALE = 4;

    public static final int W = SPRITE.getWidth() * SCALE;
    public static final int H = SPRITE.getHeight() * SCALE;

    // Timeline of one cycle, in frames
    private static final double WARN_START = 60;    // 1 second of flashing warning
    private static final double ZAP_START = 120;
    private static final double ZAP_END = 132;      // the bolt only lasts 12 frames
    private static final int BOLT_LENGTH = 170;     // the bolt stops short of the ground
    private static final int BOLT_WIDTH = 16;

    private final double speed;
    private double timer = 0;

    public StormCloud(double x, double y, double speed) {
        super(x, y, W, H, 2);
        this.speed = speed;
    }

    private boolean isWarning() { return timer >= WARN_START && timer < ZAP_START; }
    private boolean isZapping() { return timer >= ZAP_START && timer < ZAP_END; }

    private Rectangle boltBounds() {
        int bx = (int) (x + width / 2.0) - BOLT_WIDTH / 2;
        int by = (int) (y + height);
        return new Rectangle(bx, by, BOLT_WIDTH, boltEnd(by) - by);
    }

    private int boltEnd(int startY) {
        return Math.min(startY + BOLT_LENGTH, GamePanel.GROUND_Y);
    }

    /** A slightly smaller hitbox than the drawing, so near-misses feel fair. */
    @Override
    public Rectangle getBounds() {
        return new Rectangle((int) x + 8, (int) y + 4, width - 16, height - 8);
    }

    @Override
    public void update(double dt) {
        boolean wasZapping = isZapping();
        x -= speed * dt;
        timer += dt;
        if (timer >= ZAP_END) {
            timer = 0;
        }
        if (!wasZapping && isZapping() && x < GamePanel.WIDTH) {
            SoundManager.play(SoundManager.Sfx.ZAP);
        }
    }

    @Override
    public boolean hurts(Rectangle planeBox) {
        return getBounds().intersects(planeBox) || (isZapping() && boltBounds().intersects(planeBox));
    }

    @Override
    public void draw(Graphics2D g) {
        int cx = (int) (x + width / 2.0);
        int startY = (int) (y + height);

        // Warning: flashing dotted line where the bolt is about to strike
        if (isWarning() && ((int) (timer / 4)) % 2 == 0) {
            g.setColor(new Color(255, 230, 90, 140));
            for (int yy = startY; yy < boltEnd(startY); yy += 20) {
                g.fillRect(cx - 2, yy, 4, 10);
            }
        }

        g.drawImage(SPRITE, (int) x, (int) y, width, height, null);

        // Zap: blocky zigzag lightning bolt
        if (isZapping()) {
            int segH = 24;
            for (int sy = startY; sy < boltEnd(startY); sy += segH) {
                int nx = cx + (((sy - startY) / segH) % 2 == 0 ? -5 : 5);
                int h = Math.min(segH, boltEnd(startY) - sy);
                g.setColor(new Color(255, 230, 90));
                g.fillRect(nx - 6, sy, 12, h);
                g.setColor(Color.WHITE);
                g.fillRect(nx - 2, sy, 4, h);
            }
        }
    }
}