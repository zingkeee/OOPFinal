import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.util.Random;

/**
 * Stormy night: dark gradient sky, drifting pixel clouds, a city skyline silhouette,
 * falling rain, and random lightning flashes.
 */
public class StormBackground extends GameObject {
    private static final int RAIN_COUNT = 90;
    private static final int CLOUD_COUNT = 7;
    private static final int SKYLINE_COUNT = 20;

    private final Random random = new Random();

    private final double[] rainX = new double[RAIN_COUNT];
    private final double[] rainY = new double[RAIN_COUNT];

    private final double[] cloudX = new double[CLOUD_COUNT];
    private final int[] cloudY = new int[CLOUD_COUNT];
    private final int[] cloudW = new int[CLOUD_COUNT];

    private final int[] skylineW = new int[SKYLINE_COUNT];
    private final int[] skylineH = new int[SKYLINE_COUNT];
    private int skylineTotal = 0;
    private double skylineScroll = 0;

    private double flash = 0;        // screen flash brightness, 0..1
    private double boltTimer = 0;    // frames the bolt stays visible
    private int[] boltX;
    private int[] boltY;
    private boolean lightningPending = false;

    public StormBackground() {
        super(0, 0, GamePanel.WIDTH, GamePanel.HEIGHT);

        for (int i = 0; i < RAIN_COUNT; i++) {
            rainX[i] = random.nextInt(GamePanel.WIDTH + 100);
            rainY[i] = random.nextInt(GamePanel.HEIGHT);
        }
        for (int i = 0; i < CLOUD_COUNT; i++) {
            cloudW[i] = 120 + random.nextInt(100);
            cloudX[i] = random.nextInt(GamePanel.WIDTH);
            cloudY[i] = 20 + random.nextInt(260);
        }
        for (int i = 0; i < SKYLINE_COUNT; i++) {
            skylineW[i] = 30 + random.nextInt(30);
            skylineH[i] = 80 + random.nextInt(180);
            skylineTotal += skylineW[i];
        }
    }

    private void triggerLightning() {
        flash = 1.0;
        boltTimer = 10;
        lightningPending = true;
        int n = 8;
        boltX = new int[n];
        boltY = new int[n];
        int bx = 60 + random.nextInt(GamePanel.WIDTH - 120);
        for (int i = 0; i < n; i++) {
            boltX[i] = bx;
            boltY[i] = i * 32;
            bx += random.nextInt(41) - 20;
        }
    }

    /** True once per lightning strike, so the game can play thunder. */
    public boolean consumeLightning() {
        boolean pending = lightningPending;
        lightningPending = false;
        return pending;
    }

    @Override
    public void update(double dt) {
        skylineScroll = (skylineScroll + 0.4 * dt) % skylineTotal;

        for (int i = 0; i < CLOUD_COUNT; i++) {
            cloudX[i] -= (0.3 + (i % 3) * 0.25) * dt;
            if (cloudX[i] + cloudW[i] < 0) {
                cloudX[i] = GamePanel.WIDTH + random.nextInt(60);
                cloudY[i] = 20 + random.nextInt(260);
            }
        }

        for (int i = 0; i < RAIN_COUNT; i++) {
            rainX[i] -= 3 * dt;
            rainY[i] += 15 * dt;
            if (rainY[i] > GamePanel.HEIGHT || rainX[i] < -4) {
                rainY[i] = -random.nextInt(60) - 10;
                rainX[i] = random.nextInt(GamePanel.WIDTH + 100);
            }
        }

        flash = Math.max(0, flash - 0.04 * dt);
        if (boltTimer > 0) {
            boltTimer -= dt;
        }
        if (flash <= 0.05 && random.nextDouble() < 0.004 * dt) {
            triggerLightning();
        }
    }

    private void drawCloud(Graphics2D g, int cx, int cy, int w) {
        g.setColor(new Color(46, 48, 76));
        g.fillRect(cx, cy + 16, w, 16);
        g.fillRect(cx + 16, cy + 8, w - 32, 8);
        g.fillRect(cx + 40, cy, w - 80, 8);
        g.setColor(new Color(36, 38, 62));
        g.fillRect(cx + 8, cy + 32, w - 16, 8);
    }

    @Override
    public void draw(Graphics2D g) {
        // Sky
        g.setPaint(new GradientPaint(0, 0, new Color(10, 12, 30),
                0, GamePanel.GROUND_Y, new Color(40, 30, 70)));
        g.fillRect(0, 0, GamePanel.WIDTH, GamePanel.HEIGHT);

        // Clouds
        for (int i = 0; i < CLOUD_COUNT; i++) {
            drawCloud(g, (int) cloudX[i], cloudY[i], cloudW[i]);
        }

        // Lightning bolt
        if (boltTimer > 0 && boltX != null) {
            g.setStroke(new BasicStroke(9f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            g.setColor(new Color(255, 230, 120, 120));
            for (int i = 0; i < boltX.length - 1; i++) {
                g.drawLine(boltX[i], boltY[i], boltX[i + 1], boltY[i + 1]);
            }
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            g.setColor(new Color(235, 240, 255));
            for (int i = 0; i < boltX.length - 1; i++) {
                g.drawLine(boltX[i], boltY[i], boltX[i + 1], boltY[i + 1]);
            }
            g.setStroke(new BasicStroke(1f));
        }

        // City skyline silhouette (two copies so it wraps around)
        g.setColor(new Color(22, 26, 46));
        for (int rep = 0; rep < 2; rep++) {
            int bx = -(int) skylineScroll + rep * skylineTotal;
            for (int i = 0; i < SKYLINE_COUNT; i++) {
                g.fillRect(bx, GamePanel.GROUND_Y - skylineH[i], skylineW[i], skylineH[i]);
                bx += skylineW[i];
            }
        }

        // Rain
        g.setColor(new Color(170, 190, 255, 120));
        for (int i = 0; i < RAIN_COUNT; i++) {
            g.fillRect((int) rainX[i], (int) rainY[i], 2, 10);
        }

        // Screen flash
        if (flash > 0) {
            g.setColor(new Color(255, 255, 255, (int) (flash * 110)));
            g.fillRect(0, 0, GamePanel.WIDTH, GamePanel.HEIGHT);
        }
    }
}
