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
        setDataCaptureListener(this, Visualizer.getMaxCaptureRate() / 2, false, true);
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

        float magnitudeSum = 0;
        for (int i = 0; i < max; i += 2) {
            magnitudeSum += Math.hypot(mFft[i], mFft[i + 1]);
        }

        float magnitudeAvg = Curves.clamp(magnitudeSum / max / 64);

        if (magnitudeAvg != magnitude) {
            if (magnitudeAvg > magnitude) {
                //Immediately jump to the highest value
                magnitude = magnitudeAvg;
            } else {
                //Smooth the graph by slowly reducing it
                magnitude = magnitude * 0.67f + magnitudeAvg * 0.33f;
            }

            if (magnitude < 0.1f) {
                //Don't show any effects on small magnitudes
                magnitude = 0f;
            }

            mUpdate.run();
        }
    }
}
