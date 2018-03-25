package amirz.dynamicwallpapers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Keeps track of timing after locking/unlocking, and gets the variables for effects
 */
public class StateTransitions extends BroadcastReceiver {
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
        super();
        mContext = context;
        mUpdate = update;
    }

    float getSaturation(float progress) {
        //Keep progress at 50% between 8AM and 4PM
        progress = Curves.extend(progress, 1f / 3, 2f / 3);

        //Between 0.4f (night) and 1.2f (day)
        return 0.8f - 0.4f * Curves.cos(progress);
    }

    float getContrast(float progress) {
        //Keep progress at 50% between 4AM and 8PM
        progress = Curves.extend(progress, 1f / 6, 5f / 6);

        //Between 1.0f (day) and 1.4f (night)
        return 1.2f + 0.2f * Curves.cos(progress);
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

    @Override
    public void onReceive(Context context, Intent intent) {
        mLastChange = System.currentTimeMillis();
        mUpdate.run();
    }
}
