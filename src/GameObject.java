import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Base class for everything that lives in the game world.
 * Plane, Building, Hazard, PowerUp, etc. all extend this.
 */
public abstract class GameObject {
    protected double x, y;
    protected int width, height;

    public GameObject(double x, double y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** @param dt time scale: 1.0 = normal speed, 0.5 = slow motion */
    public abstract void update(double dt);

    public abstract void draw(Graphics2D g);

    /** Hitbox used for collision detection. */
    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, width, height);
    }

    /** True once the object has scrolled out of the left side of the screen. */
    public boolean isOffScreen() {
        return x + width < 0;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
