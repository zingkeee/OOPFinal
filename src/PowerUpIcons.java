import java.awt.image.BufferedImage;

/**
 * Pixel art icons shown on the powerups. Each icon is 12x12 pixels and is drawn at 2x.
 * (Letters are colors, see PixelArt: K black, L light gray, G gray, D dark, N brown,
 * Y yellow, U blue, B light blue, R red, W white, E green, e dark green.)
 */
public final class PowerUpIcons {
    private PowerUpIcons() {}

    /** A pistol, pointing right. */
    public static final BufferedImage GUN = PixelArt.create(new String[] {
        "............",
        "..K.......K.",
        ".KKKKKKKKKK.",
        ".KLLLLLLLLLK",
        ".KGGGGGGGGGK",
        ".KDDDDDDDDDK",
        ".KKKKKKKKKK.",
        ".KNNKDDK....",
        ".KNNKKKK....",
        ".KNNK.......",
        ".KNNK.......",
        ".KKKK......."
    });

    /** A sturdy steel shield with a gold cross. */
    public static final BufferedImage SHIELD = PixelArt.create(new String[] {
        "KKKKKKKKKKKK",
        "KLLLLLLLLLLK",
        "KLUUUYYUUULK",
        "KLUUUYYUUULK",
        "KLYYYYYYYYLK",
        "KLYYYYYYYYLK",
        ".KLUUYYUULK.",
        ".KLUUYYUULK.",
        "..KLUYYULK..",
        "...KLUULK...",
        "....KLLK....",
        ".....KK....."
    });

    /** A glowing green time-orb with clock hands. */
    public static final BufferedImage TIME = PixelArt.create(new String[] {
        "...KKKKKK...",
        "..KYYYYYYK..",
        ".KYEEWEEEYK.",
        "KYEEEWEEEEYK",
        "KYEEEWEEEEYK",
        "KYEEEWWWWEYK",
        "KYEEEEEEEEYK",
        "KYEEEEEEEEYK",
        "KYEeeEEEEeYK",
        ".KYeeeeeeYK.",
        "..KYYYYYYK..",
        "...KKKKKK..."
    });

    /** A red helmet with big round eyes and antennae, like an ant. */
    public static final BufferedImage SHRINK = PixelArt.create(new String[] {
        ".K........K.",
        "..K......K..",
        "...KKKKKK...",
        "..KRRRRRRK..",
        ".KRRRRRRRRK.",
        ".KRBBRRBBRK.",
        ".KRBBRRBBRK.",
        ".KRRRRRRRRK.",
        ".KLRRRRRRLK.",
        "..KLDDDDLK..",
        "...KKLLKK...",
        "....KKKK...."
    });
}
