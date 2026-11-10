import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Storm King: a horned storm demon with a crackling lightning crown, glowing red eyes,
 * a long white beard and a lightning staff. Appears every 25 points and floats up and down
 * on the right. It shoots aimed energy orbs, throws bombs in arcs, and fires horizontal
 * lightning beams (with a warning first). Gets tougher each time you beat it.
 */
public class StormBoss extends Hazard {
    private static final int GRID = 5;                 // each "pixel" of the boss art is 5x5 screen pixels
    public static final int W = 26 * GRID;
    public static final int H = 24 * GRID;

    private static final double TARGET_X = GamePanel.WIDTH - W - 20;
    private static final double BEAM_WARN = 70;
    private static final double BEAM_ZAP = 20;
    private static final int BEAM_HEIGHT = 22;
    private static final int BEAM_COUNT = 3;

    // Colors
    private static final Color SKIN = new Color(150, 160, 190);
    private static final Color SKIN_SHADE = new Color(104, 112, 148);
    private static final Color HORN = new Color(235, 228, 205);
    private static final Color HORN_SHADE = new Color(168, 158, 136);
    private static final Color ROBE = new Color(96, 48, 144);
    private static final Color ROBE_DARK = new Color(56, 28, 88);
    private static final Color BEARD = new Color(246, 246, 252);
    private static final Color BEARD_SHADE = new Color(186, 192, 212);
    private static final Color GOLD = new Color(232, 182, 44);
    private static final Color STAFF = new Color(112, 72, 36);

    private final Random random = new Random();
    private final List<Hazard> pendingShots = new ArrayList<>();
    private final int level;
    private final int maxHealth;
    private final int[] beamY = new int[BEAM_COUNT];

    private double t = 0;
    private double shotTimer = 0;
    private double bombTimer = 0;
    private int bombsToThrow = 0;
    private double bombStagger = 0;
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

    private double orbInterval() { return Math.max(45, 90 - level * 8); }
    private double bombInterval() { return Math.max(130, 220 - level * 25); }

    /** The plane's position, so the boss can aim at it. */
    public void setTarget(double tx, double ty) {
        targetX = tx;
        targetY = ty;
    }

    /** Hands over the orbs and bombs thrown since the last call. */
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

    /** The body (head and robe) is the hittable part; the horns and staff stick out. */
    @Override
    public Rectangle getBounds() {
        return new Rectangle((int) x + 25, (int) y + 10, width - 50, height - 15);
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
        double sx = x + 40;
        double sy = y + 8 * GRID;
        double dx = targetX - sx;
        double dy = targetY - sy;
        double len = Math.max(1, Math.hypot(dx, dy));
        double speed = 4.5 + level * 0.3;
        pendingShots.add(new BossShot(sx, sy, dx / len * speed, dy / len * speed));
        SoundManager.play(SoundManager.Sfx.ENEMY_SHOT);
    }

