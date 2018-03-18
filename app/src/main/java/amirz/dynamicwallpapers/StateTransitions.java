package amirz.dynamicwallpapers;

import android.content.Context;

public class StateTransitions {
    private final static int UNLOCK_BLUR_DURATION = 500;
    private final static int MAX_BLUR = 25;

    private final Context mContext;
    private final Runnable mUpdate;

    private boolean mUnlocked;
    private long mLastChange;

    StateTransitions(Context context, Runnable update) {
        mContext = context;
        mUpdate = update;
    }

    float getSaturation() {
        return 2f;
    }

    int getBlur() {
        if (mUnlocked) {
            long absoluteBlur = (System.currentTimeMillis() - mLastChange) * MAX_BLUR;
            return Math.max(0, MAX_BLUR - (int) (absoluteBlur / UNLOCK_BLUR_DURATION));
        }
        return MAX_BLUR;
    }

    boolean inTransition() {
        return getBlur() > 0;
    }

    void setUnlocked(boolean unlocked) {
        if (mUnlocked != unlocked) {
            mLastChange = System.currentTimeMillis();
        }
        mUnlocked = unlocked;
        mUpdate.run();
    }
}
