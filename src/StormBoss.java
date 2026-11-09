import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Storm King. Appears every 25 points, floats up and down on the right, shoots
 * aimed energy orbs at the plane, and periodically fires horizontal lightning beams
 * (with a warning first). Gets tougher each time you beat it.
 */
public class StormBoss extends Hazard {
    private static final String[] ROWS = {
        "......GGGGGGGG..........",
        "....GGGGGGGGGGGGGG......",
        "..GGGGGGGGGGGGGGGGGGG...",
        ".GGGGGGGGGGGGGGGGGGGGGG.",
        "GGGGKKYYGGGGGGGGKKYYGGGG",
        "GGGGGGKKGGGGGGGGKKGGGGGG",
        "GGGGGGGGGGGGGGGGGGGGGGGG",
        "GGGGDDDDDDDDDDDDDDDDGGGG",
        ".GGDDDDDDDDDDDDDDDDDDGG.",
        "..DDDDDDDDDDDDDDDDDDDD..",
        "....DDDDDDDDDDDDDDDD...."
    };
    private static final BufferedImage SPRITE = PixelArt.create(ROWS);
    private static final int SCALE = 7;

    public static final int W = SPRITE.getWidth() * SCALE;
    public static final int H = SPRITE.getHeight() * SCALE;

    private static final double TARGET_X = GamePanel.WIDTH - W - 20;
    private static final double BEAM_WARN = 70;
    private static final double BEAM_ZAP = 20;
    private static final int BEAM_HEIGHT = 22;
    private static final int BEAM_COUNT = 3;

    private final Random random = new Random();
    private final List<Hazard> pendingShots = new ArrayList<>();
    private final int level;
    private final int maxHealth;
    private final int[] beamY = new int[BEAM_COUNT];

    private double t = 0;
    private double shotTimer = 0;
    private double beamTimer = 0;
    private double beamPhase = -1;     // -1 = no beams, otherwise frames since the beams started
    private double flashTimer = 0;
    private double targetX, targetY;

    public StormBoss(int level) {
        super(GamePanel.WIDTH + 10, 240, W, H, 15 + 5 * level);
        this.level = level;
        this.maxHealth = 15 + 5 * level;
    }

    public boolean isFighting() { return x <= TARGET_X; }
    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }

    private boolean isWarning() { return beamPhase >= 0 && beamPhase < BEAM_WARN; }
    private boolean isZapping() { return beamPhase >= BEAM_WARN && beamPhase < BEAM_WARN + BEAM_ZAP; }

    /** The plane's position, so the boss can aim at it. */
    public void setTarget(double tx, double ty) {
        targetX = tx;
        targetY = ty;
    }

    /** Hands over the orbs fired since the last call. */
    public List<Hazard> takeShots() {
        List<Hazard> out = new ArrayList<>(pendingShots);
        pendingShots.clear();
        return out;
    }

    @Override
    public void damage() {
        if (!isFighting()) {
            return;   // can't be hurt while flying in
        }
        health--;
        flashTimer = 6;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle((int) x + 12, (int) y + 8, width - 24, height - 16);
    }

    @Override
    public boolean hurts(Rectangle planeBox) {
        if (getBounds().intersects(planeBox)) {
            return true;
        }
        if (isZapping()) {
            for (int by : beamY) {
                if (new Rectangle(0, by, (int) x + 20, BEAM_HEIGHT).intersects(planeBox)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void fireOrb() {
        double sx = x + 20;
        double sy = y + height / 2.0 - 9;
        double dx = targetX - sx;
        double dy = targetY - sy;
        double len = Math.max(1, Math.hypot(dx, dy));
        double speed = 4.5 + level * 0.3;
        pendingShots.add(new BossShot(sx, sy, dx / len * speed, dy / len * speed));
        SoundManager.play(SoundManager.Sfx.ENEMY_SHOT);
    }

    private void startBeams() {
        int slot = (GamePanel.GROUND_Y - 120) / BEAM_COUNT;
        for (int i = 0; i < BEAM_COUNT; i++) {
            beamY[i] = 60 + i * slot + random.nextInt(slot - 30);
        }
        beamPhase = 0;
    }

    @Override
    public void update(double dt) {
        t += dt;
        if (flashTimer > 0) {
            flashTimer -= dt;
        }

        // Fly in from the right
        if (!isFighting()) {
            x = Math.max(TARGET_X, x - 2 * dt);
            return;
        }

        y = 240 + Math.sin(t * 0.03) * 110;

        // Aimed orbs, a bit faster each level
        shotTimer += dt;
        if (shotTimer >= Math.max(40, 75 - level * 8)) {
            shotTimer = 0;
            fireOrb();
        }

        // Lightning beams
        if (beamPhase < 0) {
            beamTimer += dt;
            if (beamTimer >= 330) {
                beamTimer = 0;
                startBeams();
            }
        } else {
            boolean wasZapping = isZapping();
            beamPhase += dt;
            if (!wasZapping && isZapping()) {
                SoundManager.play(SoundManager.Sfx.ZAP);
            }
            if (beamPhase >= BEAM_WARN + BEAM_ZAP) {
                beamPhase = -1;
            }
        }
    }

    @Override
    public void draw(Graphics2D g) {
        int beamLength = (int) x + 20;

        if (isWarning() && ((int) (beamPhase / 4)) % 2 == 0) {
            g.setColor(new Color(255, 230, 90, 130));
            for (int by : beamY) {
                for (int xx = 0; xx < beamLength; xx += 24) {
                    g.fillRect(xx, by + 9, 12, 4);
                }
            }
        }
        if (isZapping()) {
            for (int by : beamY) {
                g.setColor(new Color(255, 230, 90));
                g.fillRect(0, by, beamLength, BEAM_HEIGHT);
                g.setColor(Color.WHITE);
                g.fillRect(0, by + 7, beamLength, 8);
            }
        }

        int shake = flashTimer > 0 ? random.nextInt(7) - 3 : 0;
        g.drawImage(SPRITE, (int) x + shake, (int) y + shake, width, height, null);
    }
}
