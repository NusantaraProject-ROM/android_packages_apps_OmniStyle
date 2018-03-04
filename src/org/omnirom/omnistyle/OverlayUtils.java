/*
 *  Copyright (C) 2018 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omnistyle;

import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.ServiceManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class OverlayUtils {
    private static final String TAG = "OverlayUtils";
    private static final boolean DEBUG = true;

    public static final String OMNI_THEME_PREFIX = "org.omnirom.theme";
    public static final String OMNI_ACCENT_THEME_PREFIX = "org.omnirom.theme.accent";
    public static final String OMNI_PRIMARY_THEME_PREFIX = "org.omnirom.theme.primary";
    public static final String OMNI_NOTIFICATION_THEME_PREFIX = "org.omnirom.theme.notification";
    public static final String KEY_THEMES_DISABLED = "default";

    private final OverlayManager mOverlayService;
    private final PackageManager mPackageManager;
    private final Context mContext;

    public OverlayUtils(Context context) {
        mContext = context;
        mOverlayService = new OverlayManager();
        mPackageManager = context.getPackageManager();
    }

    private boolean isChangeableOverlay(String packageName) {
        try {
            PackageInfo pi = mPackageManager.getPackageInfo(packageName, 0);
            return pi != null && (pi.overlayFlags & PackageInfo.FLAG_OVERLAY_STATIC) == 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public CharSequence getThemeLabel(String packageName)  {
        CharSequence label = null;
        try {
            label = mPackageManager.getApplicationInfo(packageName, 0).loadLabel(mPackageManager);
        } catch (PackageManager.NameNotFoundException e) {
            label = packageName;
        }
        return label;
    }

    public BitmapDrawable getThemeThumbnail(String packageName)  {
        InputStream in = null;
        try {
            Resources themeResources = mPackageManager.getResourcesForApplication(packageName);
            in = themeResources.getAssets().open("thumbnail.png");
            Bitmap b = BitmapFactory.decodeStream(in);
            return new BitmapDrawable(themeResources, b);
        } catch(Exception e) {
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }
        return null;
    }

    public int getThemeColor(String packageName, String colorResource)  {
        if (packageName.startsWith(OMNI_THEME_PREFIX)) {
            try {
                Resources themeResources = mPackageManager.getResourcesForApplication(packageName);
                int resId = themeResources.getIdentifier(colorResource, "color", packageName);
                return themeResources.getColor(resId);
            } catch (Exception e) {
            }
        }
        return 0;
    }

    public String getCurrentTheme() {
        try {
            List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget("android",
                    UserHandle.myUserId());
            for (int i = 0, size = infos.size(); i < size; i++) {
                if (infos.get(i).isEnabled() &&
                        isFullTheme(infos.get(i).packageName) &&
                        isChangeableOverlay(infos.get(i).packageName)) {
                    return infos.get(i).packageName;
                }
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    public String getCurrentTheme(String prefix) {
        try {
            List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget("android",
                    UserHandle.myUserId());
            for (int i = 0, size = infos.size(); i < size; i++) {
                if (infos.get(i).isEnabled() &&
                        infos.get(i).packageName.startsWith(prefix) &&
                        isChangeableOverlay(infos.get(i).packageName)) {
                    return infos.get(i).packageName;
                }
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    public boolean isThemeEnabled(String packageName) {
        try {
            List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget("android",
                    UserHandle.myUserId());
            for (int i = 0, size = infos.size(); i < size; i++) {
                if (infos.get(i).packageName.equals(packageName)) {
                    return infos.get(i).isEnabled();
                }
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    public void enableTheme(String packageName) {
        if (DEBUG) Log.d(TAG, "enableTheme " + packageName);
        try {
            if (packageName.equals(KEY_THEMES_DISABLED)) {
                disableTheme();
            } else {
                mOverlayService.setEnabledExclusive(packageName, true, UserHandle.myUserId());
            }
        } catch (RemoteException e) {
        }
    }

    public void enableThemeList(List<String> packageNameList) {
        if (DEBUG) Log.d(TAG, "enableThemeList " + packageNameList);
        try {
            disableTheme();
            for (String packageName : packageNameList) {
                if (!packageName.equals(KEY_THEMES_DISABLED)) {
                    mOverlayService.setEnabled(packageName, true, UserHandle.myUserId());
                }
            }
        } catch (RemoteException e) {
        }
    }

    public void disableTheme() {
        try {
            List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget("android",
                    UserHandle.myUserId());
            for (int i = 0, size = infos.size(); i < size; i++) {
                if (infos.get(i).isEnabled() &&
                        infos.get(i).packageName.startsWith(OMNI_THEME_PREFIX) &&
                        isChangeableOverlay(infos.get(i).packageName)) {
                    mOverlayService.setEnabled(infos.get(i).packageName, false, UserHandle.myUserId());
                }
            }
        } catch (RemoteException e) {
        }
    }

    private boolean isFullTheme(String packageName) {
        return packageName.startsWith(OMNI_THEME_PREFIX) &&
                !packageName.startsWith(OMNI_ACCENT_THEME_PREFIX) &&
                !packageName.startsWith(OMNI_PRIMARY_THEME_PREFIX) &&
                !packageName.startsWith(OMNI_NOTIFICATION_THEME_PREFIX);
    }

    public String[] getAvailableThemes() {
        try {
            List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget("android",
                    UserHandle.myUserId());
            List<String> pkgs = new ArrayList(infos.size());
            for (int i = 0, size = infos.size(); i < size; i++) {
                if (isFullTheme(infos.get(i).packageName) &&
                        isChangeableOverlay(infos.get(i).packageName)) {
                    pkgs.add(infos.get(i).packageName);
                }
            }
            return pkgs.toArray(new String[pkgs.size()]);
        } catch (RemoteException e) {
        }
        return new String[0];
    }

    public String[] getAvailableThemes(String prefix) {
        try {
            List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget("android",
                    UserHandle.myUserId());
            List<String> pkgs = new ArrayList(infos.size());
            for (int i = 0, size = infos.size(); i < size; i++) {
                if (infos.get(i).packageName.startsWith(prefix) &&
                        isChangeableOverlay(infos.get(i).packageName)) {
                    pkgs.add(infos.get(i).packageName);
                }
            }
            return pkgs.toArray(new String[pkgs.size()]);
        } catch (RemoteException e) {
        }
        return new String[0];
    }

    private static class OverlayManager {
        private final IOverlayManager mService;

        public OverlayManager() {
            mService = IOverlayManager.Stub.asInterface(
                    ServiceManager.getService(Context.OVERLAY_SERVICE));
        }

        public void setEnabledExclusive(String pkg, boolean enabled, int userId)
                throws RemoteException {
            mService.setEnabledExclusive(pkg, enabled, userId);
        }

        public void setEnabled(String pkg, boolean enabled, int userId)
                throws RemoteException {
            mService.setEnabled(pkg, enabled, userId);
        }

        public List<OverlayInfo> getOverlayInfosForTarget(String target, int userId)
                throws RemoteException {
            return mService.getOverlayInfosForTarget(target, userId);
        }
    }
}

