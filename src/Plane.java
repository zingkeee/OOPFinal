import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * The player's plane, a white commercial airliner. Falls with gravity, flaps up, and
 * holds powerup effects: a shield bubble, a gun (only while it has ammo), and shrink.
 */
public class Plane extends GameObject {
    private static final double GRAVITY = 0.5;
    private static final double FLAP_STRENGTH = -8.0;
    private static final int PIXEL_SCALE = 3;
    private static final double SHRINK_SCALE = 0.6;
    private static final double SHRINK_FRAMES = 420;
    private static final int MAX_AMMO = 30;
    public static final int MAX_SHIELDS = 3;   // how many shield layers can stack
    private static final Color SHIELD_COLOR = new Color(80, 230, 230);
    // Where the gun pivots, as an offset from the plane's center (at normal size)
    private static final double GUN_PIVOT_X = 7;
    private static final double GUN_PIVOT_Y = 13;     // just below the belly, like a turret
    private static final double BARREL_LENGTH = 24;

    // Commercial airliner: white body, blue tail and stripe, passenger windows, wing and engine
    private static final String[] SPRITE_ROWS = {
            "...UU.............",
            "..UUU.............",
            "..UUUWWWWWWWWWWW..",
            ".UWBWBWBWBWBWBWKKW",
            ".WUUUUUUUUUUUUUUWW",
            "..LLLLLLLLLLLLLLL.",
            ".....GGGGGGGG.....",
            ".......DDD........"
    };
    private static final BufferedImage SPRITE = PixelArt.create(SPRITE_ROWS);
    private static final BufferedImage SHIELD_RING = PixelArt.ring(24, SHIELD_COLOR);
    private static final int BASE_WIDTH = SPRITE.getWidth() * PIXEL_SCALE;
    private static final int BASE_HEIGHT = SPRITE.getHeight() * PIXEL_SCALE;

    private final double homeX;
    private double velocity = 0;

    // Powerup state
    private int shieldLayers = 0;
    private double invulnerable = 0;   // frames of invulnerability after a shield pops
    private double shrinkTimer = 0;
    private int ammo = 0;
    private double cooldown = 0;
    private double muzzleFlash = 0;
    private double aimX;
    private double aimY;

    public Plane(double x, double y) {
        super(x, y, BASE_WIDTH, BASE_HEIGHT);
        this.homeX = x;
        this.aimX = x + 300;     // until the mouse moves, aim straight ahead
        this.aimY = y;
    }

    // ----- geometry helpers -----

    /** How far the plane is tilted, based on how fast it is moving up or down. */
    private double tilt() {
        return Math.toRadians(Math.max(-30, Math.min(60, velocity * 4)));
    }

    private double scale() {
        return width / (double) BASE_WIDTH;
    }

    /** A point on the plane (offset from its center at normal size), rotated with the tilt. */
    private double[] pointOnPlane(double offsetX, double offsetY) {
        double s = scale();
        double a = tilt();
        double dx = offsetX * s;
        double dy = offsetY * s;
        double cx = x + width / 2.0;
        double cy = y + height / 2.0;
        return new double[] {
                cx + dx * Math.cos(a) - dy * Math.sin(a),
                cy + dx * Math.sin(a) + dy * Math.cos(a)
        };
    }

    /** Where the vapor trail starts: behind the engine. */
    public double getTrailX() { return pointOnPlane(-6, 10)[0]; }
    public double getTrailY() { return pointOnPlane(-6, 10)[1]; }

    // ----- actions -----

    public void flap() {
        velocity = FLAP_STRENGTH;
    }

    /** Tells the gun where the cursor is (in game coordinates). */
    public void aimAt(double targetX, double targetY) {
        this.aimX = targetX;
        this.aimY = targetY;
    }

    /** The direction the gun points, in radians (0 = straight right, positive = downward). */
    private double gunAngle() {
        double[] pivot = pointOnPlane(GUN_PIVOT_X, GUN_PIVOT_Y);
        return Math.atan2(aimY - pivot[1], aimX - pivot[0]);
    }

