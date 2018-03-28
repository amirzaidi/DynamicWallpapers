package amirz.dynamicwallpapers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Keeps track of timing after locking/unlocking, and gets the variables for effects
 */
public class StateTransitions extends BroadcastReceiver {
    public final static int MAX_CURVE_RENDER_DECAY = 120;

    public final static int FAST_UPDATE_MS = 4;
    public final static int FAST_UPDATE_FPS = 1000 / FAST_UPDATE_MS;
    private final static int SCHEDULED_UPDATE_MS = 60 * 1000;
    private final static int UNLOCK_BLUR_MS = 500;
    public final static int MAX_BLUR = 25;
    private final static int SCROLL_MS = 250;

    private final Context mContext;
    private final Runnable mUpdate;

    private boolean mLocked;
    private long mLastUnlock;
    private long mLastScroll;

    StateTransitions(Context context, Runnable update, boolean locked) {
        super();
        mContext = context;
        mUpdate = update;
        mLocked = locked;
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
         return inTransition() ?
                 FAST_UPDATE_MS :
                 SCHEDULED_UPDATE_MS;
    }

    /**
     * @return an integer in the range of 0 to MAX_BLUR
     */
    int getBlur(float bias) {
        if (mLocked) {
            return MAX_BLUR;
        }
        float lockFactor = (float) unlockedMs() / UNLOCK_BLUR_MS;
        float unlockFactor = 1 - Curves.clamp(lockFactor);
        return (int) (MAX_BLUR * Curves.clamp(bias + Curves.halfCosPosWeak(unlockFactor)));
    }

    boolean inTransition() {
        return (!mLocked && unlockedMs() <= UNLOCK_BLUR_MS) ||
                mLastScroll > System.currentTimeMillis() - SCROLL_MS;
    }

    void setLocked(boolean locked) {
        if (mLocked && !locked) {
            mLastUnlock = System.currentTimeMillis();
        }
        mLocked = locked;
    }

    void scroll() {
        mLastScroll = System.currentTimeMillis();
    }

    private long unlockedMs() {
        return System.currentTimeMillis() - mLastUnlock;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mLastUnlock = System.currentTimeMillis() - UNLOCK_BLUR_MS;
        mUpdate.run();
    }
}
