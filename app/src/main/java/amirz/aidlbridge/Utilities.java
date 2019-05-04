package amirz.aidlbridge;

public class Utilities {

    public static int SINGLE_FRAME_MS = 16;

    public static float mapRange(float value, float min, float max) {
        return min + (value * (max - min));
    }

    /**
     * Ensures that a value is within given bounds. Specifically:
     * If value is less than lowerBound, return lowerBound; else if value is greater than upperBound,
     * return upperBound; else return value unchanged.
     */
    public static int boundToRange(int value, int lowerBound, int upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }

    /**
     * @see #boundToRange(int, int, int).
     */
    public static float boundToRange(float value, float lowerBound, float upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }

    /**
     * @see #boundToRange(int, int, int).
     */
    public static long boundToRange(long value, long lowerBound, long upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }
}
