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
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

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

        private final Canvas mScaledCanvas = new Canvas();
        private Bitmap mScaledBitmap;
        private Bitmap mEffectBitmap;
        private RenderScript mRs;

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

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            mScreenStateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    reloadLockState();
                }
            };
            mContext.registerReceiver(mScreenStateReceiver, filter);

            mSurfaceHolder = surfaceHolder;
            mRs = RenderScript.create(mContext);
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

            mScaledBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mEffectBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            scaleSource(width, height);
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
            mContext.unregisterReceiver(mScreenStateReceiver);
            mSurfaceHolder = null;

            mRs.destroy();
            mEffectBitmap.recycle();
            mScaledBitmap.recycle();
            mSrcBitmap.recycle();
        }

        @Override
        public void run() {
            if (mVisible && mSurfaceHolder != null) {
                int delayToNext = mTransitions.delayToNext();
                int blurRadius = mTransitions.getBlur();

                Allocation allocIn = Allocation.createFromBitmap(mRs, mScaledBitmap);
                Allocation allocOut = Allocation.createFromBitmap(mRs, mEffectBitmap);

                allocIn.copyTo(mEffectBitmap);

                //color curves

                if (blurRadius > 0) {
                    ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(mRs, allocIn.getElement());
                    blur.setRadius(blurRadius);
                    blur.setInput(allocIn);
                    blur.forEach(allocOut);
                    allocOut.copyTo(mEffectBitmap);
                }

                allocOut.destroy();
                allocIn.destroy();

                Canvas canvas = mSurfaceHolder.lockCanvas();
                canvas.drawBitmap(mEffectBitmap, 0, 0,null);
                mSurfaceHolder.unlockCanvasAndPost(canvas);

                mHandler.removeCallbacks(this);
                mHandler.postDelayed(this, delayToNext);
            }
        }
    }
}
