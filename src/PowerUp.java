import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * A floating pickup with a pixel-art icon. Each Type knows how to apply its own effect
 * (polymorphism!), and has its own icon, pickup sound and pickup message.
 */
public class PowerUp extends GameObject {
    public static final int SIZE = 30;

    public enum Type {
        SHIELD(PowerUpIcons.SHIELD, new Color(80, 230, 230), "SHIELD UP!") {
            @Override
            public void apply(Plane plane, GamePanel game) {
                plane.activateShield();
            }

            @Override
            public SoundManager.Sfx pickupSound() {
                return SoundManager.Sfx.SHIELD_UP;
            }

            /** Shows how many layers are stacked. */
            @Override
            public String pickupText(Plane plane) {
                int layers = plane.getShieldLayers();
                if (layers >= Plane.MAX_SHIELDS) {
                    return "SHIELD MAX!";
                }
                return layers > 1 ? "SHIELD x" + layers : "SHIELD UP!";
            }
        },
        AMMO(PowerUpIcons.GUN, new Color(255, 90, 70), "GUN +12") {
            @Override
            public void apply(Plane plane, GamePanel game) {
                plane.addAmmo(12);
            }
        },
        SLOW_MO(PowerUpIcons.TIME, new Color(120, 235, 130), "SLOW-MO!") {
            @Override
            public void apply(Plane plane, GamePanel game) {
                game.activateSlowMo();
            }
        },
        SHRINK(PowerUpIcons.SHRINK, new Color(255, 120, 90), "SHRINK!") {
            @Override
            public void apply(Plane plane, GamePanel game) {
                plane.activateShrink();
            }
        };

        private final BufferedImage icon;
        private final Color color;
        private final String pickupText;

        Type(BufferedImage icon, Color color, String pickupText) {
            this.icon = icon;
            this.color = color;
            this.pickupText = pickupText;
        }

        public BufferedImage getIcon() { return icon; }
        public Color getColor() { return color; }

        /** The message shown when this powerup is collected. */
        public String pickupText(Plane plane) {
            return pickupText;
        }

        /** The sound played when this powerup is collected. */
        public SoundManager.Sfx pickupSound() {
            return SoundManager.Sfx.POWERUP;
        }

        /** What happens when the plane picks this powerup up. */
        public abstract void apply(Plane plane, GamePanel game);
    }

    private final Type type;
    private final double speed;
    private final double baseY;
    private double time = 0;

    public PowerUp(Type type, double x, double y, double speed) {
        super(x, y, SIZE, SIZE);
        this.type = type;
        this.speed = speed;
        this.baseY = y;
    }

    public Type getType() { return type; }

    @Override
    public void update(double dt) {
        x -= speed * dt;
        time += dt;
        y = baseY + Math.sin(time * 0.1) * 8;   // gentle bobbing
    }

    /** Draws the framed icon: colored bevel border, dark center, pixel-art icon on top. */
    public static void drawBadge(Graphics2D g, Type type, int px, int py, int outline) {
        Color c = type.getColor();
        g.setColor(Color.BLACK);
        g.fillRect(px - outline, py - outline, SIZE + 2 * outline, SIZE + 2 * outline);
        g.setColor(c);
        g.fillRect(px, py, SIZE, SIZE);
        g.setColor(c.brighter());
        g.fillRect(px, py, SIZE, 2);
        g.setColor(c.darker());
        g.fillRect(px, py + SIZE - 2, SIZE, 2);
        g.setColor(new Color(24, 28, 44));
        g.fillRect(px + 3, py + 3, SIZE - 6, SIZE - 6);
        g.drawImage(type.getIcon(), px + 3, py + 3, SIZE - 6, SIZE - 6, null);
    }

    @Override
    public void draw(Graphics2D g) {
        int px = (int) x;
        int py = (int) y;
        Color c = type.getColor();

        // Pulsing glow so powerups stand out against the storm
        int glow = 4 + (int) (Math.sin(time * 0.15) * 2 + 2);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 55));
        g.fillRect(px - glow - 3, py - glow - 3, SIZE + 2 * (glow + 3), SIZE + 2 * (glow + 3));

        drawBadge(g, type, px, py, 3);
    }
}