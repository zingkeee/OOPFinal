import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** A pixelated ring that expands outward and fades. Used for powerup pickups and shield breaks. */
public class Shockwave extends Effect {
    private static final double LIFETIME = 24;

    private final BufferedImage ring;
    private final double maxRadius;
    private double life = LIFETIME;

    public Shockwave(double cx, double cy, Color color, double maxRadius) {
        super(cx, cy);
        this.ring = PixelArt.ring(24, color);
        this.maxRadius = maxRadius;
    }

    @Override
    public void update(double dt) {
        life -= dt;
    }

    @Override
    public void draw(Graphics2D g) {
        double progress = 1 - Math.max(0, life) / LIFETIME;
        int diameter = Math.max(2, (int) (maxRadius * 2 * progress));
        float alpha = (float) Math.max(0, Math.min(1, 1 - progress));

        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.drawImage(ring, (int) x - diameter / 2, (int) y - diameter / 2, diameter, diameter, null);
        g.setComposite(old);
    }

    @Override
    public boolean isFinished() {
        return life <= 0;
    }
}
