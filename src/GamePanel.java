import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Owns all game objects, runs the game loop, spawns things, and handles collisions.
 *
 * Controls: SPACE = flap / start / restart, MOUSE = aim the gun, LEFT CLICK = shoot (needs a gun powerup),
 *           P or ESC = pause, Q = quit to title (while paused),
 *           M = mute all, N = music on/off, + and - = volume.
 */
public class GamePanel extends JPanel implements ActionListener {
    public static final int WIDTH = 480;
    public static final int HEIGHT = 700;
    public static final int GROUND_HEIGHT = 60;
    public static final int GROUND_Y = HEIGHT - GROUND_HEIGHT;

    private static final double SPAWN_DISTANCE = 280;  // pixels between buildings
    private static final int SLOW_MO_FRAMES = 360;     // 6 seconds
    private static final int BOSS_EVERY = 25;          // points between boss fights

    private enum State { TITLE, READY, PLAYING, PAUSED, GAME_OVER }

    private final Timer timer;
    private final StormBackground background = new StormBackground();
    private final Ground ground = new Ground();
    private final VaporTrail trail = new VaporTrail();
    private final Plane plane;
    private final List<BuildingPair> buildings = new ArrayList<>();
    private final List<Hazard> hazards = new ArrayList<>();
    private final List<PowerUp> powerUps = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Effect> effects = new ArrayList<>();
    private final List<EmergencyVehicle> vehicles = new ArrayList<>();
    private final Random random = new Random();

    private State state = State.TITLE;
    private int score = 0;
    private int highScore = 0;
    private boolean newBest = false;
    private double distance = 0;        // scrolled distance since the last building spawn
    private boolean eventSpawned = true;
    private int lastGapY = 190;
    private double slowTimer = 0;
    private int gameOverTimer = 0;
    private double vehicleTimer = 120;
    private double trailTimer = 0;
    private int volumeShowTimer = 0;
    private int shakeFrames = 0;
    private int shakeAmp = 0;

    // Mouse position (the gun aims here)
    private int mouseX = WIDTH;
    private int mouseY = HEIGHT / 2;
    private boolean mouseInside = false;

    // Boss fight
    private StormBoss boss = null;
    private int bossLevel = 0;
    private int nextBossScore = BOSS_EVERY;
    private int bannerTimer = 0;
    private double supplyTimer = 0;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        hideSystemCursor();
        SoundManager.init();

        // Load the saved high score and sound settings
        highScore = SaveData.getInt("highScore", 0);
        SoundManager.setVolume(SaveData.getDouble("volume", 1.0));
        SoundManager.setMusicOn(SaveData.getBoolean("musicOn", true));

