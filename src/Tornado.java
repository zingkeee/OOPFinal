import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/** A swaying tornado standing on the ground. Cannot be destroyed - fly over it! */
public class Tornado extends Hazard {
    public static final int W = 70;
    public static final int H = 240;
    private static final int ROW_H = 16;

    private final double speed;
    private double t = 0;

    public Tornado(double x, double speed) {
        super(x, GamePanel.GROUND_Y - H, W, H, 1);
        this.speed = speed;
    }

    /** Bullets do nothing to a tornado. */
    @Override
    public void damage() {
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle((int) x + 10, (int) y, width - 20, height);
    }

    @Override
    public void update(double dt) {
        x -= speed * dt;
        t += dt;
    }

    @Override
    public void draw(Graphics2D g) {
        int rows = H / ROW_H;
        for (int i = 0; i < rows; i++) {
            double frac = i / (double) (rows - 1);          // 0 at top, 1 at bottom
            int w = (int) (W - frac * (W - 16));
            double sway = Math.sin(t * 0.18 + i * 0.55) * (6 + (1 - frac) * 6);
            int rx = (int) (x + (W - w) / 2.0 + sway);
            int ry = (int) y + i * ROW_H;
            g.setColor(i % 2 == 0 ? new Color(150, 155, 170) : new Color(105, 110, 125));
            g.fillRect(rx, ry, w, ROW_H);
        }

        // Flying debris
        g.setColor(new Color(60, 45, 35));
        for (int k = 0; k < 6; k++) {
            int dx = (int) (x + W / 2.0 + Math.cos(t * 0.25 + k * 1.1) * (W / 2.0 + 6));
            int dy = (int) (y + (k * 37 + t * 3) % H);
            g.fillRect(dx, dy, 5, 5);
        }
    }
}
