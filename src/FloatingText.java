import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/** Text that drifts upward and fades out, like "SHIELD UP!". */
public class FloatingText extends Effect {
    private static final Font FONT = new Font(Font.MONOSPACED, Font.BOLD, 14);
    private static final double LIFETIME = 60;

    private final String text;
    private final Color color;
    private double life = LIFETIME;

    public FloatingText(String text, double x, double y, Color color) {
        super(x, y);
        this.text = text;
        this.color = color;
    }

    @Override
    public void update(double dt) {
        y -= 0.8 * dt;
        life -= dt;
    }

    @Override
    public void draw(Graphics2D g) {
        int alpha = (int) (255 * Math.max(0, Math.min(1, life / LIFETIME)));
        g.setFont(FONT);
        FontMetrics fm = g.getFontMetrics();
        int tx = (int) x - fm.stringWidth(text) / 2;
        g.setColor(new Color(0, 0, 0, alpha));
        g.drawString(text, tx + 1, (int) y + 1);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        g.drawString(text, tx, (int) y);
    }

    @Override
    public boolean isFinished() {
        return life <= 0;
    }
}