        plane = new Plane(100, HEIGHT / 2.0);
        goToTitle();

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int key = e.getKeyCode();
                if (key == KeyEvent.VK_SPACE) {
                    handleSpace();
                } else if (key == KeyEvent.VK_P) {
                    handlePause(false);
                } else if (key == KeyEvent.VK_ESCAPE) {
                    handlePause(true);
                } else if (key == KeyEvent.VK_Q) {
                    if (state == State.PAUSED) {
                        goToTitle();
                    }
                } else if (key == KeyEvent.VK_M) {
                    SoundManager.toggleMute();
                } else if (key == KeyEvent.VK_N) {
                    SoundManager.toggleMusic();
                    SaveData.set("musicOn", String.valueOf(SoundManager.isMusicOn()));
                } else if (key == KeyEvent.VK_EQUALS || key == KeyEvent.VK_PLUS || key == KeyEvent.VK_ADD) {
                    changeVolume(0.1);
                } else if (key == KeyEvent.VK_MINUS || key == KeyEvent.VK_SUBTRACT) {
                    changeVolume(-0.1);
                }
            }
        });

        // The gun aims at the mouse; the left button shoots (clicking also gives the game focus back)
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                mouseX = e.getX();
                mouseY = e.getY();
                mouseInside = true;
                if (e.getButton() == MouseEvent.BUTTON1) {
                    plane.aimAt(mouseX, mouseY);
                    handleShoot();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                mouseInside = true;
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouseInside = false;
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                mouseInside = true;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseMoved(e);
            }
        });

        // Pause automatically if the window loses focus mid-game
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (state == State.PLAYING) {
                    state = State.PAUSED;
                }
            }
        });

        timer = new Timer(16, this); // ~60 FPS
        timer.start();
    }

    // ----- input -----

    private void handleSpace() {
        if (state == State.TITLE) {
            state = State.READY;
        } else if (state == State.READY) {
            state = State.PLAYING;
            plane.flap();
            SoundManager.play(SoundManager.Sfx.FLAP);
        } else if (state == State.PLAYING) {
            plane.flap();
            SoundManager.play(SoundManager.Sfx.FLAP);
        } else if (state == State.GAME_OVER && gameOverTimer > 30) {
            resetGame();
        }
    }

    private void handleShoot() {
        if (state == State.PLAYING) {
            Bullet b = plane.shoot();   // null when the plane has no gun
            if (b != null) {
                bullets.add(b);
                SoundManager.play(SoundManager.Sfx.SHOOT);
            }
        }
    }

    private void handlePause(boolean escape) {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        } else if (state == State.PAUSED) {
            state = State.PLAYING;
        } else if (escape && (state == State.READY || (state == State.GAME_OVER && gameOverTimer > 30))) {
            goToTitle();
        }
    }

    private void changeVolume(double delta) {
        SoundManager.changeVolume(delta);
        SaveData.set("volume", String.valueOf(SoundManager.getVolume()));
        volumeShowTimer = 100;
        SoundManager.play(SoundManager.Sfx.SCORE);   // a beep so you can hear the new level
    }

    // ----- state -----

    private void resetGame() {
        buildings.clear();
        hazards.clear();
        powerUps.clear();
        bullets.clear();
        effects.clear();
        vehicles.clear();
        trail.clear();
        plane.reset(HEIGHT / 2.0);
        score = 0;
        newBest = false;
        distance = SPAWN_DISTANCE * 0.6;
        eventSpawned = true;
        lastGapY = 190;
        slowTimer = 0;
        boss = null;
        bossLevel = 0;
        nextBossScore = BOSS_EVERY;
        bannerTimer = 0;
        supplyTimer = 0;
        vehicleTimer = 120;
        trailTimer = 0;
        shakeFrames = 0;
        state = State.READY;
    }

    private void goToTitle() {
        resetGame();
        state = State.TITLE;
    }

    private void gameOver() {
        state = State.GAME_OVER;
        newBest = score > highScore;
        if (newBest) {
            highScore = score;
            SaveData.set("highScore", String.valueOf(highScore));
        }
        gameOverTimer = 0;
        slowTimer = 0;
    }

    /** Called by the slow-motion powerup. */
    public void activateSlowMo() {
        slowTimer = SLOW_MO_FRAMES;
    }

    private void shake(int frames, int amplitude) {
        shakeFrames = Math.max(shakeFrames, frames);
        shakeAmp = Math.max(shakeAmp, amplitude);
    }

    private double planeCenterX() { return plane.getX() + plane.getWidth() / 2.0; }
    private double planeCenterY() { return plane.getY() + plane.getHeight() / 2.0; }

    /** The plane got hit. Returns true if that was fatal. */
    private boolean hurtPlane() {
        boolean hadShield = plane.isShielded();
        if (plane.takeHit()) {
            SoundManager.play(SoundManager.Sfx.CRASH);
            effects.add(new Explosion(planeCenterX(), planeCenterY(), 30, 4));
            shake(14, 5);
            gameOver();
            return true;
        }
        if (hadShield) {
            // A shield layer shatters (the last one is a bigger blast)
            boolean last = !plane.isShielded();
            Color cyan = PowerUp.Type.SHIELD.getColor();
            effects.add(new Explosion(planeCenterX(), planeCenterY(), last ? 34 : 20, last ? 5.5 : 4.0,
                    Explosion.paletteFor(cyan)));
            effects.add(new Shockwave(planeCenterX(), planeCenterY(), Color.WHITE, last ? 110 : 80));
            effects.add(new FloatingText(last ? "SHIELD BROKEN!" : "SHIELD x" + plane.getShieldLayers(),
                    planeCenterX(), plane.getY() - 8, cyan));
            shake(last ? 8 : 5, last ? 4 : 3);
            SoundManager.play(SoundManager.Sfx.SHIELD_BREAK);
        }
        return false;
    }

    private void collectPowerUp(PowerUp.Type type) {
        type.apply(plane, this);
        SoundManager.play(type.pickupSound());

        Color color = type.getColor();
        boolean shield = type == PowerUp.Type.SHIELD;
        effects.add(new Explosion(planeCenterX(), planeCenterY(), shield ? 28 : 14, shield ? 4.5 : 3.0,
                Explosion.paletteFor(color)));
        effects.add(new Shockwave(planeCenterX(), planeCenterY(), color, shield ? 90 : 60));
        effects.add(new FloatingText(type.pickupText(plane), planeCenterX(), plane.getY() - 8, color));
    }

    // ----- difficulty -----

    private double currentSpeed() {
        return Math.min(6.0, 3.0 + (score / 5) * 0.25);
    }

    private int currentGap() {
        return Math.max(140, 175 - score);
    }

    // ----- spawning -----

    private void spawnBuilding() {
        int gap = currentGap();
        int minGapY = 80;
        int maxGapY = GROUND_Y - gap - 80;
        int gapY = lastGapY + random.nextInt(301) - 150;   // stay within reach of the last gap
        gapY = Math.max(minGapY, Math.min(maxGapY, gapY));
        lastGapY = gapY;
        buildings.add(new BuildingPair(WIDTH, gapY, gap, GROUND_Y, currentSpeed()));
    }

    /** Runs halfway between two buildings: maybe a hazard, maybe a powerup. */
    private void spawnEvent() {
        double r = random.nextDouble();
        double hazardChance = score >= 2 ? Math.min(0.65, 0.2 + score * 0.03) : 0;
        if (r < hazardChance) {
            spawnHazard();
        } else if (r < hazardChance + 0.25) {
            spawnPowerUp();
        }
    }

    private void spawnHazard() {
        double speed = currentSpeed();
        double centerX = WIDTH + BuildingPair.WIDTH / 2.0;
        int kind = random.nextInt(3);
        if (kind == 0) {
            double vy = 3 + random.nextDouble() * 2;
            hazards.add(new Meteor(WIDTH + 20, -Meteor.H, -(speed + 1.5), vy));
        } else if (kind == 1) {
            hazards.add(new StormCloud(centerX - StormCloud.W / 2.0, 60 + random.nextInt(110), speed));
        } else {
            hazards.add(new Tornado(centerX - Tornado.W / 2.0, speed));
        }
    }

    private void spawnPowerUp() {
        PowerUp.Type[] types = PowerUp.Type.values();
        PowerUp.Type type = types[random.nextInt(types.length)];
        double centerX = WIDTH + BuildingPair.WIDTH / 2.0;
        double y = 140 + random.nextInt(GROUND_Y - 280);
        powerUps.add(new PowerUp(type, centerX - PowerUp.SIZE / 2.0, y, currentSpeed()));
    }

    // ----- boss -----

    private void startBoss() {
        boss = new StormBoss(bossLevel);
        plane.addAmmo(20);
        bannerTimer = 180;
        supplyTimer = 0;
        SoundManager.play(SoundManager.Sfx.WARNING);
    }

    private void defeatBoss() {
        effects.add(new Explosion(boss.getX() + boss.getWidth() / 2.0,
                boss.getY() + boss.getHeight() / 2.0, 70, 6));
        SoundManager.play(SoundManager.Sfx.BIG_EXPLOSION);
        shake(24, 7);
        score += 10;
        bossLevel++;
        nextBossScore = (score / BOSS_EVERY + 1) * BOSS_EVERY;
        boss = null;
        hazards.removeIf(h -> h instanceof BossShot);

        // Normal play resumes: buildings start coming again
        distance = SPAWN_DISTANCE * 0.6;
        eventSpawned = true;
    }

    // ----- game loop -----

    @Override
    public void actionPerformed(ActionEvent e) {
        updateMusic();
        if (volumeShowTimer > 0) {
            volumeShowTimer--;
        }
        if (state == State.PAUSED) {
            repaint();
            return;
        }
        if (shakeFrames > 0) {
            shakeFrames--;
            if (shakeFrames == 0) {
                shakeAmp = 0;
            }
        }

        plane.aimAt(mouseX, mouseY);   // the gun follows the cursor, even while the plane moves

        double dt = slowTimer > 0 ? 0.5 : 1.0;
        background.update(dt);
        if (background.consumeLightning()) {
            SoundManager.playDelayed(SoundManager.Sfx.THUNDER, 250 + random.nextInt(500));
        }

        if (state == State.PLAYING) {
            updateGame(dt);
        } else if (state == State.GAME_OVER) {
            gameOverTimer++;
            // Let the plane fall to the ground after crashing
            if (plane.getY() + plane.getHeight() < GROUND_Y) {
                plane.update(1.0);
            } else {
                plane.rest(GROUND_Y);
            }
            updateEffects(1.0);
            updateVehicles(1.0);
        }
        repaint();
    }

    /** Picks the background music for the current situation. */
    private void updateMusic() {
        SoundManager.Track track;
        if (state == State.PAUSED || state == State.GAME_OVER) {
            track = SoundManager.Track.NONE;
        } else if (state == State.PLAYING && boss != null) {
            track = SoundManager.Track.BOSS;
        } else {
            track = SoundManager.Track.STORM;
        }
        SoundManager.setMusic(track);
    }

    private void updateEffects(double dt) {
        Iterator<Effect> it = effects.iterator();
        while (it.hasNext()) {
            Effect ex = it.next();
            ex.update(dt);
            if (ex.isFinished()) {
                it.remove();
            }
        }
    }

    /** Ambulances and fire trucks race along the bottom every few seconds. */
    private void updateVehicles(double dt) {
        vehicleTimer -= dt;
        if (vehicleTimer <= 0) {
            vehicleTimer = 240 + random.nextInt(300);
            double speed = 3.5 + random.nextDouble() * 1.5;
            EmergencyVehicle v = random.nextBoolean() ? new Ambulance(speed) : new FireTruck(speed);
            vehicles.add(v);
            SoundManager.play(SoundManager.Sfx.SIREN);
        }
        Iterator<EmergencyVehicle> it = vehicles.iterator();
        while (it.hasNext()) {
            EmergencyVehicle v = it.next();
            v.update(dt);
            if (v.isOffScreen()) {
                it.remove();
            }
        }
    }

    /**
     * @param dt time scale for the WORLD (0.5 during slow-mo). The plane and its bullets
     *           always run at normal speed, so slow-mo gives you extra time to react.
     */
    private void updateGame(double dt) {
        if (slowTimer > 0) {
            slowTimer--;
        }
        if (bannerTimer > 0) {
            bannerTimer--;
        }

        double speedNow = currentSpeed();

        plane.update(1.0);
        if (plane.getY() < 0) {
            plane.stopAtCeiling();
        }
        ground.setSpeed(speedNow);
        ground.update(dt);
        updateVehicles(dt);

        // Vapor trail: a new puff every other frame
        trail.setSpeed(speedNow);
        trailTimer += 1.0;
        if (trailTimer >= 2) {
            trailTimer -= 2;
            trail.emit(plane.getTrailX(), plane.getTrailY());
        }
        trail.update(dt);

        if (boss == null && score >= nextBossScore) {
            startBoss();
        }

        // No new buildings or random events while a boss is around
        if (boss == null) {
            distance += speedNow * dt;
            if (distance >= SPAWN_DISTANCE) {
                distance -= SPAWN_DISTANCE;
                spawnBuilding();
                eventSpawned = false;
            } else if (!eventSpawned && distance >= SPAWN_DISTANCE / 2) {
                eventSpawned = true;
                spawnEvent();
            }
        }

        Rectangle box = plane.getBounds();

        // Buildings
        Iterator<BuildingPair> bi = buildings.iterator();
        while (bi.hasNext()) {
            BuildingPair pair = bi.next();
            pair.update(dt);

            if (pair.checkScored(plane.getX())) {
                score++;
                SoundManager.play(SoundManager.Sfx.SCORE);
            }
            if (!plane.isInvulnerable() && pair.collidesWith(box)) {
                if (hurtPlane()) {
                    return;
                }
            }
            if (pair.isOffScreen()) {
                bi.remove();
            }
        }

        // Hazards
        Iterator<Hazard> hi = hazards.iterator();
        while (hi.hasNext()) {
            Hazard h = hi.next();
            h.update(dt);

            if (!plane.isInvulnerable() && h.hurts(box)) {
                if (hurtPlane()) {
                    return;
                }
                h.damage();   // the shield popped and hurt the hazard too
            }

            if (h.isDestroyed()) {
                score += h.getPoints();
                effects.add(new Explosion(h.getX() + h.getWidth() / 2.0,
                        h.getY() + h.getHeight() / 2.0, h.getPoints() > 0 ? 20 : 12, 3.5));
                if (h.getPoints() > 0) {
                    SoundManager.play(SoundManager.Sfx.EXPLOSION);
                }
                hi.remove();
            } else if (h.isOffScreen()) {
                hi.remove();
            }
        }

        // Boss
        if (boss != null) {
            boss.setTarget(planeCenterX(), planeCenterY());
            boss.update(dt);
            hazards.addAll(boss.takeShots());

            if (!plane.isInvulnerable() && boss.hurts(box)) {
                if (hurtPlane()) {
                    return;
                }
            }

            // Drop supplies during the fight so the player can keep shooting
            supplyTimer += dt;
            if (supplyTimer >= 330) {
                supplyTimer = 0;
                PowerUp.Type type = random.nextDouble() < 0.7 ? PowerUp.Type.AMMO : PowerUp.Type.SHIELD;
                powerUps.add(new PowerUp(type, WIDTH + 10, 150 + random.nextInt(270), 3.0));
            }

            if (boss.isDestroyed()) {
                defeatBoss();
            }
        }

        // Bullets: fired by the plane, so they move at normal speed even in slow-mo,
        // and they fly straight through buildings
        Iterator<Bullet> bu = bullets.iterator();
        while (bu.hasNext()) {
            Bullet b = bu.next();
            b.update(1.0);

            for (Hazard h : hazards) {
                if (b.getBounds().intersects(h.getBounds())) {
                    h.damage();
                    b.expire();
                    break;
                }
            }
            if (boss != null && !b.isExpired() && b.getBounds().intersects(boss.getBounds())) {
                boss.damage();
                b.expire();
            }
            if (b.isExpired() || b.isOffScreen()) {
                bu.remove();
            }
        }

        // Powerups
        Iterator<PowerUp> pi = powerUps.iterator();
        while (pi.hasNext()) {
            PowerUp p = pi.next();
            p.update(dt);
            if (p.getBounds().intersects(box)) {
                collectPowerUp(p.getType());
                pi.remove();
            } else if (p.isOffScreen()) {
                pi.remove();
            }
        }

        // Ground: bounce if protected, otherwise it's fatal
        if (plane.getY() + plane.getHeight() >= GROUND_Y) {
            plane.bounceOnGround(GROUND_Y);
            if (!plane.isInvulnerable()) {
                hurtPlane();
            }
        }

        updateEffects(1.0);
    }

    // ----- drawing -----

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // Screen shake moves the whole world (but not the HUD text)
        int ox = 0;
        int oy = 0;
        if (shakeFrames > 0 && shakeAmp > 0) {
            ox = random.nextInt(shakeAmp * 2 + 1) - shakeAmp;
            oy = random.nextInt(shakeAmp * 2 + 1) - shakeAmp;
        }
        g2.translate(ox, oy);

        background.draw(g2);
        for (BuildingPair pair : buildings) pair.draw(g2);
        for (Hazard h : hazards) h.draw(g2);
        if (boss != null) boss.draw(g2);
        for (PowerUp p : powerUps) p.draw(g2);
        for (Bullet b : bullets) b.draw(g2);
        ground.draw(g2);
        for (EmergencyVehicle v : vehicles) v.draw(g2);
        if (state != State.TITLE) {
            trail.draw(g2);
            plane.draw(g2);
        }
        for (Effect ex : effects) ex.draw(g2);

        // Purple tint while slow-mo is active (it flickers when about to run out)
        if (slowTimer > 0 && state != State.TITLE) {
            int alpha = slowTimer < 60 ? (((int) slowTimer / 6) % 2 == 0 ? 40 : 12) : 32;
            g2.setColor(new Color(140, 90, 255, alpha));
            g2.fillRect(0, 0, WIDTH, HEIGHT);
        }

        g2.translate(-ox, -oy);
        drawHud(g2);
        drawCrosshair(g2);
    }

    private void drawHud(Graphics2D g) {
        Font big = new Font(Font.MONOSPACED, Font.BOLD, 48);
        Font mid = new Font(Font.MONOSPACED, Font.BOLD, 28);
        Font small = new Font(Font.MONOSPACED, Font.BOLD, 18);

        if (state == State.TITLE) {
            drawTitle(g);
        } else if (state == State.READY) {
            drawCentered(g, "GET READY", 220, big, Color.WHITE);
            drawCentered(g, "Press SPACE to take off", 270, small, Color.WHITE);
            drawCentered(g, "P pause   M mute   ESC title", 300, new Font(Font.MONOSPACED, Font.BOLD, 14), new Color(200, 200, 220));
        } else if (state == State.PLAYING || state == State.PAUSED) {
            drawCentered(g, String.valueOf(score), 80, new Font(Font.MONOSPACED, Font.BOLD, 56), Color.WHITE);
            drawStatus(g);
            if (boss != null) {
                drawBossBar(g);
            }
            if (bannerTimer > 0 && (bannerTimer / 10) % 2 == 0) {
                drawCentered(g, "WARNING!", 270, big, new Color(255, 70, 60));
                drawCentered(g, "THE STORM KING APPROACHES", 305, small, new Color(255, 230, 120));
            }
            if (state == State.PAUSED) {
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRect(0, 0, WIDTH, HEIGHT);
                drawCentered(g, "PAUSED", 300, big, Color.WHITE);
                drawCentered(g, "P / ESC - resume", 350, small, Color.WHITE);
                drawCentered(g, "Q - quit to title", 380, small, new Color(255, 140, 110));
            }
        } else {
            drawCentered(g, "GAME OVER", 240, big, new Color(255, 80, 70));
            drawCentered(g, "Score: " + score, 295, mid, Color.WHITE);
            drawCentered(g, "Best: " + highScore, 332, mid, Color.WHITE);
            if (newBest && (System.currentTimeMillis() / 400) % 2 == 0) {
                drawCentered(g, "NEW BEST!", 362, new Font(Font.MONOSPACED, Font.BOLD, 18), new Color(255, 230, 120));
            }
            if (gameOverTimer > 30) {
                drawCentered(g, "SPACE to restart", 400, new Font(Font.MONOSPACED, Font.BOLD, 20), Color.WHITE);
                drawCentered(g, "ESC for title screen", 428, new Font(Font.MONOSPACED, Font.BOLD, 14), new Color(200, 200, 220));
            }
        }

        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        if (SoundManager.isMuted()) {
            drawShadowText(g, "MUTED [M]", WIDTH - 90, HEIGHT - 10, new Color(255, 200, 120));
        } else if (!SoundManager.isMusicOn()) {
            drawShadowText(g, "MUSIC OFF [N]", WIDTH - 114, HEIGHT - 10, new Color(255, 200, 120));
        }

        if (volumeShowTimer > 0) {
            drawVolume(g);
        }
    }

    /** Hides the normal mouse arrow over the game, because we draw our own crosshair. */
    private void hideSystemCursor() {
        try {
            BufferedImage blank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            setCursor(Toolkit.getDefaultToolkit().createCustomCursor(blank, new Point(0, 0), "crosshair"));
        } catch (RuntimeException e) {
            // Headless or unsupported: just keep the normal cursor
        }
    }

    /** A pixel crosshair at the mouse. It turns red and gets a center dot when the gun is ready. */
    private void drawCrosshair(Graphics2D g) {
        if (!mouseInside) {
            return;
        }
        boolean armed = plane.hasGun() && state == State.PLAYING;
        Color c = armed ? new Color(255, 90, 70) : new Color(235, 235, 245);
        int cx = mouseX;
        int cy = mouseY;

        g.setColor(Color.BLACK);                       // outline
        g.fillRect(cx - 11, cy - 2, 9, 4);
        g.fillRect(cx + 2, cy - 2, 9, 4);
        g.fillRect(cx - 2, cy - 11, 4, 9);
        g.fillRect(cx - 2, cy + 2, 4, 9);
        g.setColor(c);
        g.fillRect(cx - 10, cy - 1, 7, 2);
        g.fillRect(cx + 3, cy - 1, 7, 2);
        g.fillRect(cx - 1, cy - 10, 2, 7);
        g.fillRect(cx - 1, cy + 3, 2, 7);
        if (armed) {
            g.setColor(Color.BLACK);
            g.fillRect(cx - 2, cy - 2, 4, 4);
            g.setColor(c);
            g.fillRect(cx - 1, cy - 1, 2, 2);
        }
    }

    private void drawVolume(Graphics2D g) {
        int level = (int) Math.round(SoundManager.getVolume() * 10);
        int blockW = 14;
        int gap = 3;
        int total = 10 * blockW + 9 * gap;
        int bx = (WIDTH - total) / 2;
        int by = HEIGHT - 42;

        drawCentered(g, "VOLUME " + (level * 10) + "%", by - 8, new Font(Font.MONOSPACED, Font.BOLD, 13), Color.WHITE);
        for (int i = 0; i < 10; i++) {
            int x = bx + i * (blockW + gap);
            g.setColor(Color.BLACK);
            g.fillRect(x - 1, by - 1, blockW + 2, 12);
            g.setColor(i < level ? new Color(130, 255, 150) : new Color(60, 64, 80));
            g.fillRect(x, by, blockW, 10);
        }
    }

    private void drawTitle(Graphics2D g) {
        Font body = new Font(Font.MONOSPACED, Font.BOLD, 14);
        Font label = new Font(Font.MONOSPACED, Font.BOLD, 16);

        drawCentered(g, "STORM PILOT", 130, new Font(Font.MONOSPACED, Font.BOLD, 48), Color.WHITE);
        drawCentered(g, "Fly through the city. Survive the storm.", 165, body, new Color(200, 200, 230));

        drawCentered(g, "POWERUPS", 225, label, new Color(255, 230, 120));
        String[] descriptions = {
                "Shield - blocks a hit (stacks to " + Plane.MAX_SHIELDS + ")",
                "Gun - 12 shots, aim with the mouse",
                "Slow-mo - world slows, you don't",
                "Shrink - smaller plane"
        };
        PowerUp.Type[] types = PowerUp.Type.values();
        for (int i = 0; i < types.length; i++) {
            int ty = 245 + i * 34;
            PowerUp.drawBadge(g, types[i], 42, ty - 4, 1);
            g.setFont(body);
            drawShadowText(g, descriptions[i], 84, ty + 16, Color.WHITE);
        }

        drawCentered(g, "HAZARDS", 410, label, new Color(255, 230, 120));
        drawCentered(g, "Meteors   Storm clouds   Tornadoes", 436, body, Color.WHITE);
        drawCentered(g, "A boss appears every " + BOSS_EVERY + " points!", 462, body, new Color(255, 100, 90));

        drawCentered(g, "SPACE flap    MOUSE aim + click", 515, body, Color.WHITE);
        drawCentered(g, "P pause    M mute    N music", 537, body, Color.WHITE);
        drawCentered(g, "+ / -  volume", 559, body, Color.WHITE);

        if (highScore > 0) {
            drawCentered(g, "Best: " + highScore, 592, label, new Color(255, 230, 120));
        }
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            drawCentered(g, "PRESS SPACE TO PLAY", 640, new Font(Font.MONOSPACED, Font.BOLD, 22), Color.WHITE);
        }
    }

    private void drawBossBar(Graphics2D g) {
        int barW = 300;
        int barH = 14;
        int bx = (WIDTH - barW) / 2;
        int by = 98;
        double fraction = Math.max(0, boss.getHealth()) / (double) boss.getMaxHealth();

        g.setColor(Color.BLACK);
        g.fillRect(bx - 3, by - 3, barW + 6, barH + 6);
        g.setColor(new Color(90, 20, 20));
        g.fillRect(bx, by, barW, barH);
        g.setColor(new Color(230, 50, 45));
        g.fillRect(bx, by, (int) (barW * fraction), barH);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        drawShadowText(g, "STORM KING", bx, by + barH + 16, new Color(255, 230, 120));
    }

    private void drawStatus(Graphics2D g) {
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        int y = 24;
        if (plane.isShielded()) {
            drawShadowText(g, plane.getShieldLayers() > 1 ? "SHIELD x" + plane.getShieldLayers() : "SHIELD",
                    12, y, new Color(80, 230, 230));
            y += 18;
        }
        if (plane.hasGun()) {
            drawShadowText(g, "GUN " + plane.getAmmo() + " [CLICK]", 12, y, new Color(255, 120, 100));
            y += 18;
        }
        if (slowTimer > 0) {
            drawShadowText(g, String.format("SLOW-MO %.1fs", slowTimer / 60.0), 12, y, new Color(200, 140, 255));
            y += 18;
        }
        if (plane.getShrinkSeconds() > 0) {
            drawShadowText(g, String.format("SHRINK %.1fs", plane.getShrinkSeconds()), 12, y, new Color(130, 235, 110));
        }
    }

    private void drawShadowText(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(Color.BLACK);
        g.drawString(text, x + 1, y + 1);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private void drawCentered(Graphics2D g, String text, int y, Font font, Color color) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int x = (WIDTH - fm.stringWidth(text)) / 2;
        drawShadowText(g, text, x + 1, y + 1, Color.BLACK);
        drawShadowText(g, text, x, y, color);
    }
}