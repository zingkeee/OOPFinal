import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * A bomb thrown by the Storm King. It flies in an arc toward where the plane was, then
 * explodes when its fuse runs out or it hits the ground. The explosion is a big blast that
 * hurts for a moment. Shooting a bomb makes it go off early (hopefully far away from you).
 */
public class Bomb extends Hazard {
    private static final double GRAVITY = 0.12;
    private static final int SIZE = 16;
    private static final double BLAST_RADIUS = 46;
    private static final double BLAST_FRAMES = 16;

    private final double fuse;          // frames of flight before it goes off
    private double vx, vy;
    private double age = 0;
    private double blastAge = -1;       // -1 = still flying, otherwise frames since the blast began

    /** Throws from (x, y) so that the bomb arrives at (targetX, targetY) after flightFrames. */
    public Bomb(double x, double y, double targetX, double targetY, double flightFrames) {
        super(x, y, SIZE, SIZE, 1);
        this.fuse = flightFrames;
        this.vx = (targetX - x) / flightFrames;
        this.vy = (targetY - y) / flightFrames - 0.5 * GRAVITY * flightFrames;
    }

    private boolean isExploding() { return blastAge >= 0; }

    private double radius() {
        return BLAST_RADIUS * Math.min(1.0, (blastAge + 2) / 6.0);   // the blast swells quickly
    }

    private void detonate() {
        if (!isExploding()) {
            blastAge = 0;
            if (x > -40 && x < GamePanel.WIDTH + 40) {
                SoundManager.play(SoundManager.Sfx.EXPLOSION);
            }
        }
    }

    @Override
    public int getPoints() {
        return 0;
    }

    /** The blast is its own big fireball, so no extra sparks are needed. */
    @Override
    public boolean leavesDebris() {
        return false;
    }

    /** A bullet (or a shield) sets the bomb off. */
    @Override
    public void damage() {
        detonate();
    }

    @Override
    public boolean isDestroyed() {
        return blastAge >= BLAST_FRAMES;
    }

    @Override
    public boolean isOffScreen() {
        return !isExploding() && (x < -50 || x > GamePanel.WIDTH + 80 || y > GamePanel.HEIGHT);
    }

    @Override
    public boolean hurts(Rectangle planeBox) {
        if (!isExploding()) {
            return getBounds().intersects(planeBox);
        }
        // Circle against rectangle: find the closest point of the plane's box to the blast center
        double cx = x + SIZE / 2.0;
        double cy = y + SIZE / 2.0;
        double nearestX = Math.max(planeBox.x, Math.min(cx, planeBox.x + planeBox.width));
        double nearestY = Math.max(planeBox.y, Math.min(cy, planeBox.y + planeBox.height));
        return Math.hypot(cx - nearestX, cy - nearestY) <= radius();
    }

    @Override
    public void update(double dt) {
        if (isExploding()) {
            blastAge += dt;
            return;
        }
        age += dt;
        x += vx * dt;
        y += vy * dt;
        vy += GRAVITY * dt;
        if (age >= fuse || y + height >= GamePanel.GROUND_Y) {
            detonate();
        }
    }

    @Override
    public void draw(Graphics2D g) {
        int cx = (int) x + SIZE / 2;
        int cy = (int) y + SIZE / 2;

        if (isExploding()) {
            drawBlast(g, cx, cy);
            return;
        }

        // Round black bomb with a highlight
        g.setColor(new Color(18, 20, 28));
        g.fillRect(cx - 7, cy - 4, 14, 11);
        g.fillRect(cx - 5, cy - 7, 10, 17);
        g.setColor(new Color(72, 78, 96));
        g.fillRect(cx - 4, cy - 4, 3, 3);
        // Cap and a burning fuse
        g.setColor(new Color(110, 70, 40));
        g.fillRect(cx - 2, cy - 10, 4, 3);
        g.setColor(new Color(190, 160, 120));
        g.fillRect(cx + 1, cy - 13, 2, 3);
        boolean flicker = (System.currentTimeMillis() / 80) % 2 == 0;
        g.setColor(flicker ? new Color(255, 230, 90) : new Color(255, 130, 40));
        g.fillRect(cx, cy - 17, 4, 4);
    }

    /** A blocky fireball: orange outside, yellow in the middle, white in the core. */
    private void drawBlast(Graphics2D g, int cx, int cy) {
        double fade = 1 - Math.max(0, (blastAge - 6) / (BLAST_FRAMES - 6));
        double r = radius();
        int[] sizes = {(int) r, (int) (r * 0.65), (int) (r * 0.3)};
        Color[] colors = {
            new Color(255, 90, 30, (int) (210 * fade)),
            new Color(255, 200, 60, (int) (230 * fade)),
            new Color(255, 255, 230, (int) (240 * fade))
        };
        for (int i = 0; i < 3; i++) {
            g.setColor(colors[i]);
            int rr = sizes[i];
            for (int dy = -rr; dy <= rr; dy += 4) {
                int half = (int) Math.sqrt(Math.max(0, (double) rr * rr - (double) dy * dy));
                g.fillRect(cx - half, cy + dy - 2, 2 * half, 4);
            }
        }
    }
}
