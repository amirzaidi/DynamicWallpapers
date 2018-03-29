package amirz.dynamicwallpapers;

import android.media.audiofx.Visualizer;

public class VisualizeFX extends Visualizer implements Visualizer.OnDataCaptureListener {
    private final DynamicService.WPEngine mEngine;
    public float magnitude;

    public VisualizeFX(DynamicService.WPEngine engine) throws RuntimeException {
        super(0);
        mEngine = engine;
        setEnabled(false);
        setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
        setDataCaptureListener(this, Visualizer.getMaxCaptureRate(), false, true);
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
        //Bias towards bassline
        float totalMagnitude = (float)Math.log10(Math.hypot(fft[0], fft[1]) + 1) * fft.length;
        for (int i = 2; i < fft.length; i += 2) {
            totalMagnitude += (float)Math.log10(Math.hypot(fft[i], fft[i + 1]) + 1);
        }

        float newMagnitude = Curves.clamp(totalMagnitude / fft.length * 0.7f - 0.8f);

        if (newMagnitude < 0.1f) {
            //Don't show any effects on small magnitudes
            newMagnitude = 0f;
        }

        if (newMagnitude != magnitude) {
            if (newMagnitude > magnitude) {
                //Immediately jump to the highest value
                magnitude = newMagnitude;
            } else {
                //Smooth the graph by slowly reducing it
                magnitude = magnitude * 0.67f + newMagnitude * 0.33f;
            }

            mEngine.requeue(0);
        }
    }
}
