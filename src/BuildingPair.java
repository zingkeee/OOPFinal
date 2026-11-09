import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Random;

/**
 * A top building and a bottom building with a gap between them.
 * Handles its own movement, collision check, and scoring.
 */
public class BuildingPair extends GameObject {
    public static final int WIDTH = 70;

    private static final Color[] PALETTE = {
            new Color(58, 66, 96),
            new Color(72, 60, 100),
            new Color(50, 82, 102),
            new Color(86, 72, 92),
            new Color(60, 76, 70)
    };

    private final Building top;
    private final Building bottom;
    private final double speed;
    private boolean scored = false;

    /**
     * @param x       starting x position
     * @param gapY    y where the gap begins (height of the top building)
     * @param gap     size of the gap
     * @param groundY y where the ground starts
     * @param speed   scroll speed
     */
    public BuildingPair(double x, int gapY, int gap, int groundY, double speed) {
        super(x, 0, WIDTH, groundY);
        this.speed = speed;

        Random rnd = new Random();
        Color color = PALETTE[rnd.nextInt(PALETTE.length)];

        top = new Building(x, 0, WIDTH, gapY, color, true, speed, rnd.nextLong());
        bottom = new Building(x, gapY + gap, WIDTH, groundY - (gapY + gap), color, false, speed, rnd.nextLong());
    }

    @Override
    public void update(double dt) {
        x -= speed * dt;
        top.update(dt);
        bottom.update(dt);
    }

    @Override
    public void draw(Graphics2D g) {
        top.draw(g);
        bottom.draw(g);
    }

    public boolean collidesWith(Rectangle other) {
        return top.getBounds().intersects(other) || bottom.getBounds().intersects(other);
    }

    /** Returns true exactly once: the first frame the plane has fully passed this pair. */
    public boolean checkScored(double planeX) {
        if (!scored && x + width < planeX) {
            scored = true;
            return true;
        }
        return false;
    }
}