    /**
     * Fires from the tip of the gun barrel, toward the cursor.
     * Returns null if there is no gun (no ammo) or it is still cooling down.
     */
    public Bullet shoot() {
        if (ammo <= 0 || cooldown > 0) {
            return null;
        }
        ammo--;
        cooldown = 10;
        muzzleFlash = 4;

        double[] pivot = pointOnPlane(GUN_PIVOT_X, GUN_PIVOT_Y);
        double angle = gunAngle();
        double reach = BARREL_LENGTH * scale();
        return new Bullet(pivot[0] + Math.cos(angle) * reach, pivot[1] + Math.sin(angle) * reach, angle);
    }

    /**
     * Called when something dangerous touches the plane.
     * @return true if the hit is fatal, false if the plane survived
     */
    public boolean takeHit() {
        if (invulnerable > 0) {
            return false;
        }
        if (shieldLayers > 0) {
            shieldLayers--;       // one layer absorbs the hit
            invulnerable = 60;
            return false;
        }
        return true;
    }

    // ----- powerups -----

    /** Each shield pickup adds a layer (up to MAX_SHIELDS). Each hit removes one. */
    public void activateShield() { shieldLayers = Math.min(MAX_SHIELDS, shieldLayers + 1); }

    public void addAmmo(int amount) { ammo = Math.min(MAX_AMMO, ammo + amount); }

    public void activateShrink() {
        if (shrinkTimer <= 0) {
            applySize(SHRINK_SCALE);
        }
        shrinkTimer = SHRINK_FRAMES;
    }

    /** Resize but keep the plane centered on the same spot. */
    private void applySize(double scale) {
        double cx = x + width / 2.0;
        double cy = y + height / 2.0;
        width = (int) Math.round(BASE_WIDTH * scale);
        height = (int) Math.round(BASE_HEIGHT * scale);
        x = cx - width / 2.0;
        y = cy - height / 2.0;
    }

    // ----- state helpers -----

    public void reset(double startY) {
        applySize(1.0);
        x = homeX;
        y = startY;
        velocity = 0;
        shieldLayers = 0;
        invulnerable = 0;
        shrinkTimer = 0;
        ammo = 0;
        cooldown = 0;
        muzzleFlash = 0;
    }

    public void stopAtCeiling() {
        y = 0;
        if (velocity < 0) {
            velocity = 0;
        }
    }

    public void bounceOnGround(double groundY) {
        y = groundY - height;
        velocity = FLAP_STRENGTH;
    }

    public void rest(double groundY) {
        y = groundY - height;
        velocity = 0;
    }

    public boolean isShielded() { return shieldLayers > 0; }
    public int getShieldLayers() { return shieldLayers; }
    public boolean isInvulnerable() { return invulnerable > 0; }
    public boolean hasGun() { return ammo > 0; }
    public int getAmmo() { return ammo; }
    public double getShrinkSeconds() { return Math.max(0, shrinkTimer) / 60.0; }

    /** Slightly smaller than the drawing so near-misses feel fair. */
    @Override
    public Rectangle getBounds() {
        return new Rectangle((int) x + 4, (int) y + 3, width - 8, height - 6);
    }

    @Override
    public void update(double dt) {
        velocity += GRAVITY * dt;
        y += velocity * dt;

        if (invulnerable > 0) invulnerable -= dt;
        if (cooldown > 0) cooldown -= dt;
        if (muzzleFlash > 0) muzzleFlash -= dt;
        if (shrinkTimer > 0) {
            shrinkTimer -= dt;
            if (shrinkTimer <= 0) {
                applySize(1.0);
            }
        }
    }

    // ----- drawing -----

    /** A size, scaled with the plane (never smaller than 1 pixel). */
    private static int sc(double value, double scale) {
        return Math.max(1, (int) Math.round(value * scale));
    }

    /** A position (can be negative), scaled with the plane. */
    private static int pos(double value, double scale) {
        return (int) Math.round(value * scale);
    }

