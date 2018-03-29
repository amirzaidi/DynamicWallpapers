package amirz.dynamicwallpapers;

import android.app.KeyguardManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.wallpaper.WallpaperService;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;

public class DynamicService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        Context context = getApplicationContext();
        if (Permissions.hasAll(context)) {
            return new WPEngine(context);
        }
        return new WallpaperService.Engine();
    }

    class WPEngine extends WallpaperService.Engine implements Runnable, SharedPreferences.OnSharedPreferenceChangeListener {
        private final Context mContext;
        private final KeyguardManager mKm;
        private BroadcastReceiver mScreenStateReceiver;

        private HandlerThread mBackgroundThread;
        private Handler mHandler;

        private final Bitmap mSrcBitmap;

        /**
         * Cache that changes on rotation and swipes
         */
        private final Canvas mCanvas = new Canvas();
        private Bitmap mScaleBitmap;
        private Allocation mScaleAlloc;

        /**
         * Cache that changes every minute
         */
        private int mLastCurveRender;
        private Bitmap mMinuteBitmap;
        private Allocation mMinuteAlloc;

        /**
         * Cache that changes every render
         */
        private Bitmap mEffectBitmap;
        private Allocation mEffectAlloc;

        private RenderScript mRs;
        private ScriptC_main mRsMain;
        private SurfaceHolder mSurfaceHolder;
        private Rect mDestRect;
        private boolean mVisible;
        private StateTransitions mTransitions;

        private float mScroll;

        private VisualizeFX mVisualizer;

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

            mTransitions = new StateTransitions(mContext, this, mKm.inKeyguardRestrictedInputMode());
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
                AndroidBmpUtil.save(bitmap, os);
                os.close();
            } catch (Exception ignored) {
            }
        }

        private File getFile() {
            ContextWrapper cw = new ContextWrapper(mContext);
            File cir = cw.getDir("cache", Context.MODE_PRIVATE);
            cir.mkdir();
            return new File(cir, "bg.bmp");
        }

        /*
        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
        }
        */

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
            mScroll = xOffset;
            if (Settings.scrollingEnabled) {
                mTransitions.scroll();
                mHandler.post(this);
            }
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            final DynamicService.WPEngine self = this;

            mBackgroundThread = new HandlerThread("background");
            mBackgroundThread.start();
            mHandler = new Handler(mBackgroundThread.getLooper());

            IntentFilter lockFilter = new IntentFilter();
            lockFilter.addAction(Intent.ACTION_SCREEN_ON);
            lockFilter.addAction(Intent.ACTION_SCREEN_OFF);
            lockFilter.addAction(Intent.ACTION_USER_PRESENT);
            mScreenStateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    reloadLockState();
                    mHandler.post(self);
                }
            };
            registerReceiver(mScreenStateReceiver, lockFilter);

            mSurfaceHolder = surfaceHolder;
            mRs = RenderScript.create(mContext);
            mRsMain = new ScriptC_main(mRs);

            IntentFilter timeFilter = new IntentFilter();
            timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
            timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            timeFilter.addAction(Intent.ACTION_DATE_CHANGED);
            mContext.registerReceiver(mTransitions, timeFilter);

            mVisualizer = new VisualizeFX(this);

            Settings.reload(mContext);
            Settings.getPrefs(mContext).registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Settings.reload(mContext);
            reloadVisualizerState();

            mHandler.post(this);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            mVisible = visible;

            reloadLockState();
            reloadVisualizerState();

            mHandler.post(this);
        }

        private void reloadLockState() {
            mTransitions.setLocked(mKm.inKeyguardRestrictedInputMode());
        }

        private void reloadVisualizerState() {
            mVisualizer.setEnabled(mVisible && Settings.visualizerEnabled);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            releaseBitmaps();

            mDestRect = new Rect(0, 0, width, height);

            mScaleBitmap = scaleSource(); //creates mScaleBitmap
            mMinuteBitmap = Bitmap.createBitmap(mScaleBitmap.getWidth(), mScaleBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            mEffectBitmap = Bitmap.createBitmap(mMinuteBitmap.getWidth(), mMinuteBitmap.getHeight(), Bitmap.Config.ARGB_8888);

            mScaleAlloc = Allocation.createFromBitmap(mRs, mScaleBitmap);
            mMinuteAlloc = Allocation.createFromBitmap(mRs, mMinuteBitmap);
            mEffectAlloc = Allocation.createFromBitmap(mRs, mEffectBitmap);
        }

        private Bitmap scaleSource() {
            int srcWidth = mSrcBitmap.getWidth();
            int srcHeight = mSrcBitmap.getHeight();
            float srcRatio = (float)srcWidth / srcHeight;

            int destWidth = mDestRect.right;
            int destHeight = mDestRect.bottom;
            float destRatio = (float)destWidth / destHeight;

            int cutVertical = 0;

            //Aspect ratio equalization
            if (destWidth > destHeight) {
                if (srcRatio < 1 / destRatio) {
                    //Less wide than portrait, cut source top/bottom
                    cutVertical += (int)(srcHeight - srcWidth * destRatio);
                }

                //Cut source top/bottom to match portrait position
                float heightScale = (srcHeight - cutVertical) / destHeight;
                cutVertical += (int)(heightScale * (destWidth - ((float)destHeight * destHeight / destWidth)));

                //Make sure ratio is correct again now
                srcRatio = (float)srcWidth / (srcHeight - cutVertical);
                destWidth = (int)(destHeight * srcRatio);
            } else {
                if (srcRatio > destRatio) {
                    //Scrollable
                    destWidth = (int)(destHeight * srcRatio);
                } else if (srcRatio < destRatio) {
                    //Fixed, cut top/bottom
                    cutVertical = (int)(srcHeight - srcWidth / destRatio);
                }
            }

            Bitmap scaleBitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888);

            mCanvas.setBitmap(scaleBitmap);
            mCanvas.drawBitmap(mSrcBitmap, new Rect(0,
                        cutVertical / 2,
                        srcWidth,
                        srcHeight - cutVertical / 2),
                    new Rect(0,
                        0,
                        destWidth,
                        destHeight), null);

            return scaleBitmap;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            Settings.getPrefs(mContext).unregisterOnSharedPreferenceChangeListener(this);

            mVisualizer.release();

            try {
                mContext.unregisterReceiver(mTransitions);
                mContext.unregisterReceiver(mScreenStateReceiver);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }

            releaseBitmaps();

            mRsMain.destroy();
            mRs.destroy();

            mSrcBitmap.recycle();
            mBackgroundThread.quitSafely();
        }

        private void releaseBitmaps() {
            mLastCurveRender = 0;
            if (mScaleBitmap != null) {
                mEffectAlloc.destroy();
                mMinuteAlloc.destroy();
                mScaleAlloc.destroy();

                mEffectBitmap.recycle();
                mMinuteBitmap.recycle();
                mScaleBitmap.recycle();
            }
        }

        public void requeue(int delayToNext) {
            mHandler.removeCallbacks(this);
            if (delayToNext == 0) {
                mHandler.post(this);
            } else {
                mHandler.postDelayed(this, delayToNext);
            }
        }

        @Override
        public void run() {
            if (mVisible && mScaleBitmap != null) {
                float musicMagnitude = 0f;
                int second = currentSecond();

                if (Settings.colorEnabled && (second > mLastCurveRender + StateTransitions.MAX_CURVE_RENDER_DECAY || second < mLastCurveRender || !mTransitions.inTransition())) {
                    //Only render on the first frame of blurring and scrolling to prevent stutters
                    mLastCurveRender = second;
                    float progress = (float)second / 24 / 3600;

                    mRsMain.invoke_setContrast(mTransitions.getContrast(progress));
                    mRsMain.set_saturationIncrease(mTransitions.getSaturation(progress));
                    mRsMain.forEach_transform(mScaleAlloc, mMinuteAlloc);

                    musicMagnitude = mVisualizer.magnitude * 0.4f;
                }

                int delayToNext = mTransitions.delayToNext();
                int blurRadius = mTransitions.getBlur(musicMagnitude);

                Allocation minuteAlloc = Settings.colorEnabled ? mMinuteAlloc : mScaleAlloc;
                if (blurRadius > 0 && Settings.blurEnabled) {
                    ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(mRs, minuteAlloc.getElement());
                    blur.setRadius(blurRadius);
                    blur.setInput(mMinuteAlloc);
                    blur.forEach(mEffectAlloc);
                    mEffectAlloc.copyTo(mEffectBitmap);
                } else {
                    minuteAlloc.copyTo(mEffectBitmap);
                }

                int leftOffset = Settings.scrollingEnabled ? (int)(mScroll * (mScaleBitmap.getWidth() - mDestRect.right)) : 0;

                int zoomWidth = (int)(mDestRect.right * mVisualizer.magnitude / 96);
                int zoomHeight = (int)(mDestRect.bottom * mVisualizer.magnitude / 96);

                Canvas canvas = mSurfaceHolder.lockCanvas();
                canvas.drawBitmap(mEffectBitmap, new Rect(leftOffset + zoomWidth,
                            zoomHeight,
                            mDestRect.right + leftOffset - zoomWidth,
                            mDestRect.bottom - zoomHeight),
                        mDestRect,null);
                mSurfaceHolder.unlockCanvasAndPost(canvas);

                requeue(delayToNext);
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
