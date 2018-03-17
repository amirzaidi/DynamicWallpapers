package amirz.dynamicwallpapers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public class DynamicService extends WallpaperService {
    private final static int AUTO_UPDATE_TICK_MS = 60 * 1000;

    @Override
    public Engine onCreateEngine() {
        android.util.Log.e("DynamicWallpaper", "onCreateEngine");
        return new WPEngine(BitmapFactory.decodeResource(getResources(), R.drawable.bg));
    }

    class WPEngine extends WallpaperService.Engine implements Runnable {
        private final Handler mHandler = new Handler();
        private final Bitmap mBitmap;
        private SurfaceHolder mSurfaceHolder;
        private boolean mVisible;

        WPEngine(Bitmap bitmap) {
            super();
            mBitmap = bitmap;
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            android.util.Log.e("DynamicWallpaper", "onCreate");
            super.onCreate(surfaceHolder);
            mSurfaceHolder = surfaceHolder;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            android.util.Log.e("DynamicWallpaper", "onVisibilityChanged");
            super.onVisibilityChanged(visible);
            mVisible = visible;
            run();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            android.util.Log.e("DynamicWallpaper", "onSurfaceChanged");
            super.onSurfaceChanged(holder, format, width, height);
            run();
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            android.util.Log.e("DynamicWallpaper", "onOffsetsChanged");
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
            run();
        }

        @Override
        public void onDestroy() {
            android.util.Log.e("DynamicWallpaper", "onDestroy");
            super.onDestroy();
            mSurfaceHolder = null;
        }

        @Override
        public void run() {
            android.util.Log.e("DynamicWallpaper", "run");
            if (mVisible) {
                Canvas canvas = mSurfaceHolder.lockCanvas();

                canvas.save();
                canvas.scale(1f, 1f);

                android.util.Log.e("DynamicWallpaper", "draw");
                Rect target = mSurfaceHolder.getSurfaceFrame();

                float cutHorizontal = 0;
                float cutVertical = 0;

                float widenessBm = (float)mBitmap.getWidth() / mBitmap.getHeight();
                float widenessCv = (float)target.right / target.bottom;
                if (widenessBm > widenessCv) {
                    float heightScale = mBitmap.getHeight() / target.bottom;
                    cutHorizontal = (mBitmap.getWidth() - heightScale * target.right) / 2;
                } else if (widenessBm < widenessCv) {
                    float widthScale = mBitmap.getWidth() / target.right;
                    cutVertical = (mBitmap.getHeight() - widthScale * target.bottom) / 2;
                }

                canvas.drawBitmap(mBitmap,
                        new Rect((int)cutHorizontal,
                                (int)cutVertical,
                                (int)(mBitmap.getWidth() - cutHorizontal),
                                (int)(mBitmap.getHeight() - cutVertical)),
                        mSurfaceHolder.getSurfaceFrame(),
                        null);

                canvas.restore();
                mSurfaceHolder.unlockCanvasAndPost(canvas);

                mHandler.removeCallbacks(this);
                mHandler.postDelayed(this, AUTO_UPDATE_TICK_MS);
            }
        }
    }
}
