import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

/**
 * A shot fired by the plane's gun. It flies in a straight line in whatever direction
 * the gun was pointing, passes straight through buildings, and stops when it hits a hazard.
 */
public class Bullet extends GameObject {
    private static final double SPEED = 12;
    private static final int SIZE = 8;          // hitbox is a small square around the middle

    private final double vx, vy, angle;
    private boolean expired = false;

    /** @param cx,cy the middle of the bullet when it leaves the barrel */
    public Bullet(double cx, double cy, double angle) {
        super(cx - SIZE / 2.0, cy - SIZE / 2.0, SIZE, SIZE);
        this.angle = angle;
        this.vx = Math.cos(angle) * SPEED;
        this.vy = Math.sin(angle) * SPEED;
    }

    @Override
    public void update(double dt) {
        x += vx * dt;
        y += vy * dt;
    }

    @Override
    public void draw(Graphics2D g) {
        AffineTransform old = g.getTransform();
        int cx = (int) (x + SIZE / 2.0);
        int cy = (int) (y + SIZE / 2.0);
        g.rotate(angle, cx, cy);
        g.setColor(new Color(255, 200, 60));
        g.fillRect(cx - 7, cy - 2, 14, 4);
        g.setColor(Color.WHITE);
        g.fillRect(cx + 1, cy - 1, 6, 2);
        g.setTransform(old);
    }

    public void expire() { expired = true; }
    public boolean isExpired() { return expired; }

    /** Bullets can leave the screen in any direction. */
    @Override
    public boolean isOffScreen() {
        return x < -30 || x > GamePanel.WIDTH + 30 || y < -30 || y > GamePanel.HEIGHT + 30;
    }
}