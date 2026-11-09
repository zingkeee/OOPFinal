import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Remembers the high score and sound settings between runs, in a small file in the
 * user's home folder. If the file can't be read or written, the game just carries on.
 */
public final class SaveData {
    private static final File FILE = new File(System.getProperty("user.home"), ".storm_pilot.properties");
    private static final Properties PROPS = new Properties();

    static {
        try (FileInputStream in = new FileInputStream(FILE)) {
            PROPS.load(in);
        } catch (IOException e) {
            // First run: no save file yet
        }
    }

    private SaveData() {}

    public static int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(PROPS.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static double getDouble(String key, double fallback) {
        try {
            return Double.parseDouble(PROPS.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean getBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(PROPS.getProperty(key, String.valueOf(fallback)));
    }

    public static void set(String key, String value) {
        PROPS.setProperty(key, value);
        try (FileOutputStream out = new FileOutputStream(FILE)) {
            PROPS.store(out, "Storm Pilot save data");
        } catch (IOException e) {
            System.err.println("[SaveData] Could not save: " + e);
        }
    }
}
