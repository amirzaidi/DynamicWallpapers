package amirz.dynamicwallpapers;

public class Curves {
    private final static double PI = Math.PI;
    private final static double TAU = 2 * PI;

    static float sin(float progress) {
        return (float) Math.sin(progress * TAU);
    }

    static float cos(float progress) {
        return (float) Math.cos(progress * TAU);
    }

    static float halfCos(float progress) {
        return (float) Math.cos(progress * PI);
    }

    static float halfCosPos(float progress) {
        return 0.5f * (1f - halfCos(progress));
    }

    static float halfCosPosWeak(float progress) {
        return 0.5f * (progress + halfCosPos(progress));
    }

    /**
     * @param num [0, 1]
     * @param start <0, 1], smaller than end
     * @param end [0, 1>, larger than start
     */
    static float extend(float num, float start, float end) {
        float mid = (start + end) / 2;
        if (num < start) {
            return mid * (num / start);
        }
        if (num > end) {
            return 1 + (mid - 1) * (1 - num) / (1 - end);
        }
        return mid;
    }

    static float clamp(float num) {
        return clamp(num, 0, 1);
    }

    static float clamp(float num, float min, float max) {
        return Math.max(min, Math.min(max, num));
    }
}
