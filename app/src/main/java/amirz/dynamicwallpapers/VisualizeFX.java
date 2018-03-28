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
        setDataCaptureListener(this, Math.min(StateTransitions.FAST_UPDATE_FPS * 1000, Visualizer.getMaxCaptureRate()), false, true);
    }

    @Override
    public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
    }

    @Override
    public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
        float highestMagnitude = 0f;

        //Up to 250 Hz
        for (int i = 0; i < 500 * 1000 * fft.length / samplingRate; i += 2) {
            float magnitude = (float)Math.abs(fft[i]) / 128;
            if (magnitude > highestMagnitude) {
                highestMagnitude = magnitude;
            }
        }

        highestMagnitude *= highestMagnitude;
        if (highestMagnitude != magnitude) {
            magnitude = (magnitude + highestMagnitude) / 2;
            if (magnitude < 0.05f) {
                magnitude = 0f;
            }
            mUpdate.run();
        }
    }
}
