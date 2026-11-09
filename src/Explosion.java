import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Random;

/** A burst of square pixel particles that fly outward, fall, and fade. */
public class Explosion extends Effect {
    public static final Color[] DEFAULT_PALETTE = {
            new Color(255, 220, 70), new Color(255, 140, 30), new Color(255, 80, 50),
            new Color(240, 240, 240), new Color(150, 155, 170)
    };

    private final double[] px, py, vx, vy;
    private final int[] size;
    private final Color[] color;
    private final double maxLife;
    private double life;

    /** A palette of shades based on one color, e.g. for a cyan shield burst. */
    public static Color[] paletteFor(Color base) {
        return new Color[] {base, base.brighter(), Color.WHITE, base.darker()};
    }

    public Explosion(double cx, double cy, int count, double power) {
        this(cx, cy, count, power, DEFAULT_PALETTE);
    }

    public Explosion(double cx, double cy, int count, double power, Color[] palette) {
        super(cx, cy);
        px = new double[count];
        py = new double[count];
        vx = new double[count];
        vy = new double[count];
        size = new int[count];
        color = new Color[count];

        Random r = new Random();
        for (int i = 0; i < count; i++) {
            px[i] = cx;
            py[i] = cy;
            double angle = r.nextDouble() * Math.PI * 2;
            double speed = (0.3 + r.nextDouble()) * power;
            vx[i] = Math.cos(angle) * speed;
            vy[i] = Math.sin(angle) * speed;
            size[i] = 4 + r.nextInt(3) * 2;
            color[i] = palette[r.nextInt(palette.length)];
        }
        maxLife = count > 40 ? 50 : 30;
        life = maxLife;
    }

    @Override
    public void update(double dt) {
        for (int i = 0; i < px.length; i++) {
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            vy[i] += 0.15 * dt;
        }
        life -= dt;
    }

    @Override
    public void draw(Graphics2D g) {
        int alpha = (int) (255 * Math.max(0, life / maxLife));
        for (int i = 0; i < px.length; i++) {
            Color c = color[i];
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            g.fillRect((int) px[i], (int) py[i], size[i], size[i]);
        }
    }

    @Override
    public boolean isFinished() {
        return life <= 0;
    }
}