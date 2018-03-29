package amirz.dynamicwallpapers;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceFragment;

public class Settings extends Activity {
    private final static String SHARED_PREFERENCES_KEY = "amirz.dynamicwallpapers.prefs";

    private final static String PREF_VISUALIZER = "pref_visualizer";
    private final static String PREF_SCROLLING = "pref_scrolling";
    private final static String PREF_BLUR = "pref_blur";
    private final static String PREF_COLOR = "pref_color";

    public static boolean visualizerEnabled;
    public static boolean scrollingEnabled;
    public static boolean blurEnabled;
    public static boolean colorEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            // Display the fragment as the main content.
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment())
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Permissions.hasAll(this)) {
            requestPermissions(Permissions.NECESSARY, 0);
        }
    }

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
    }

    public static void reload(Context context) {
        SharedPreferences prefs = getPrefs(context);
        Resources res = context.getResources();

        visualizerEnabled = prefs.getBoolean(Settings.PREF_VISUALIZER, res.getBoolean(R.bool.visualizer_enable_default));
        scrollingEnabled = prefs.getBoolean(Settings.PREF_SCROLLING, res.getBoolean(R.bool.scrolling_enable_default));
        blurEnabled = prefs.getBoolean(Settings.PREF_BLUR, res.getBoolean(R.bool.blur_enable_default));
        colorEnabled = prefs.getBoolean(Settings.PREF_COLOR, res.getBoolean(R.bool.color_enable_default));
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.preferences);
        }
    }
}
