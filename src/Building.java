import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Random;

/**
 * One pixel-style building. Scrolls left every frame. A "hanging" building comes down
 * from the top of the screen.
 *
 * Some buildings have a civilian waving for help from a window, and a little rescue scene
 * plays out (decoration only, it never affects gameplay):
 *  - On buildings standing on the ground, a fire truck drives up and parks outside, its
 *    ladder extends up to the window, and a fireman climbs it, helps the civilian out,
 *    and brings them down to safety.
 *  - On hanging buildings there is no ground to put a ladder on, so a fireman appears in
 *    the next window inside the building to help.
 * A fire flickers in another window nearby.
 */
public class Building extends GameObject {
    private static final Color LIT_WINDOW = new Color(255, 214, 102);
    private static final Color DARK_WINDOW = new Color(36, 44, 66);
    private static final Color SKIN = new Color(240, 190, 150);
    private static final Color[] SHIRTS = {
            new Color(220, 60, 60), new Color(70, 120, 230), new Color(60, 190, 90), new Color(230, 140, 40)
    };
    private static final Color HELP_RED = new Color(210, 40, 40);
    private static final Color SAFE_GREEN = new Color(30, 150, 60);
    private static final Font BUBBLE_FONT = new Font(Font.MONOSPACED, Font.BOLD, 11);

    // Timeline of the rescue scene, in frames (it starts after a short random delay)
    private static final int TRUCK_FRAMES = 22;   // the fire truck drives up and parks
    private static final int EXTEND = 20;     // the ladder extends up the building
    private static final int CLIMB = 40;      // the fireman climbs up to the window
    private static final int HAND = 18;       // he reaches the civilian
    private static final int DESCEND = 45;    // they climb down together
    private static final int SAFE = 35;       // they stand safe on the ground
    private static final int T_ARRIVE = EXTEND + CLIMB;
    private static final int T_LEAVE = T_ARRIVE + HAND;
    private static final int T_GROUND = T_LEAVE + DESCEND;
    private static final int T_END = T_GROUND + SAFE;

    private final Color color;
    private final boolean hanging;
    private final double speed;
    private final boolean[][] lit;   // [column][row]: is this window on?
    private final int cols;
    private final int rows;

    // The civilian in a window (civilianCol = -1 means this building has no rescue scene)
    private final int civilianCol;
    private final int civilianRow;
    private final Color shirt;
    private final boolean mirror;    // which side of the window the waving arm sticks out
    private final int fireCol;       // window with a fire in it (-1 = none)
    private final int helperCol;     // window the fireman appears in, on hanging buildings (-1 = none)
    private final int delay;         // frames before the rescue starts
    private final FireTruck truck;   // parked outside while the rescue happens (ground buildings only)
    private double age = 0;          // frames since this building was created

