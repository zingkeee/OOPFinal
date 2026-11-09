import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Arrays;

/**
 * The white vapor trail behind the plane. Puffs are dropped into the world, so they
 * drift backward with the scrolling city, growing and fading as they age.
 */
public class VaporTrail extends GameObject {
    private static final int MAX_PUFFS = 70;
    private static final double LIFE = 38;

    private final double[] px = new double[MAX_PUFFS];
    private final double[] py = new double[MAX_PUFFS];
    private final double[] life = new double[MAX_PUFFS];
    private int next = 0;
    private double speed = 3;

    public VaporTrail() {
        super(0, 0, 0, 0);
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public void emit(double x, double y) {
        px[next] = x;
        py[next] = y;
        life[next] = LIFE;
        next = (next + 1) % MAX_PUFFS;
    }

    public void clear() {
        Arrays.fill(life, 0);
    }

    @Override
    public void update(double dt) {
        for (int i = 0; i < MAX_PUFFS; i++) {
            if (life[i] > 0) {
                px[i] -= speed * dt;
                life[i] -= dt;
            }
        }
    }

    @Override
    public void draw(Graphics2D g) {
        for (int i = 0; i < MAX_PUFFS; i++) {
            if (life[i] > 0) {
                double fresh = life[i] / LIFE;                 // 1 = just emitted, 0 = gone
                int size = (4 + (int) ((1 - fresh) * 8)) / 2 * 2;
                g.setColor(new Color(240, 245, 255, (int) (170 * fresh)));
                g.fillRect((int) px[i] - size / 2, (int) py[i] - size / 2, size, size);
            }
        }
    }
}
