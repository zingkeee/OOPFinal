import java.awt.Color;
import java.awt.Graphics2D;

/** Red fire truck with a ladder on top and flashing lights. */
public class FireTruck extends EmergencyVehicle {
    public FireTruck(double speed) {
        super(speed);
    }

    @Override
    protected void drawBody(Graphics2D g, int px, int py) {
        Color red = new Color(205, 40, 35);

        g.setColor(red);
        g.fillRect(px, py + 6, 44, 16);          // body
        g.fillRect(px + 44, py + 4, 18, 18);     // cab
        g.setColor(new Color(110, 200, 255));
        g.fillRect(px + 50, py + 7, 9, 7);       // windshield
        g.setColor(Color.WHITE);
        g.fillRect(px, py + 15, 62, 2);          // stripe

        // Ladder on the roof
        g.setColor(new Color(170, 175, 185));
        g.fillRect(px + 2, py + 3, 40, 2);
        for (int rx = px + 4; rx < px + 42; rx += 6) {
            g.fillRect(rx, py + 3, 2, 4);
        }

        boolean on = lightPhase();
        g.setColor(on ? new Color(255, 70, 60) : Color.WHITE);
        g.fillRect(px + 47, py + 1, 6, 3);
        g.setColor(on ? Color.WHITE : new Color(255, 70, 60));
        g.fillRect(px + 53, py + 1, 6, 3);
    }
}
