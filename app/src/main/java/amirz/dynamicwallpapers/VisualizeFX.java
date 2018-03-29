package amirz.dynamicwallpapers;

import android.media.audiofx.Visualizer;
import android.os.Handler;

public class VisualizeFX extends Visualizer implements Visualizer.OnDataCaptureListener, Runnable {
    private final Runnable mUpdate;
    private final Handler mHandler;
    private byte[] mFft;
    private int mSamplingRate;
    public float magnitude;

    public VisualizeFX(Runnable update) throws RuntimeException {
        super(0);
        mUpdate = update;
        mHandler = new Handler();
        setEnabled(false);
        setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
        setDataCaptureListener(this, Visualizer.getMaxCaptureRate(), false, true);
    }

    @Override
    public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
    }

    @Override
    public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
        mFft = fft;
        mSamplingRate = samplingRate;

        mHandler.removeCallbacks(this);
        mHandler.post(this);
    }

    @Override
    public void run() {
        //To 120 Hz
        int max = 120 * 1000 * 2 * mFft.length / mSamplingRate;

        double magnitudeSum = 0;
        for (int i = 0; i < max; i += 2) {
            double mag = Math.hypot(mFft[i], mFft[i + 1]);
            magnitudeSum += mag;
        }

        float magnitudeAvg = Curves.clamp((float)magnitudeSum / max / 64);
        magnitudeAvg *= magnitudeAvg;

        if (magnitudeAvg != magnitude) {
            magnitude = (magnitudeAvg + magnitude) / 2;
            if (magnitude < 0.05f) {
                magnitude = 0f;
            }
            mUpdate.run();
        }
    }
}