    /** Lobs a bomb from the staff hand toward (roughly) where the plane is. */
    private void throwBomb() {
        double sx = x + 6;
        double sy = y + 17 * GRID - 6;
        double tx = targetX + (random.nextDouble() - 0.5) * 70;
        double ty = targetY + (random.nextDouble() - 0.5) * 70;
        pendingShots.add(new Bomb(sx, sy, tx, ty, 70 + random.nextInt(20)));
        SoundManager.play(SoundManager.Sfx.BOMB_THROW);
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

        y = 230 + Math.sin(t * 0.03) * 105;

        // Aimed orbs
        shotTimer += dt;
        if (shotTimer >= orbInterval()) {
            shotTimer = 0;
            fireOrb();
        }

        // Bombs: one at first, up to three at once on later fights
        bombTimer += dt;
        if (bombTimer >= bombInterval()) {
            bombTimer = 0;
            bombsToThrow = Math.min(3, 1 + level / 2);
            bombStagger = 0;
        }
        if (bombsToThrow > 0) {
            bombStagger -= dt;
            if (bombStagger <= 0) {
                throwBomb();
                bombsToThrow--;
                bombStagger = 14;
            }
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

    // ----- drawing -----

    /** Whitens a color while the boss is flashing from a hit. */
    private Color tint(Color c) {
        if (flashTimer <= 0) {
            return c;
        }
        return new Color((c.getRed() + 255) / 2, (c.getGreen() + 255) / 2, (c.getBlue() + 255) / 2, c.getAlpha());
    }

    /** Fills a rectangle given in "boss pixels" (each one is GRID screen pixels). */
    private void cell(Graphics2D g, int ox, int oy, double gx, double gy, double gw, double gh, Color c) {
        int x1 = ox + (int) Math.round(gx * GRID);
        int y1 = oy + (int) Math.round(gy * GRID);
        int x2 = ox + (int) Math.round((gx + gw) * GRID);
        int y2 = oy + (int) Math.round((gy + gh) * GRID);
        g.setColor(tint(c));
        g.fillRect(x1, y1, x2 - x1, y2 - y1);
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
        drawDemon(g, (int) x + shake, (int) y + shake);
    }

    private void drawDemon(Graphics2D g, int ox, int oy) {
        double sway = Math.sin(t * 0.09);
        boolean charging = isWarning() || bombTimer > bombInterval() - 20;

        // The robe fades into a dark storm cloud at the bottom
        cell(g, ox, oy, 3, 20, 20, 4, new Color(48, 52, 76));
        cell(g, ox, oy, 0, 21, 7, 3, new Color(60, 64, 90));
        cell(g, ox, oy, 19, 21, 7, 3, new Color(60, 64, 90));
        cell(g, ox, oy, 6, 22, 14, 2, new Color(36, 38, 58));

        // Staff (held on the left, toward the player) with a crackling orb on top
        cell(g, ox, oy, 1, 3, 1, 19, STAFF);
        cell(g, ox, oy, 0, 3, 1, 2, STAFF);
        cell(g, ox, oy, 2, 3, 1, 2, STAFF);
        boolean spark = (int) (t / 5) % 2 == 0;
        Color orb = charging ? (spark ? new Color(255, 70, 50) : new Color(255, 200, 60))
                : (spark ? new Color(90, 230, 255) : new Color(170, 245, 255));
        cell(g, ox, oy, 0, 0, 3, 3, orb);
        cell(g, ox, oy, 1, 1, 1, 1, Color.WHITE);

        // Robe, shoulders and the arm holding the staff
        cell(g, ox, oy, 5, 16, 16, 6, ROBE);
        cell(g, ox, oy, 5, 16, 2, 6, ROBE_DARK);
        cell(g, ox, oy, 19, 16, 2, 6, ROBE_DARK);
        cell(g, ox, oy, 3, 16, 3, 3, ROBE);
        cell(g, ox, oy, 20, 16, 3, 3, ROBE);
        cell(g, ox, oy, 4, 15, 2, 2, ROBE_DARK);       // pointed collar
        cell(g, ox, oy, 20, 15, 2, 2, ROBE_DARK);
        cell(g, ox, oy, 2, 17, 4, 2, ROBE);            // arm
        cell(g, ox, oy, 1, 17, 2, 2, SKIN);            // hand gripping the staff

        // Horns (curving up and outward)
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            double[][] parts = {{6, 5, 3, 2}, {4, 4, 3, 2}, {3, 2, 2, 2}, {2, 0, 2, 2}};
            for (double[] p : parts) {
                double hx = left ? p[0] : 26 - p[0] - p[2];
                cell(g, ox, oy, hx, p[1], p[2], p[3], HORN);
                cell(g, ox, oy, hx, p[1] + p[3] - 0.5, p[2], 0.5, HORN_SHADE);
            }
        }

        // Head
        cell(g, ox, oy, 7, 6, 12, 9, SKIN);
        cell(g, ox, oy, 7, 6, 1, 9, SKIN_SHADE);
        cell(g, ox, oy, 18, 6, 1, 9, SKIN_SHADE);
        cell(g, ox, oy, 7, 6, 12, 1, SKIN_SHADE);

        // Angry brows, nose, and glowing red eyes
        cell(g, ox, oy, 8, 8, 2, 1, Color.BLACK);
        cell(g, ox, oy, 10, 9, 2, 1, Color.BLACK);
        cell(g, ox, oy, 16, 8, 2, 1, Color.BLACK);
        cell(g, ox, oy, 14, 9, 2, 1, Color.BLACK);
        int glow = (int) (70 + 55 * Math.sin(t * 0.2));
        cell(g, ox, oy, 8, 9, 5, 4, new Color(255, 40, 40, glow));
        cell(g, ox, oy, 13, 9, 5, 4, new Color(255, 40, 40, glow));
        cell(g, ox, oy, 9, 10, 3, 2, new Color(255, 40, 40));
        cell(g, ox, oy, 14, 10, 3, 2, new Color(255, 40, 40));
        cell(g, ox, oy, 10, 10, 1, 2, new Color(255, 225, 80));
        cell(g, ox, oy, 15, 10, 1, 2, new Color(255, 225, 80));
        cell(g, ox, oy, 12, 11, 2, 2, SKIN_SHADE);
        cell(g, ox, oy, 12, 12, 1, 1, Color.BLACK);
        cell(g, ox, oy, 13, 12, 1, 1, Color.BLACK);

        // Mouth with fangs, framed by a mustache
        cell(g, ox, oy, 9, 14, 8, 1, Color.BLACK);
        cell(g, ox, oy, 10, 14, 1, 1, Color.WHITE);
        cell(g, ox, oy, 15, 14, 1, 1, Color.WHITE);
        cell(g, ox, oy, 8, 13, 4, 1, BEARD);
        cell(g, ox, oy, 14, 13, 4, 1, BEARD);

        // Long white beard that sways as he floats
        cell(g, ox, oy, 7, 12, 2, 4, BEARD);
        cell(g, ox, oy, 17, 12, 2, 4, BEARD);
        cell(g, ox, oy, 8 + 0.2 * sway, 15, 10, 3, BEARD);
        cell(g, ox, oy, 9 + 0.5 * sway, 18, 8, 3, BEARD);
        cell(g, ox, oy, 10 + 0.8 * sway, 21, 6, 2, BEARD);
        cell(g, ox, oy, 11 + 1.1 * sway, 23, 4, 1, BEARD);
        cell(g, ox, oy, 11 + 0.3 * sway, 16, 1, 5, BEARD_SHADE);
        cell(g, ox, oy, 14 + 0.5 * sway, 16, 1, 5, BEARD_SHADE);
        cell(g, ox, oy, 12.5 + 0.8 * sway, 20, 1, 3, BEARD_SHADE);

        // Crown of lightning between the horns
        cell(g, ox, oy, 8, 5, 10, 1, GOLD);
        int[] crownHeight = {2, 3, 4, 3, 2};
        for (int i = 0; i < 5; i++) {
            int h = crownHeight[i] + (((int) (t / 6) + i) % 2 == 0 ? 0 : 1);
            cell(g, ox, oy, 9 + 2 * i, 5 - h, 1, h, new Color(255, 232, 90));
        }
        if ((int) (t / 5) % 3 == 0) {
            cell(g, ox, oy, 1, 0, 1, 1, new Color(255, 232, 90));      // sparks at the horn tips
            cell(g, ox, oy, 24, 0, 1, 1, new Color(255, 232, 90));
        }
    }
}