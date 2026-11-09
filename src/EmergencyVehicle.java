import java.awt.Color;
import java.awt.Graphics2D;

/**
 * A rescue vehicle racing along the road at the bottom of the screen.
 * Subclasses (Ambulance, FireTruck) only draw their own body; movement, wheels,
 * and flashing lights are shared here.
 */
public abstract class EmergencyVehicle extends GameObject {
    public static final int W = 64;
    public static final int H = 28;

    private final double speed;

    protected EmergencyVehicle(double speed) {
        super(-W - 10, GamePanel.GROUND_Y + 14 - H, W, H);
        this.speed = speed;
    }

    @Override
    public void update(double dt) {
        x += speed * dt;
    }

    @Override
    public boolean isOffScreen() {
        return x > GamePanel.WIDTH;
    }

    /** Alternates every 150 ms: drives the flashing lights. */
    protected boolean lightPhase() {
        return (System.currentTimeMillis() / 150) % 2 == 0;
    }

    /** Draws everything except the wheels. (px, py) is the top-left corner. */
    protected abstract void drawBody(Graphics2D g, int px, int py);

    @Override
    public void draw(Graphics2D g) {
        int px = (int) x;
        int py = (int) y + ((System.currentTimeMillis() / 100) % 2 == 0 ? 0 : 1);   // little road bounce

        drawBody(g, px, py);

        // Wheels
        for (int wheelX : new int[] {px + 8, px + 46}) {
            g.setColor(Color.BLACK);
            g.fillRect(wheelX, py + 20, 10, 8);
            g.setColor(new Color(150, 155, 165));
            g.fillRect(wheelX + 3, py + 23, 4, 3);
        }
    }
}