    /** The gun is mounted under the nose and swivels to point at the cursor. */
    private void drawGun(Graphics2D g) {
        double s = scale();
        double[] pivot = pointOnPlane(GUN_PIVOT_X, GUN_PIVOT_Y);
        double angle = gunAngle();

        AffineTransform old = g.getTransform();
        g.translate(pivot[0], pivot[1]);
        g.rotate(angle);
        if (Math.cos(angle) < 0) {
            g.scale(1, -1);     // keeps the grip underneath when aiming backward
        }

        // Black outlines first, so the gun stands out against the white plane
        g.setColor(Color.BLACK);
        g.fillRect(pos(-5, s), pos(-4, s), sc(14, s), sc(8, s));          // body outline
        g.fillRect(pos(5, s), pos(-3, s), sc(19, s), sc(5, s));           // barrel outline
        g.fillRect(pos(-4, s), pos(2, s), sc(6, s), sc(8, s));            // grip outline

        g.setColor(new Color(130, 75, 35));
        g.fillRect(pos(-3, s), pos(3, s), sc(4, s), sc(6, s));            // grip
        g.setColor(new Color(70, 76, 92));
        g.fillRect(pos(-4, s), pos(-3, s), sc(12, s), sc(6, s));          // body
        g.setColor(new Color(95, 102, 120));
        g.fillRect(pos(6, s), pos(-2, s), sc(15, s), sc(3, s));           // barrel
        g.setColor(new Color(200, 205, 220));
        g.fillRect(pos(6, s), pos(-2, s), sc(15, s), sc(1, s));           // barrel highlight
        g.setColor(new Color(30, 32, 40));
        g.fillRect(pos(20, s), pos(-2, s), sc(3, s), sc(3, s));           // muzzle

        if (muzzleFlash > 0) {
            g.setColor(new Color(255, 230, 120));
            g.fillRect(pos(23, s), pos(-4, s), sc(7, s), sc(7, s));
            g.setColor(Color.WHITE);
            g.fillRect(pos(24, s), pos(-2, s), sc(4, s), sc(3, s));
        }
        g.setTransform(old);
    }

    /** One pulsing bubble per shield layer, with sparkles orbiting the outermost one. */
    private void drawShield(Graphics2D g, double cx, double cy) {
        double t = System.currentTimeMillis() / 1000.0;
        int base = Math.max(width, height) + 24;

        Composite old = g.getComposite();
        for (int layer = 0; layer < shieldLayers; layer++) {
            int d = base + layer * 10;
            float alpha = (float) (0.7 + 0.25 * Math.sin(t * 6 + layer * 1.5));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.drawImage(SHIELD_RING, (int) (cx - d / 2.0), (int) (cy - d / 2.0), d, d, null);
        }
        g.setComposite(old);

        int outer = base + (shieldLayers - 1) * 10;
        int sparkles = 4 + 2 * (shieldLayers - 1);
        for (int i = 0; i < sparkles; i++) {
            double angle = t * 3 + i * 2 * Math.PI / sparkles;
            int sx = (int) (cx + Math.cos(angle) * outer / 2.0) - 2;
            int sy = (int) (cy + Math.sin(angle) * outer / 2.0) - 2;
            g.setColor(i % 2 == 0 ? Color.WHITE : SHIELD_COLOR);
            g.fillRect(sx, sy, 4, 4);
        }
    }

    @Override
    public void draw(Graphics2D g) {
        AffineTransform oldTransform = g.getTransform();
        Composite oldComposite = g.getComposite();

        boolean blink = invulnerable > 0 && ((int) (invulnerable / 4)) % 2 == 0;
        if (blink) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        }

        double cx = x + width / 2.0;
        double cy = y + height / 2.0;
        g.rotate(tilt(), cx, cy);

        g.drawImage(SPRITE, (int) x, (int) y, width, height, null);
        g.setTransform(oldTransform);

        // The gun is drawn after the body, so it can aim independently of the plane's tilt
        if (hasGun()) {
            drawGun(g);
        }
        g.setComposite(oldComposite);

        if (shieldLayers > 0) {
            drawShield(g, cx, cy);
        }
    }
}