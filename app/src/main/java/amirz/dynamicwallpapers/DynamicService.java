package amirz.dynamicwallpapers;

import android.app.KeyguardManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;

public class DynamicService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new WPEngine(getApplicationContext());
    }

    class WPEngine extends WallpaperService.Engine implements Runnable {
        private final Context mContext;
        private final KeyguardManager mKm;
        private BroadcastReceiver mScreenStateReceiver;

        private final Handler mHandler = new Handler();
        private final Bitmap mSrcBitmap;
        private final int mSrcWidth;
        private final int mSrcHeight;

        /**
         * Cache that changes on rotation
         */
        private final Canvas mScaledCanvas = new Canvas();
        private Bitmap mScaledBitmap;
        private Allocation mScaledAlloc;

        /**
         * Cache that changes every minute
         */
        private int mLastSecond;
        private Allocation mMinuteAlloc;

        /**
         * Cache that changes every render
         */
        private Bitmap mEffectBitmap;
        private Allocation mEffectAlloc;

        private RenderScript mRs;
        private ScriptC_main mRsMain;
        private SurfaceHolder mSurfaceHolder;
        private boolean mVisible;
        private StateTransitions mTransitions;

        WPEngine(Context context) {
            super();
            mContext = context;
            mKm = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

            WallpaperManager wm = WallpaperManager.getInstance(context);
            if (wm.getWallpaperInfo() == null) {
                Drawable drawable = wm.getDrawable();
                mSrcBitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);

                Canvas canvas = new Canvas(mSrcBitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);

                saveBitmap(mSrcBitmap);
            } else {
                Bitmap bitmap = loadBitmap();
                mSrcBitmap = bitmap == null ? BitmapFactory.decodeResource(getResources(), R.drawable.default_preview) : bitmap;
            }

            mSrcWidth = mSrcBitmap.getWidth();
            mSrcHeight = mSrcBitmap.getHeight();
            mTransitions = new StateTransitions(mContext, this);
        }

        private Bitmap loadBitmap() {
            File f = getFile();
            try {
                InputStream is = new FileInputStream(f);
                Bitmap bm = BitmapFactory.decodeStream(is);
                is.close();
                return bm;
            } catch (Exception ignored) {
            }
            return null;
        }

        private void saveBitmap(Bitmap bitmap) {
            File f = getFile();
            FileOutputStream os;
            try {
                os = new FileOutputStream(f);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.close();
            } catch (Exception ignored) {
            }
        }

        private File getFile() {
            ContextWrapper cw = new ContextWrapper(mContext);
            File cir = cw.getDir("cache", Context.MODE_PRIVATE);
            cir.mkdir();
            return new File(cir, "bg.png");
        }

        /*
        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
            run();
        }
        */

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);

            IntentFilter lockFilter = new IntentFilter();
            lockFilter.addAction(Intent.ACTION_SCREEN_ON);
            lockFilter.addAction(Intent.ACTION_SCREEN_OFF);
            lockFilter.addAction(Intent.ACTION_USER_PRESENT);
            mScreenStateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    reloadLockState();
                }
            };
            mContext.registerReceiver(mScreenStateReceiver, lockFilter);

            mSurfaceHolder = surfaceHolder;
            mRs = RenderScript.create(mContext);
            mRsMain = new ScriptC_main(mRs);

            IntentFilter timeFilter = new IntentFilter();
            timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
            timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            timeFilter.addAction(Intent.ACTION_DATE_CHANGED);
            mContext.registerReceiver(mTransitions, timeFilter);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            mVisible = visible;
            reloadLockState();
        }

        private void reloadLockState() {
            mTransitions.setUnlocked(!mKm.inKeyguardRestrictedInputMode());
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            releaseBitmaps();

            mScaledBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mEffectBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            scaleSource(width, height);

            mScaledAlloc = Allocation.createFromBitmap(mRs, mScaledBitmap);
            mMinuteAlloc = Allocation.createFromBitmap(mRs, mScaledBitmap);
            mEffectAlloc = Allocation.createFromBitmap(mRs, mScaledBitmap);

            reloadLockState();
        }

        private void scaleSource(int destWidth, int destHeight) {
            float cutHorizontal = 0;
            float cutVertical = 0;

            float srcWideness = (float)mSrcWidth / mSrcHeight;
            float destWideness = (float)destWidth / destHeight;
            if (srcWideness > destWideness) {
                cutHorizontal = ((float)mSrcWidth - (float)mSrcHeight * destWideness) / 2;
            } else if (srcWideness < destWideness) {
                cutVertical = ((float)mSrcHeight - (float)mSrcWidth / destWideness) / 2;
            }

            mScaledCanvas.setBitmap(mScaledBitmap);
            mScaledCanvas.drawBitmap(mSrcBitmap,
                    new Rect((int)cutHorizontal,
                            (int)cutVertical,
                            (int)(mSrcWidth - cutHorizontal),
                            (int)(mSrcHeight - cutVertical)),
                    new Rect(0, 0, destWidth, destHeight),null);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();

            mContext.unregisterReceiver(mTransitions);
            mContext.unregisterReceiver(mScreenStateReceiver);

            releaseBitmaps();

            mRsMain.destroy();
            mRs.destroy();

            mSrcBitmap.recycle();
        }

        private void releaseBitmaps() {
            mLastSecond = 0;
            if (mScaledBitmap != null) {
                mEffectAlloc.destroy();
                mMinuteAlloc.destroy();
                mScaledAlloc.destroy();

                mEffectBitmap.recycle();
                mScaledBitmap.recycle();
            }
        }

        @Override
        public void run() {
            if (mVisible && mScaledBitmap != null) {
                int delayToNext = mTransitions.delayToNext();
                int blurRadius = mTransitions.getBlur();

                int second = currentSecond();
                if (second != mLastSecond && !mTransitions.inTransition()) {
                    mLastSecond = second;
                    float progress = (float)second / 24 / 3600;

                    mRsMain.invoke_setContrast(mTransitions.getContrast(progress));
                    mRsMain.set_saturationIncrease(mTransitions.getSaturation(progress));
                    mRsMain.forEach_transform(mScaledAlloc, mMinuteAlloc);
                }

                if (blurRadius > 0) {
                    ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(mRs, mMinuteAlloc.getElement());
                    blur.setRadius(blurRadius);
                    blur.setInput(mMinuteAlloc);
                    blur.forEach(mEffectAlloc);
                    mEffectAlloc.copyTo(mEffectBitmap);
                } else {
                    mMinuteAlloc.copyTo(mEffectBitmap);
                }

                Canvas canvas = mSurfaceHolder.lockCanvas();
                canvas.drawBitmap(mEffectBitmap, 0, 0,null);
                mSurfaceHolder.unlockCanvasAndPost(canvas);

                mHandler.removeCallbacks(this);
                mHandler.postDelayed(this, delayToNext);
            }
        }

        private int currentSecond() {
            Calendar calendar = Calendar.getInstance();
            return calendar.get(Calendar.SECOND) +
                    60 * calendar.get(Calendar.MINUTE) +
                    3600 * calendar.get(Calendar.HOUR_OF_DAY);
        }
    }
}
