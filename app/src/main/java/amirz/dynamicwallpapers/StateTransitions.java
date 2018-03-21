package amirz.dynamicwallpapers;

import android.content.Context;

/**
 * Keeps track of timing after locking/unlocking, and gets the variables for effects
 */
public class StateTransitions {
    private final static int FAST_UPDATE_FPS = 250;
    private final static int FAST_UPDATE_MS = 1000 / FAST_UPDATE_FPS;
    private final static int SCHEDULED_UPDATE_MS = 60 * 1000;
    private final static int UNLOCK_BLUR_MS = 500;
    private final static int MAX_BLUR = 25;

    private final Context mContext;
    private final Runnable mUpdate;

    private boolean mUnlocked;
    private long mLastChange;

    StateTransitions(Context context, Runnable update) {
        mContext = context;
        mUpdate = update;
    }

    float getSaturation(int secondOfDay) {
        return 1f;
    }

    float getContrast(int secondOfDay) {
        return 1f;
    }

    int delayToNext() {
         return mUnlocked && msSinceChange() <= UNLOCK_BLUR_MS ?
                 FAST_UPDATE_MS :
                 SCHEDULED_UPDATE_MS;
    }

    /**
     * @return an integer in the range of 0 to MAX_BLUR
     */
    int getBlur() {
        if (!mUnlocked) {
            return MAX_BLUR;
        }
        float lockFactor = (float) msSinceChange() / UNLOCK_BLUR_MS;
        float unlockFactor = 1 - Curves.clamp(lockFactor);
        return (int) (MAX_BLUR * Curves.halfCosPosWeak(unlockFactor));
    }

    void setUnlocked(boolean unlocked) {
        if (mUnlocked != unlocked) {
            mLastChange = System.currentTimeMillis();
        }
        mUnlocked = unlocked;
        mUpdate.run();
    }

    private long msSinceChange() {
        return System.currentTimeMillis() - mLastChange;
    }
}
