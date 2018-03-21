package amirz.dynamicwallpapers;

import org.junit.Test;

import static org.junit.Assert.*;

public class CurvesUnitTest {
    @Test
    public void extend_low() {
        assertEquals(0f, Curves.extend(0f, .25f, .75f), 0.001f);
    }

    @Test
    public void extend_lowmid() {
        assertEquals(.25f, Curves.extend(.125f, .25f, .75f), 0.001f);
    }

    @Test
    public void extend_midlow() {
        assertEquals(.5f, Curves.extend(.25f, .25f, .75f), 0.001f);
    }

    @Test
    public void extend_mid() {
        assertEquals(.5f, Curves.extend(.4f, .25f, .75f), 0.001f);
    }

    @Test
    public void extend_midhigh() {
        assertEquals(.5f, Curves.extend(.75f, .25f, .75f), 0.001f);
    }

    @Test
    public void extend_highmid() {
        assertEquals(.75f, Curves.extend(.875f, .25f, .75f), 0.001f);
    }

    @Test
    public void extend_high() {
        assertEquals(1f, Curves.extend(1f, .25f, .75f), 0.001f);
    }
}
