package amirz.dynamicwallpapers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.content.ContextCompat;

public class Permissions {
    public final static String[] NECESSARY = new String[] { Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE };

    public static boolean hasAll(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isGranted(context);
    }

    private static boolean isGranted(Context context) {
        for (String perm : NECESSARY) {
            if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
