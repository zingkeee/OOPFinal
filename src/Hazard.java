import java.awt.Rectangle;

/**
 * Anything dangerous that is not a building: meteors, storm clouds, tornadoes.
 * Subclasses decide how they move, look, and where they can hurt the plane.
 */
public abstract class Hazard extends GameObject {
    protected int health;

    public Hazard(double x, double y, int width, int height, int health) {
        super(x, y, width, height);
        this.health = health;
    }

    /** Called when a bullet (or a shield) hits this hazard. */
    public void damage() {
        health--;
    }

    public boolean isDestroyed() {
        return health <= 0;
    }

    /** Whether destroying this hazard throws off a few sparks. Bombs have their own blast instead. */
    public boolean leavesDebris() {
        return true;
    }

    /** Score awarded for destroying this hazard. */
    public int getPoints() {
        return 1;
    }

    /** Does this hazard hurt a plane with the given hitbox right now? */
    public boolean hurts(Rectangle planeBox) {
        return getBounds().intersects(planeBox);
    }
}