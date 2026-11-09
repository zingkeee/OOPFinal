import java.awt.Color;
import java.awt.Graphics2D;

/** An energy orb fired by the boss. Bullets can shoot it down. Worth no points. */
public class BossShot extends Hazard {
    private final double vx, vy;

    public BossShot(double x, double y, double vx, double vy) {
        super(x, y, 18, 18, 1);
        this.vx = vx;
        this.vy = vy;
    }

    @Override
    public int getPoints() {
        return 0;
    }

    @Override
    public void update(double dt) {
        x += vx * dt;
        y += vy * dt;
    }

    @Override
    public boolean isOffScreen() {
        return x + width < 0 || y > GamePanel.HEIGHT || y + height < 0;
    }

    @Override
    public void draw(Graphics2D g) {
        int px = (int) x;
        int py = (int) y;
        boolean flicker = (System.currentTimeMillis() / 80) % 2 == 0;
        g.setColor(flicker ? new Color(255, 80, 60) : new Color(255, 130, 60));
        g.fillRect(px, py, width, height);
        g.setColor(new Color(255, 220, 70));
        g.fillRect(px + 4, py + 4, width - 8, height - 8);
        g.setColor(Color.WHITE);
        g.fillRect(px + 7, py + 7, width - 14, height - 14);
    }
}
