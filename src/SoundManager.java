import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Plays sound effects and background music. There are no audio files: everything is
 * generated in code (square waves, triangle waves, noise, simple drums).
 *
 * One background thread keeps a single audio line open and continuously feeds it a
 * mix of the music loop plus whatever sound effects are playing (silence when nothing
 * is). Keeping the line open means the sound device never has to "wake up" for a
 * short sound. If the computer has no usable audio device, the game stays silent
 * instead of crashing.
 */
public final class SoundManager {
    public enum Sfx {
        FLAP, SHOOT, ENEMY_SHOT, POWERUP, SCORE, EXPLOSION, BIG_EXPLOSION,
        HIT, CRASH, ZAP, THUNDER, WARNING, SIREN, SHIELD_UP, SHIELD_BREAK, BOMB_THROW
    }

    /** Which music loop should be playing. */
    public enum Track { NONE, STORM, BOSS }

    private enum Wave { SQUARE, SINE, NOISE }

    private static final float RATE = 44100f;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 2, true, false); // stereo
    private static final int CHUNK_FRAMES = 882;     // about 20 ms of audio per mixing step
    private static final int MAX_VOICES = 16;        // most sounds that can overlap
    private static final double TONE_GAIN = 2.5;     // makes the beeps as loud as the explosions
    private static final double MUSIC_VOLUME = 0.30; // music level compared to the effects

    private static final Map<Sfx, short[]> SOUNDS = new EnumMap<>(Sfx.class);
    private static final Random RNG = new Random();
    private static final List<Voice> VOICES = new ArrayList<>();

    private static volatile boolean muted = false;
    private static volatile boolean musicOn = true;
    private static volatile double masterVolume = 1.0;   // 0.0 to 1.0
    private static volatile boolean available = true;
    private static volatile Track wantedTrack = Track.NONE;
    private static boolean started = false;
    private static boolean loggedFirstPlay = false;

    // ----- music data (must be declared before the static block that uses it) -----
    private static final int NO_NOTE = -99;
    private static final int[] ARP = {0, 1, 2, 3, 2, 1, 3, 2};         // which chord tone each 16th note plays
    private static final int[] MIN = {0, 3, 7, 12};                     // minor chord tones
    private static final int[] MAJ = {0, 4, 7, 12};                     // major chord tones

    private static final short[] STORM_LOOP;
    private static final short[] BOSS_LOOP;

    /** One sound effect that is currently playing (or waiting for its delay to pass). */
    private static final class Voice {
        final short[] data;
        int pos = 0;
        int delay;

        Voice(short[] data, int delay) {
            this.data = data;
            this.delay = delay;
        }
    }

    static {
        SOUNDS.put(Sfx.FLAP, toPcm(tone(420, 760, 70, Wave.SQUARE, 0.14)));
        SOUNDS.put(Sfx.SHOOT, toPcm(tone(1000, 250, 100, Wave.SQUARE, 0.2)));
        SOUNDS.put(Sfx.ENEMY_SHOT, toPcm(tone(300, 700, 140, Wave.SQUARE, 0.15)));
        SOUNDS.put(Sfx.POWERUP, toPcm(concat(
                tone(523, 523, 60, Wave.SQUARE, 0.2),
                tone(659, 659, 60, Wave.SQUARE, 0.2),
                tone(880, 880, 110, Wave.SQUARE, 0.2))));
        SOUNDS.put(Sfx.SCORE, toPcm(tone(900, 1300, 70, Wave.SINE, 0.22)));
        SOUNDS.put(Sfx.EXPLOSION, toPcm(rumble(350, 0.7, 0.6, 1.6)));
        SOUNDS.put(Sfx.BIG_EXPLOSION, toPcm(rumble(1100, 0.9, 0.85, 3.0)));
        SOUNDS.put(Sfx.HIT, toPcm(mix(
                tone(600, 150, 180, Wave.SQUARE, 0.25),
                rumble(180, 0.5, 0.5, 1.5))));
        SOUNDS.put(Sfx.CRASH, toPcm(mix(
                tone(350, 60, 550, Wave.SQUARE, 0.25),
                rumble(550, 0.7, 0.8, 2.5))));
        SOUNDS.put(Sfx.ZAP, toPcm(mix(
                tone(1800, 200, 220, Wave.SQUARE, 0.15),
                rumble(220, 0.5, 0.3, 1.0))));
        SOUNDS.put(Sfx.THUNDER, toPcm(rumble(1400, 0.8, 0.96, 6.0)));
        SOUNDS.put(Sfx.WARNING, toPcm(concat(
                tone(440, 440, 150, Wave.SQUARE, 0.2), tone(330, 330, 150, Wave.SQUARE, 0.2),
                tone(440, 440, 150, Wave.SQUARE, 0.2), tone(330, 330, 150, Wave.SQUARE, 0.2),
                tone(440, 440, 150, Wave.SQUARE, 0.2), tone(330, 330, 150, Wave.SQUARE, 0.2))));
        SOUNDS.put(Sfx.SIREN, toPcm(concat(
                tone(700, 950, 200, Wave.SQUARE, 0.06), tone(950, 700, 200, Wave.SQUARE, 0.06),
                tone(700, 950, 200, Wave.SQUARE, 0.06), tone(950, 700, 200, Wave.SQUARE, 0.06))));

        SOUNDS.put(Sfx.SHIELD_UP, toPcm(concat(
                tone(500, 500, 50, Wave.SINE, 0.2), tone(750, 750, 50, Wave.SINE, 0.2),
                tone(1000, 1000, 50, Wave.SINE, 0.2), tone(1500, 1800, 160, Wave.SINE, 0.2))));
        SOUNDS.put(Sfx.SHIELD_BREAK, toPcm(mix(
                tone(2400, 500, 200, Wave.SQUARE, 0.12),
                rumble(220, 0.6, 0.2, 1.2))));

        SOUNDS.put(Sfx.BOMB_THROW, toPcm(mix(
                tone(280, 110, 150, Wave.SQUARE, 0.12),
                rumble(120, 0.4, 0.6, 1.2))));

        // Calm, moody storm theme: A minor, 8 bars, ~20 seconds before it loops
        STORM_LOOP = buildTrack(96,
                new int[] {57, 57, 53, 53, 60, 60, 55, 52},
                new int[][] {MIN, MIN, MAJ, MAJ, MAJ, MAJ, MAJ, MAJ},
                new int[][] {
                        {NO_NOTE, NO_NOTE, NO_NOTE, NO_NOTE}, {12, NO_NOTE, 15, NO_NOTE},
                        {12, NO_NOTE, 16, NO_NOTE}, {19, 16, 12, NO_NOTE},
                        {12, 16, 19, 16}, {12, 19, 16, 12},
                        {12, 16, 19, 16}, {12, 16, 19, 23}},
                false);

        // Faster, more aggressive boss theme
        BOSS_LOOP = buildTrack(148,
                new int[] {57, 57, 53, 55, 57, 57, 53, 52},
                new int[][] {MIN, MIN, MAJ, MAJ, MIN, MIN, MAJ, MAJ},
                new int[][] {
                        {12, 15, 19, 15}, {12, 15, 19, 22}, {12, 16, 19, 16}, {14, 19, 16, 14},
                        {12, 15, 19, 15}, {12, 15, 19, 22}, {12, 16, 19, 24}, {11, 16, 19, 23}},
                true);
    }

    private SoundManager() {}

    // ----- public API -----

    /** Call once at startup: opens the audio line and starts the mixer thread. */
    public static synchronized void init() {
        if (started) {
            return;
        }
        started = true;

        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        System.out.println("[SoundManager] Audio mixers found: " + mixers.length);
        for (Mixer.Info m : mixers) {
            System.out.println("[SoundManager]   - " + m.getName());
        }

        Thread t = new Thread(SoundManager::mixerLoop, "audio-mixer");
        t.setDaemon(true);
        t.start();
    }

    public static void play(Sfx sfx) {
        playDelayed(sfx, 0);
    }

    public static void playDelayed(Sfx sfx, long delayMs) {
        if (!loggedFirstPlay) {
            loggedFirstPlay = true;
            System.out.println("[SoundManager] First sound requested: " + sfx + (muted ? " (but sound is MUTED)" : ""));
        }
        if (muted || !available) {
            return;
        }
        short[] data = SOUNDS.get(sfx);
        int delayFrames = (int) (delayMs * RATE / 1000);
        synchronized (VOICES) {
            if (VOICES.size() < MAX_VOICES) {
                VOICES.add(new Voice(data, delayFrames));
            }
        }
    }

    /** Chooses which music loop plays (NONE = silence). It fades between tracks smoothly. */
    public static void setMusic(Track track) {
        wantedTrack = track;
    }

    public static void toggleMute() { muted = !muted; }
    public static boolean isMuted() { return muted; }

    public static void toggleMusic() { musicOn = !musicOn; }
    public static boolean isMusicOn() { return musicOn; }
    public static void setMusicOn(boolean on) { musicOn = on; }

    public static double getVolume() { return masterVolume; }

    public static void setVolume(double volume) {
        masterVolume = Math.max(0, Math.min(1, volume));
    }

    public static void changeVolume(double delta) {
        setVolume(Math.round((masterVolume + delta) * 10) / 10.0);   // keeps it on clean 10% steps
    }

    // ----- mixing -----

    /**
     * Plays the music loops. It remembers where each loop was, so pausing and resuming
     * continues the song, and it fades out and in when the track changes.
     */
    private static final class MusicPlayer {
        private Track playing = Track.NONE;
        private Track lastActive = Track.NONE;
        private double gain = 0;
        private int stormPos = 0;
        private int bossPos = 0;

        void mixInto(int[] acc, int frames, Track want) {
            for (int f = 0; f < frames; f++) {
                double target = (want == playing && playing != Track.NONE) ? 1.0 : 0.0;
                gain += (target - gain) * 0.0008;

                // Once the old track has faded out, switch to the new one
                if (want != playing && gain < 0.01) {
                    playing = want;
                    gain = 0;
                    if (playing == Track.BOSS && lastActive != Track.BOSS) {
                        bossPos = 0;   // every boss fight starts the theme from the top
                    }
                    if (playing != Track.NONE) {
                        lastActive = playing;
                    }
                }
                if (playing == Track.NONE) {
                    continue;
                }

                short[] loop = playing == Track.STORM ? STORM_LOOP : BOSS_LOOP;
                int pos = playing == Track.STORM ? stormPos : bossPos;
                acc[f] += (int) (loop[pos] * MUSIC_VOLUME * gain);
                pos++;
                if (pos >= loop.length) {
                    pos = 0;
                }
                if (playing == Track.STORM) {
                    stormPos = pos;
                } else {
                    bossPos = pos;
                }
            }
        }
    }

    /** Runs on its own thread: mixes music and sound effects and writes them to the speakers. */
    private static void mixerLoop() {
        try {
            SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT);
            line.open(FORMAT, CHUNK_FRAMES * 4 * 4);
            line.start();
            System.out.println("[SoundManager] Audio line opened: " + line.getFormat()
                    + ", buffer " + line.getBufferSize() + " bytes");

            MusicPlayer music = new MusicPlayer();
            byte[] buf = new byte[CHUNK_FRAMES * 4];
            int[] acc = new int[CHUNK_FRAMES];

            while (true) {
                java.util.Arrays.fill(acc, 0);

                music.mixInto(acc, CHUNK_FRAMES, (muted || !musicOn) ? Track.NONE : wantedTrack);

                synchronized (VOICES) {
                    Iterator<Voice> it = VOICES.iterator();
                    while (it.hasNext()) {
                        Voice v = it.next();
                        for (int f = 0; f < CHUNK_FRAMES; f++) {
                            if (v.delay > 0) {
                                v.delay--;
                                continue;
                            }
                            if (v.pos >= v.data.length) {
                                break;
                            }
                            acc[f] += v.data[v.pos++];
                        }
                        if (v.pos >= v.data.length) {
                            it.remove();
                        }
                    }
                }

                // Master volume (squared, so the steps sound even to the ear)
                double vol = masterVolume * masterVolume;

                // Stereo, 16-bit little-endian: same sample in left and right
                for (int f = 0; f < CHUNK_FRAMES; f++) {
                    int sample = Math.max(-32768, Math.min(32767, (int) (acc[f] * vol)));
                    byte lo = (byte) sample;
                    byte hi = (byte) (sample >> 8);
                    buf[4 * f] = lo;
                    buf[4 * f + 1] = hi;
                    buf[4 * f + 2] = lo;
                    buf[4 * f + 3] = hi;
                }
                line.write(buf, 0, buf.length);   // blocks until the speakers need more
            }
        } catch (Exception e) {
            available = false;
            System.err.println("[SoundManager] Audio failed, sound disabled: " + e);
        }
    }

    // ----- sound effect synthesis -----

    /** A tone that sweeps from f0 to f1 Hz and fades out. */
    private static double[] tone(double f0, double f1, int ms, Wave wave, double vol) {
        int n = (int) (RATE * ms / 1000.0);
        double[] out = new double[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double p = i / (double) n;
            phase += 2 * Math.PI * (f0 + (f1 - f0) * p) / RATE;
            double s;
            switch (wave) {
                case SQUARE: s = Math.sin(phase) >= 0 ? 1 : -1; break;
                case SINE:   s = Math.sin(phase); break;
                default:     s = RNG.nextDouble() * 2 - 1;
            }
            double attack = Math.min(1.0, i / 50.0);   // tiny fade-in avoids clicks
            out[i] = s * vol * TONE_GAIN * (1 - p) * attack;
        }
        return out;
    }

    /** Low-passed noise: explosions and thunder. Higher smooth = deeper rumble. */
    private static double[] rumble(int ms, double vol, double smooth, double gain) {
        int n = (int) (RATE * ms / 1000.0);
        double[] out = new double[n];
        double y = 0;
        for (int i = 0; i < n; i++) {
            double p = i / (double) n;
            y = y * smooth + (RNG.nextDouble() * 2 - 1) * (1 - smooth);
            double decay = (1 - p) * (1 - p);
            out[i] = y * gain * vol * decay * Math.min(1.0, i / 50.0);
        }
        return out;
    }

    private static double[] concat(double[]... parts) {
        int total = 0;
        for (double[] p : parts) {
            total += p.length;
        }
        double[] out = new double[total];
        int pos = 0;
        for (double[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private static double[] mix(double[] a, double[] b) {
        double[] out = new double[Math.max(a.length, b.length)];
        for (int i = 0; i < out.length; i++) {
            out[i] = (i < a.length ? a[i] : 0) + (i < b.length ? b[i] : 0);
        }
        return out;
    }

    private static short[] toPcm(double[] samples) {
        short[] data = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            data[i] = (short) (Math.max(-1, Math.min(1, samples[i])) * 32767 * 0.8);
        }
        return data;
    }

    // ----- music synthesis -----

    private static double midiFreq(int note) {
        return 440.0 * Math.pow(2, (note - 69) / 12.0);
    }

    /**
     * Adds one note into the loop buffer. kind 0 = pulse wave (duty = pulse width),
     * kind 1 = triangle wave. Notes that run past the end wrap around to the start,
     * so the loop is seamless.
     */
    private static void addNote(double[] buf, int start, int len, double freq,
                                int kind, double duty, double vol, double decay) {
        double phase = 0;
        double inc = freq / RATE;
        for (int i = 0; i < len; i++) {
            phase += inc;
            if (phase >= 1) {
                phase -= 1;
            }
            double s = kind == 0 ? (phase < duty ? 1 : -1) : 4 * Math.abs(phase - 0.5) - 1;
            double t = i / (double) RATE;
            double env = Math.exp(-decay * t) * Math.min(1.0, i / 100.0) * Math.min(1.0, (len - i) / 200.0);
            buf[(start + i) % buf.length] += s * vol * env;
        }
    }

    private static void addKick(double[] buf, int start, double vol) {
        int len = (int) (RATE * 0.14);
        double phase = 0;
        for (int i = 0; i < len; i++) {
            double t = i / (double) RATE;
            phase += 2 * Math.PI * (45 + 110 * Math.exp(-t * 30)) / RATE;   // pitch drops quickly
            buf[(start + i) % buf.length] += Math.sin(phase) * vol * Math.exp(-t * 20);
        }
    }

    private static void addSnare(double[] buf, int start, double vol) {
        int len = (int) (RATE * 0.12);
        for (int i = 0; i < len; i++) {
            double t = i / (double) RATE;
            double noise = (RNG.nextDouble() * 2 - 1) * Math.exp(-t * 28);
            double body = Math.sin(2 * Math.PI * 190 * t) * Math.exp(-t * 35) * 0.5;
            buf[(start + i) % buf.length] += (noise + body) * vol;
        }
    }

    private static void addHat(double[] buf, int start, double vol) {
        int len = (int) (RATE * 0.04);
        double prev = 0;
        for (int i = 0; i < len; i++) {
            double t = i / (double) RATE;
            double noise = RNG.nextDouble() * 2 - 1;
            buf[(start + i) % buf.length] += (noise - prev) * vol * Math.exp(-t * 110);   // high-passed noise
            prev = noise;
        }
    }

    /**
     * Builds a looping chiptune: bass, arpeggio, melody and drums.
     * @param roots  the root note (MIDI number) of each bar
     * @param chords the chord tones of each bar
     * @param melody four melody notes per bar (offset from the root, or NO_NOTE)
     * @param intense denser bass, hats and kicks for the boss theme
     */
    private static short[] buildTrack(int bpm, int[] roots, int[][] chords, int[][] melody, boolean intense) {
        int stepSamples = (int) (RATE * 60.0 / bpm / 4);     // one 16th note
        int bars = roots.length;
        double[] buf = new double[bars * 16 * stepSamples];

        for (int bar = 0; bar < bars; bar++) {
            int root = roots[bar];
            int[] chord = chords[bar];

            for (int step = 0; step < 16; step++) {
                int t = (bar * 16 + step) * stepSamples;

                // Bass
                if (intense || step % 2 == 0) {
                    boolean octaveUp = intense ? step % 2 == 1 : step % 4 == 2;
                    addNote(buf, t, stepSamples * (intense ? 1 : 2), midiFreq(root - 12 + (octaveUp ? 12 : 0)),
                            0, 0.5, 0.30, 5);
                }

                // Arpeggio (thin pulse wave)
                addNote(buf, t, stepSamples, midiFreq(root + chord[ARP[step % 8]]),
                        0, 0.25, intense ? 0.15 : 0.12, 14);

                // Melody (triangle wave, one note per beat)
                if (step % 4 == 0) {
                    int m = melody[bar][step / 4];
                    if (m != NO_NOTE) {
                        addNote(buf, t, stepSamples * 4, midiFreq(root + m), 1, 0, 0.30, 2.5);
                    }
                }

                // Drums
                if (step % 8 == 0 || (intense && step % 4 == 0)) {
                    addKick(buf, t, 0.55);
                }
                if (step == 4 || step == 12) {
                    addSnare(buf, t, 0.30);
                }
                if (intense || step % 2 == 0) {
                    addHat(buf, t, 0.12);
                }
            }
        }

        // Normalize so both tracks have the same loudness
        double peak = 0.0001;
        for (double v : buf) {
            peak = Math.max(peak, Math.abs(v));
        }
        short[] out = new short[buf.length];
        for (int i = 0; i < buf.length; i++) {
            out[i] = (short) (buf[i] / peak * 0.85 * 32767);
        }
        return out;
    }
}