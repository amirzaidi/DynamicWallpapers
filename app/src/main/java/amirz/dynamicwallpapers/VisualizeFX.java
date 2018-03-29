package amirz.dynamicwallpapers;

import android.media.audiofx.Visualizer;

public class VisualizeFX extends Visualizer implements Visualizer.OnDataCaptureListener {
    private final Runnable mUpdate;
    public float magnitude;

    public VisualizeFX(Runnable update) throws RuntimeException {
        super(0);
        mUpdate = update;
        setEnabled(false);
        setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
        setDataCaptureListener(this, Visualizer.getMaxCaptureRate() / 2, false, true);
    }

    @Override
    public int setEnabled(boolean enabled) {
        if (!enabled) {
            magnitude = 0;
        }
        return super.setEnabled(enabled);
    }

    @Override
    public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
    }

    @Override
    public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
        //To 120 Hz
        int max = 120 * 1000 * 2 * fft.length / samplingRate;

        float magnitudeSum = 0;
        for (int i = 0; i < max; i += 2) {
            magnitudeSum += Math.hypot(fft[i], fft[i + 1]);
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