    public Building(double x, double y, int width, int height, Color color,
                    boolean hanging, double speed, long seed) {
        super(x, y, width, height);
        this.color = color;
        this.hanging = hanging;
        this.speed = speed;

        this.cols = Math.max(1, (width - 18) / 16);
        this.rows = height < 36 ? 0 : (height - 36) / 22 + 1;

        Random rnd = new Random(seed);
        this.lit = new boolean[cols][rows];
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                lit[c][r] = rnd.nextDouble() < 0.65;
            }
        }

        // Maybe put a civilian in a window, usually on the row closest to the gap
        int col = -1;
        int row = -1;
        if (rows > 0 && rnd.nextDouble() < 0.45) {
            col = rnd.nextInt(cols);
            row = rnd.nextDouble() < 0.7 ? (hanging ? rows - 1 : 0) : rnd.nextInt(rows);
            lit[col][row] = true;
        }
        this.civilianCol = col;
        this.civilianRow = row;
        this.shirt = SHIRTS[rnd.nextInt(SHIRTS.length)];
        this.mirror = rnd.nextBoolean();
        this.delay = rnd.nextInt(20);
        this.truck = (col >= 0 && !hanging) ? new FireTruck(0) : null;

        // With three window columns there is room for a fire and a helper next to the civilian
        int fire = -1;
        int helper = -1;
        if (col >= 0 && cols >= 3) {
            fire = (col + 1) % cols;
            helper = hanging ? (col + 2) % cols : -1;
        }
        this.fireCol = fire;
        this.helperCol = helper;
    }

    private int windowX(int col) { return (int) x + 9 + col * 16; }
    private int windowY(int row) { return (int) y + 12 + row * 22; }

    @Override
    public void update(double dt) {
        x -= speed * dt;
        age += dt;
    }

    @Override
    public void draw(Graphics2D g) {
        int bx = (int) x;
        int by = (int) y;

        // Body with a light left edge and dark right edge (blocky shading)
        g.setColor(color);
        g.fillRect(bx, by, width, height);
        g.setColor(color.brighter());
        g.fillRect(bx, by, 4, height);
        g.setColor(color.darker());
        g.fillRect(bx + width - 10, by, 10, height);

        // Rooftop ledge on the end that faces the gap
        int ledgeY = hanging ? by + height - 8 : by;
        g.setColor(color.brighter().brighter());
        g.fillRect(bx - 4, ledgeY, width + 8, 8);

        // Windows (one of them may be on fire)
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                if (c == fireCol && r == civilianRow) {
                    drawFire(g, windowX(c), windowY(r));
                } else {
                    g.setColor(lit[c][r] ? LIT_WINDOW : DARK_WINDOW);
                    g.fillRect(windowX(c), windowY(r), 10, 12);
                }
            }
        }

        if (civilianCol >= 0) {
            drawRescueScene(g);
        }
    }

    // ----- the rescue scene -----

    private void drawRescueScene(Graphics2D g) {
        double t = age - delay;
        // On ground buildings the fireman can only start once the truck has parked
        double rt = hanging ? t : t - TRUCK_FRAMES;
        int wx = windowX(civilianCol);
        int wy = windowY(civilianRow);

        if (!hanging && t >= 0) {
            drawLadderScene(g, t, rt, wx);
        }
        if (hanging && helperCol >= 0 && rt >= T_ARRIVE - 15 && rt < T_LEAVE + 20) {
            drawHelperFireman(g);
        }

        // The civilian waves from the window until the fireman gets them out
        if (rt < T_LEAVE) {
            drawCivilianInWindow(g, wx, wy);
            boolean thanks = rt >= T_ARRIVE;
            boolean above = wy >= 26;
            drawBubble(g, wx + 5, above ? wy : wy + 12, above,
                    thanks ? "THANKS!" : "HELP!", thanks ? SAFE_GREEN : HELP_RED);
        }
    }

    /** The fire truck, its ladder, the fireman climbing it, and the two of them coming back down. */
    private void drawLadderScene(Graphics2D g, double t, double rt, int lx) {
        int bottom = (int) y + height;
        int topY = windowY(civilianRow) + 2;     // top of a person who is level with the window
        int roofY = bottom - 24;                 // top of a person standing on the truck's roof

        drawTruck(g, t, bottom);
        drawLadder(g, lx, Math.max(0.0, Math.min(1.0, rt / EXTEND)), bottom - 12);

        int step = (int) (age / 6) % 2;          // alternates the arms and legs while climbing
        if (rt < EXTEND) {
            // Ready on the truck's roof (and riding along while the truck is still driving in)
            int ride = -(int) ((1 - Math.min(1.0, t / TRUCK_FRAMES)) * 110);
            drawPerson(g, lx + 1 + ride, roofY, true, null, 0);
        } else if (rt < T_ARRIVE) {
            double p = (rt - EXTEND) / CLIMB;
            drawPerson(g, lx + 1, (int) (roofY + (topY - roofY) * p), true, null, step);
        } else if (rt < T_LEAVE) {
            double reach = (rt - T_ARRIVE) / HAND;                      // steps up to help them out
            drawPerson(g, lx + 1, (int) (topY - 12 * reach), true, null, 0);
        } else if (rt < T_GROUND) {
            double q = (rt - T_LEAVE) / DESCEND;
            int personY = (int) (topY + (roofY - topY) * q);
            drawPerson(g, lx + 1, personY - 12, true, null, step);      // fireman, just above
            drawPerson(g, lx + 1, personY, false, shirt, step);         // the rescued civilian
        } else if (rt < T_END) {
            drawPerson(g, lx - 6, roofY, true, null, 0);
            drawPerson(g, lx + 12, roofY, false, shirt, 0);
            drawBubble(g, lx + 16, roofY, true, "SAFE!", SAFE_GREEN);
        }
    }

    /** The truck drives in from the left and parks in front of the building, lights flashing. */
    private void drawTruck(Graphics2D g, double t, int bottom) {
        double slide = (1 - Math.min(1.0, t / TRUCK_FRAMES)) * 110;
        truck.x = x + 3 - slide;
        truck.y = bottom - 14;
        truck.draw(g);
    }

    /** The extended ladder, which stands on the truck's roof. */
    private void drawLadder(Graphics2D g, int lx, double extend, int baseY) {
        if (extend <= 0) {
            return;
        }
        int top = windowY(civilianRow) - 10;
        int visibleTop = (int) (baseY - (baseY - top) * extend);

        g.setColor(new Color(205, 210, 222));                 // rails
        g.fillRect(lx, visibleTop, 2, baseY - visibleTop);
        g.fillRect(lx + 8, visibleTop, 2, baseY - visibleTop);
        g.setColor(new Color(235, 238, 248));                 // rungs
        for (int ry = baseY - 4; ry > visibleTop; ry -= 6) {
            g.fillRect(lx + 2, ry, 6, 2);
        }
    }

    /** A small person, 8x13 pixels. Firemen wear a yellow helmet and a dark coat with a stripe. */
    private void drawPerson(Graphics2D g, int px, int py, boolean fireman, Color civilianShirt, int step) {
        Color coat = fireman ? new Color(40, 46, 78) : civilianShirt;
        Color pants = fireman ? new Color(30, 34, 50) : new Color(60, 70, 110);

        // Legs
        g.setColor(pants);
        g.fillRect(px + 1, py + 10, 3, step == 0 ? 2 : 3);
        g.fillRect(px + 4, py + 10, 3, step == 0 ? 3 : 2);

        // Body and arms
        g.setColor(coat);
        g.fillRect(px + 1, py + 5, 6, 5);
        g.fillRect(px, py + 5, 2, 3);
        g.fillRect(px + 6, py + 5, 2, 3);
        if (fireman) {
            g.setColor(new Color(255, 214, 60));              // reflective stripe
            g.fillRect(px + 1, py + 7, 6, 1);
        }

        // Hands (one up, one down, swapping with each step)
        g.setColor(SKIN);
        g.fillRect(px, step == 0 ? py + 3 : py + 8, 2, 2);
        g.fillRect(px + 6, step == 0 ? py + 8 : py + 3, 2, 2);

        // Head
        g.fillRect(px + 2, py + 2, 4, 4);
        if (fireman) {
            g.setColor(new Color(255, 200, 40));
            g.fillRect(px + 1, py, 6, 3);                     // helmet
            g.fillRect(px, py + 2, 8, 1);                     // brim
        } else {
            g.setColor(new Color(60, 40, 30));
            g.fillRect(px + 2, py + 1, 4, 2);                 // hair
        }
    }

    /** Hanging buildings: a fireman leans into the next window to help from the inside. */
    private void drawHelperFireman(Graphics2D g) {
        int hx = windowX(helperCol);
        int hy = windowY(civilianRow);
        Shape oldClip = g.getClip();
        g.clipRect(hx, hy, 10, 12);                           // only the part inside the window shows
        drawPerson(g, hx + 1, hy - 1, true, null, (int) (age / 8) % 2);
        g.setClip(oldClip);
    }

    private void drawFire(Graphics2D g, int wx, int wy) {
        int f = (int) ((System.currentTimeMillis() / 120) % 3);
        g.setColor(f == 0 ? new Color(255, 110, 30) : new Color(255, 150, 40));
        g.fillRect(wx, wy, 10, 12);
        g.setColor(new Color(255, 60, 20));
        g.fillRect(wx + 1, wy + 7 - f, 3, 5 + f);
        g.fillRect(wx + 6, wy + 5 + f, 3, 7 - f);
        g.setColor(new Color(255, 230, 90));
        g.fillRect(wx + 3, wy + 3 + f, 4, 9 - f);
        g.setColor(Color.WHITE);
        g.fillRect(wx + 4, wy + 8, 2, 4);
    }

    // ----- the civilian waving from the window -----

    /** Fills a rectangle positioned relative to the window, optionally mirrored left-to-right. */
    private void part(Graphics2D g, int wx, int wy, boolean flip, int dx, int dy, int w, int h) {
        int px = flip ? wx + 10 - dx - w : wx + dx;
        g.fillRect(px, wy + dy, w, h);
    }

    private void drawCivilianInWindow(Graphics2D g, int wx, int wy) {
        // Waving cycle: the forearm sways left, up, right, up (every 150 ms per frame)
        int frame = (int) ((System.currentTimeMillis() / 150) % 4);
        int sway = frame == 0 ? -1 : (frame == 2 ? 1 : 0);

        // Resting arm, inside the window
        g.setColor(SKIN);
        part(g, wx, wy, mirror, 0, 7, 2, 4);

        // Body and head
        g.setColor(shirt);
        part(g, wx, wy, mirror, 2, 6, 6, 6);
        g.setColor(SKIN);
        part(g, wx, wy, mirror, 3, 1, 4, 4);
        g.setColor(new Color(60, 40, 30));
        part(g, wx, wy, mirror, 3, 1, 4, 1);

        // Waving arm: reaches out through the window, then the forearm swings side to side
        g.setColor(shirt);
        part(g, wx, wy, mirror, 8, 6, 3, 2);                       // sleeve
        g.setColor(SKIN);
        part(g, wx, wy, mirror, 10, 6, 3, 2);                      // upper arm, sticking out of the window
        part(g, wx, wy, mirror, 11 + sway, 3, 2, 4);               // lower forearm
        part(g, wx, wy, mirror, 11 + 3 * sway, -1, 2, 4);          // upper forearm, swings wide
        part(g, wx, wy, mirror, 10 + 3 * sway, -3, 4, 3);          // hand
    }

    /**
     * A speech bubble sized to its text, with a little tail pointing at (centerX, targetY).
     * It goes above the target, or below it.
     */
    private void drawBubble(Graphics2D g, int centerX, int targetY, boolean above, String text, Color textColor) {
        g.setFont(BUBBLE_FONT);
        FontMetrics fm = g.getFontMetrics();
        int bw = fm.stringWidth(text) + 8;
        int bh = 16;
        int bubbleX = centerX - bw / 2;
        int bubbleY = above ? targetY - bh - 6 : targetY + 6;

        g.setColor(Color.WHITE);
        g.fillRect(bubbleX, bubbleY, bw, bh);
        g.setColor(Color.BLACK);
        g.drawRect(bubbleX, bubbleY, bw, bh);

        int tailY = above ? bubbleY + bh : bubbleY - 4;
        g.setColor(Color.WHITE);
        g.fillRect(centerX - 2, tailY, 4, 5);
        g.setColor(Color.BLACK);
        g.fillRect(centerX - 3, tailY, 1, 5);
        g.fillRect(centerX + 2, tailY, 1, 5);

        g.setColor(textColor);
        g.drawString(text, bubbleX + 4, bubbleY + 12);
    }
}