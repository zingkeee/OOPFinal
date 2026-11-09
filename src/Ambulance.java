import java.awt.Color;
import java.awt.Graphics2D;

/** White ambulance with a red cross and flashing red/blue lights. */
public class Ambulance extends EmergencyVehicle {
    public Ambulance(double speed) {
        super(speed);
    }

    @Override
    protected void drawBody(Graphics2D g, int px, int py) {
        Color red = new Color(220, 50, 47);
        Color blue = new Color(60, 110, 255);

        g.setColor(Color.WHITE);
        g.fillRect(px, py + 4, 42, 18);          // cargo box
        g.fillRect(px + 42, py + 10, 20, 12);    // cab
        g.setColor(new Color(110, 200, 255));
        g.fillRect(px + 48, py + 12, 10, 6);     // windshield
        g.setColor(red);
        g.fillRect(px, py + 18, 62, 3);          // stripe
        g.fillRect(px + 17, py + 6, 4, 9);       // red cross
        g.fillRect(px + 14, py + 9, 10, 3);

        boolean on = lightPhase();
        g.setColor(on ? red : blue);
        g.fillRect(px + 6, py + 1, 6, 3);
        g.setColor(on ? blue : red);
        g.fillRect(px + 12, py + 1, 6, 3);
    }
}
