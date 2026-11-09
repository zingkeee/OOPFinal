/** A short-lived visual effect (explosion, shockwave, floating text) that removes itself when finished. */
public abstract class Effect extends GameObject {
    protected Effect(double x, double y) {
        super(x, y, 0, 0);
    }

    public abstract boolean isFinished();
}
