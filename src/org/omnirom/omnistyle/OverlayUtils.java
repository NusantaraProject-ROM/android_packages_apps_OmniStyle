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
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.ServiceManager;
import android.text.TextUtils;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OverlayUtils {
    private static final String TAG = "OverlayUtils";
    private static final boolean DEBUG = true;

    private static final String OMNI_THEME_PREFIX = "org.omnirom.theme";
    public static final String OMNI_ACCENT_THEME_PREFIX = "org.omnirom.theme.accent";
    public static final String OMNI_PRIMARY_THEME_PREFIX = "org.omnirom.theme.primary";
    public static final String OMNI_NOTIFICATION_THEME_PREFIX = "org.omnirom.theme.notification";
    public static final String KEY_THEMES_DISABLED = "default";
    public static final String OMNI_APP_THEME_PREFIX = "org.omnirom.theme.app";
    public static final String ANDROID_TARGET_PACKAGE = "android";

    private final OverlayManager mOverlayService;
    private final PackageManager mPackageManager;
    private final Context mContext;
    private List<ThemeInfo> mThemeList = new ArrayList<>();

    public static class ThemeInfo implements Comparable<ThemeInfo> {
        String mName;
        String mThumbNailFile;
        String mAccent;
        String mPrimary;
        String mNotification;
        BitmapDrawable mThumbNail;
        Bitmap mThumbNailBlur;
        boolean mComposePlaceholder;
        boolean mDefaultPlaceholder;

        @Override
        public int compareTo(ThemeInfo o) {
            return mName.compareTo(o.mName);
        }
    
        @Override
        public String toString() {
            return mName + " " + mThumbNailFile + " " + mAccent + "," + mPrimary + "," + mNotification;
        }
    }

    public OverlayUtils(Context context) {
        mContext = context;
        mOverlayService = new OverlayManager();
        mPackageManager = context.getPackageManager();
        loadThemesFromXml();
    }

    private boolean isChangeableOverlay(String packageName) {
        try {
            PackageInfo pi = mPackageManager.getPackageInfo(packageName, 0);
            return pi != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public CharSequence getPackageLabel(String packageName)  {
        CharSequence label = null;
        try {
            label = mPackageManager.getApplicationInfo(packageName, 0).loadLabel(mPackageManager);
        } catch (PackageManager.NameNotFoundException e) {
            label = packageName;
        }
        return label;
    }

    public Drawable getPackageIcon(String packageName)  {
        Drawable icon = null;
        try {
            icon = mPackageManager.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
        }
        return icon;
    }

    public BitmapDrawable getThemeThumbnail(ThemeInfo ti)  {
        InputStream in = null;
        try {
            in = mContext.getAssets().open(ti.mThumbNailFile);
            Bitmap b = BitmapFactory.decodeStream(in);
            return new BitmapDrawable(mContext.getResources(), b);
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
                return themeResources.getColor(resId, null);
            } catch (Exception e) {
            }
        }
        return 0;
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

    private boolean isThemeEnabled(String packageName) {
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

    public boolean isThemeEnabled(ThemeInfo themeInfo) {
        return isThemeEnabled(themeInfo.mAccent) && isThemeEnabled(themeInfo.mPrimary)
                    && isThemeEnabled(themeInfo.mNotification);
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
            Map<String, List<OverlayInfo>> infos = mOverlayService.getAllOverlays(
                    UserHandle.myUserId());
            List<String> pkgs = new ArrayList(infos.keySet().size());
            for (String targetPkg : infos.keySet()) {
                List<OverlayInfo> overlays = infos.get(targetPkg);
                for (OverlayInfo oi : overlays) {
                    String packageName = oi.packageName;
                    if (packageName.startsWith(OMNI_THEME_PREFIX) && isChangeableOverlay(packageName)) {
                        mOverlayService.setEnabled(packageName, false, UserHandle.myUserId());
                    }
                }
            }
        } catch (RemoteException e) {
        }
    }

    public String[] getAvailableThemes(String prefix) {
        try {
            Map<String, List<OverlayInfo>> infos = mOverlayService.getAllOverlays(
                    UserHandle.myUserId());
            List<String> pkgs = new ArrayList(infos.keySet().size());
            for (String targetPkg : infos.keySet()) {
                List<OverlayInfo> overlays = infos.get(targetPkg);
                for (OverlayInfo oi : overlays) {
                    String packageName = oi.packageName;
                    if (packageName.startsWith(prefix) && isChangeableOverlay(packageName)) {
                        pkgs.add(packageName);
                    }
                }
            }
            return pkgs.toArray(new String[pkgs.size()]);
        } catch (RemoteException e) {
        }
        return new String[0];
    }

    public String[] getAvailableThemes(String prefix, String targetPackage) {
        try {
            List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget(targetPackage,
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

    public String[] getAvailableTargetPackages(String prefix, String excludePackage) {
        try {
            Map<String, List<OverlayInfo>> infos = mOverlayService.getAllOverlays(
                    UserHandle.myUserId());
            Set<String> pkgs = new HashSet(infos.keySet().size());
            for (String targetPkg : infos.keySet()) {
                if (excludePackage != null && targetPkg.equals(excludePackage)) {
                    continue;
                }
                List<OverlayInfo> overlays = infos.get(targetPkg);
                for (OverlayInfo oi : overlays) {
                    String packageName = oi.packageName;
                    if (packageName.startsWith(prefix) && isChangeableOverlay(packageName)) {
                        pkgs.add(targetPkg);
                    }
                }
            }
            return pkgs.toArray(new String[pkgs.size()]);
        } catch (RemoteException e) {
        }
        return new String[0];
    }

    public List<ThemeInfo> getThemeList() {
        return mThemeList;
    }

    private void loadThemesFromXml() {
        mThemeList.clear();
        InputStream in = null;
        XmlPullParser parser = null;

        try {
            in = mContext.getAssets().open("themes.xml");
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            parser = factory.newPullParser();
            parser.setInput(in, "UTF-8");
            loadThemesFromXmlParser(parser);
        } catch (XmlPullParserException e) {
        } catch (IOException e) {
        } finally {
            // Cleanup resources
            if (parser instanceof XmlResourceParser) {
                ((XmlResourceParser) parser).close();
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void loadThemesFromXmlParser(XmlPullParser parser) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        do {
            if (eventType != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equalsIgnoreCase("theme")) {
                String themeName = parser.getAttributeValue(null, "name");
                String thumbnail = parser.getAttributeValue(null, "thumbnail");
                String accent = parser.getAttributeValue(null, "accent");
                String primary = parser.getAttributeValue(null, "primary");
                String notification = parser.getAttributeValue(null, "notification");
                if (themeName != null && thumbnail != null && accent != null && primary != null && notification != null) {
                    ThemeInfo ti = new ThemeInfo();
                    ti.mName = themeName;
                    ti.mThumbNailFile = thumbnail;
                    ti.mAccent = accent;
                    ti.mPrimary = primary;
                    ti.mNotification = notification;
                    mThemeList.add(ti);
                }
            }
        } while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT);
        if (DEBUG) Log.i(TAG, "loaded size = " + mThemeList.size());
    }

    private static class OverlayManager {
        private final IOverlayManager mService;

        public OverlayManager() {
            mService = IOverlayManager.Stub.asInterface(
                    ServiceManager.getService(Context.OVERLAY_SERVICE));
        }

        public void setEnabled(String pkg, boolean enabled, int userId)
                throws RemoteException {
            mService.setEnabled(pkg, enabled, userId);
        }

        public List<OverlayInfo> getOverlayInfosForTarget(String target, int userId)
                throws RemoteException {
            return mService.getOverlayInfosForTarget(target, userId);
        }

        public Map<String, List<OverlayInfo>> getAllOverlays(int userId)
                throws RemoteException {
            return mService.getAllOverlays(userId);
        }
    }
}
