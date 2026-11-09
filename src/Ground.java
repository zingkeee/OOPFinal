import java.awt.Color;
import java.awt.Graphics2D;

/** Scrolling pixel ground at the bottom of the screen. */
public class Ground extends GameObject {
    private double speed = 3;
    private double offset = 0;

    public Ground() {
        super(0, GamePanel.GROUND_Y, GamePanel.WIDTH, GamePanel.GROUND_HEIGHT);
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    @Override
    public void update(double dt) {
        offset = (offset + speed * dt) % 32;
    }

    @Override
    public void draw(Graphics2D g) {
        int gy = (int) y;

        g.setColor(new Color(60, 40, 30));          // dirt
        g.fillRect(0, gy, width, height);

        for (int gx = 0; gx < GamePanel.WIDTH + 32; gx += 16) {
            int px = gx - (int) offset;
            boolean even = (gx / 16) % 2 == 0;

            g.setColor(even ? new Color(34, 110, 60) : new Color(28, 92, 50));   // grass
            g.fillRect(px, gy, 16, 16);
            if (even) {
                g.setColor(new Color(50, 140, 75));
                g.fillRect(px + 4, gy - 4, 4, 4);                                // tuft
                g.setColor(new Color(80, 55, 40));
                g.fillRect(px + 4, gy + 24, 8, 8);                               // dirt speck
            } else {
                g.setColor(new Color(45, 30, 22));
                g.fillRect(px + 2, gy + 40, 8, 8);
            }
        }
    }
}
