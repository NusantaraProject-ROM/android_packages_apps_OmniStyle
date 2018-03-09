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

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ComposeThemeActivity extends Activity {
    private static final String TAG = "ComposeThemeActivity";
    private static final boolean DEBUG = false;
    private Spinner mAccentSpinner;
    private Spinner mPrimarySpinner;
    private Spinner mNotificationSpinner;
    private List<ThemeInfo> mAccentOverlays;
    private List<ThemeInfo> mPrimaryOverlays;
    private List<ThemeInfo> mNotificationOverlays;
    private OverlayUtils mOverlayUtils;
    private List<String> mOverlayCompose = new ArrayList<>();

    private class ThemeInfo implements Comparable<ThemeInfo> {
        public String mPackageName;
        public String mName;

        @Override
        public int compareTo(ThemeInfo o) {
            return mName.compareTo(o.mName);
        }
    }

    private class OverlayAdapter extends ArrayAdapter<CharSequence> {
        private List<CharSequence> mOverlayNames;
        private List<Integer> mOverlayColors;
        private boolean mWithColor;
        private String mColorResource;
        private String mCurrentPrimary;
        private boolean mColorCircle;

        public OverlayAdapter(Context context, List<ThemeInfo> overlayList, boolean withColor, String colorResource,
                              String currentPrimary) {
            super(context, R.layout.color_spinner_item);
            mWithColor = withColor;
            mColorResource = colorResource;
            mCurrentPrimary = currentPrimary;
            int currentPrimaryColor = 0;
            if (currentPrimary != null) {
                currentPrimaryColor = mOverlayUtils.getThemeColor(currentPrimary, "omni_color2");
            }

            mOverlayNames = new ArrayList<>();
            mOverlayColors = new ArrayList<>();

            for (ThemeInfo overlay : overlayList){
                mOverlayNames.add(overlay.mName);
                if (mWithColor) {
                    if (overlay.mPackageName.equals("org.omnirom.theme.notification.primary")) {
                        // hacky
                        mOverlayColors.add(currentPrimaryColor);
                    } else {
                        int themeColor = mOverlayUtils.getThemeColor(overlay.mPackageName, mColorResource);
                        mOverlayColors.add(themeColor);
                    }
                }
            }
            mOverlayNames.add(0, getResources().getString(R.string.theme_disable));
            if (mWithColor) {
                mOverlayColors.add(0, 0);
            }
            if (DEBUG) Log.d(TAG, "OverlayAdapter = " + mOverlayNames);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater)
                    getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View rowView;
            final ViewHolder holder;

            if (convertView == null) {
                rowView = inflater.inflate(R.layout.color_spinner_item, null);
                holder = new ViewHolder();
                holder.swatch = (LayerDrawable) getDrawable(R.drawable.color_dot);
                holder.label = (TextView) rowView.findViewById(R.id.color_text);
                holder.icon = (ImageView) rowView.findViewById(R.id.color_icon);
                rowView.setTag(holder);
            } else {
                rowView = convertView;
                holder = (ViewHolder) rowView.getTag();
            }

            holder.label.setText(getItem(position));
            if (mWithColor && mOverlayColors.get(position) != 0) {
                GradientDrawable inner = (GradientDrawable) holder.swatch.findDrawableByLayerId(R.id.dot_inner);
                inner.setColor(mOverlayColors.get(position));
                holder.icon.setVisibility(View.VISIBLE);
                holder.icon.setImageDrawable(holder.swatch);
            } else {
                holder.icon.setVisibility(View.INVISIBLE);
            }
            return rowView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View rowView = getView(position, convertView, parent);
            return rowView;
        }

        @Override
        public int getCount() {
            return mOverlayNames.size();
        }

        @Override
        public CharSequence getItem(int position) {
            return mOverlayNames.get(position);
        }

        private class ViewHolder {
            TextView label;
            ImageView icon;
            LayerDrawable swatch;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_themes_compose);

        getActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);
        mOverlayUtils = new OverlayUtils(this);

        mOverlayCompose.add(OverlayUtils.KEY_THEMES_DISABLED);
        mOverlayCompose.add(OverlayUtils.KEY_THEMES_DISABLED);
        mOverlayCompose.add(OverlayUtils.KEY_THEMES_DISABLED);

        mAccentSpinner = (Spinner) findViewById(R.id.accent_select);
        mPrimarySpinner = (Spinner) findViewById(R.id.primary_select);
        mNotificationSpinner = (Spinner) findViewById(R.id.notification_select);

        mAccentOverlays = new ArrayList();
        final List<String> accentOverlays = new ArrayList();
        accentOverlays.addAll(Arrays.asList(mOverlayUtils.getAvailableThemes(OverlayUtils.OMNI_ACCENT_THEME_PREFIX)));
        if (DEBUG) Log.d(TAG, "accentOverlays = " + accentOverlays);
        for (String packageName : accentOverlays) {
            ThemeInfo ti = new ThemeInfo();
            ti.mPackageName = packageName;
            ti.mName = mOverlayUtils.getThemeLabel(packageName).toString();
            mAccentOverlays.add(ti);
        }
        Collections.sort(mAccentOverlays);

        mPrimaryOverlays = new ArrayList();
        final List<String> primaryOverlays = new ArrayList();
        primaryOverlays.addAll(Arrays.asList(mOverlayUtils.getAvailableThemes(OverlayUtils.OMNI_PRIMARY_THEME_PREFIX)));
        if (DEBUG) Log.d(TAG, "primaryOverlays = " + primaryOverlays);
        for (String packageName : primaryOverlays) {
            ThemeInfo ti = new ThemeInfo();
            ti.mPackageName = packageName;
            ti.mName = mOverlayUtils.getThemeLabel(packageName).toString();
            mPrimaryOverlays.add(ti);
        }
        Collections.sort(mPrimaryOverlays);

        mNotificationOverlays = new ArrayList();
        final List<String> notificationOverlays = new ArrayList();
        notificationOverlays.addAll(Arrays.asList(mOverlayUtils.getAvailableThemes(OverlayUtils.OMNI_NOTIFICATION_THEME_PREFIX)));
        if (DEBUG) Log.d(TAG, "notificationOverlays = " + notificationOverlays);
        for (String packageName : notificationOverlays) {
            ThemeInfo ti = new ThemeInfo();
            ti.mPackageName = packageName;
            ti.mName = mOverlayUtils.getThemeLabel(packageName).toString();
            mNotificationOverlays.add(ti);
        }
        Collections.sort(mNotificationOverlays);

        String currentAccent = mOverlayUtils.getCurrentTheme(OverlayUtils.OMNI_ACCENT_THEME_PREFIX);
        if (currentAccent != null) {
            mOverlayCompose.set(0, currentAccent);
        }
        String currentPrimary = mOverlayUtils.getCurrentTheme(OverlayUtils.OMNI_PRIMARY_THEME_PREFIX);
        if (currentPrimary != null) {
            mOverlayCompose.set(1, currentPrimary);
        }
        String currentNotification = mOverlayUtils.getCurrentTheme(OverlayUtils.OMNI_NOTIFICATION_THEME_PREFIX);
        if (currentNotification != null) {
            mOverlayCompose.set(2, currentNotification);
        }
        mAccentSpinner.setAdapter(new OverlayAdapter(this, mAccentOverlays, true, "omni_color5", currentPrimary));
        if (currentAccent != null) {
            mAccentSpinner.setSelection(getOverlaySpinnerPosition(mAccentOverlays, currentAccent) + 1, false);
        } else {
            mAccentSpinner.setSelection(0, false);
        }
        mAccentSpinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String packageName = OverlayUtils.KEY_THEMES_DISABLED;
                if (position != 0) {
                    packageName = mAccentOverlays.get(position - 1).mPackageName;
                }
                mOverlayCompose.set(0, packageName);
                updatePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        mPrimarySpinner.setAdapter(new OverlayAdapter(this, mPrimaryOverlays, true, "omni_color2", currentPrimary));
        if (currentPrimary != null) {
            mPrimarySpinner.setSelection(getOverlaySpinnerPosition(mPrimaryOverlays, currentPrimary) + 1, false);
        } else {
            mPrimarySpinner.setSelection(0, false);
        }

        mPrimarySpinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String packageName = OverlayUtils.KEY_THEMES_DISABLED;
                if (position != 0) {
                    packageName = mPrimaryOverlays.get(position - 1).mPackageName;
                }
                mOverlayCompose.set(1, packageName);
                updatePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        mNotificationSpinner.setAdapter(new OverlayAdapter(this, mNotificationOverlays, true, "omni_theme_color", currentPrimary));
        if (currentNotification != null) {
            mNotificationSpinner.setSelection(getOverlaySpinnerPosition(mNotificationOverlays, currentNotification) + 1, false);
        } else {
            mNotificationSpinner.setSelection(0, false);
        }
        mNotificationSpinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String packageName = OverlayUtils.KEY_THEMES_DISABLED;
                if (position != 0) {
                    packageName = mNotificationOverlays.get(position - 1).mPackageName;
                }
                mOverlayCompose.set(2, packageName);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        Button applyButton = (Button) findViewById(R.id.overlay_apply);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOverlayUtils.enableThemeList(mOverlayCompose);
            }
        });
        updatePreview();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }

    private int getOverlaySpinnerPosition(List<ThemeInfo> overlays, String packageName) {
        int i = 0;
        for (ThemeInfo ti : overlays) {
            if (ti.mPackageName.equals(packageName)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private int getSystemTextColor() {
        return getColor(R.color.system_default_text_color);
    }

    private int getSystemPrimary() {
        return getColor(R.color.system_default_primary);
    }

    private int getSystemPrimaryDark() {
        return getColor(R.color.system_default_primary_dark);
    }

    private int getSystemAccent() {
        return getColor(R.color.system_default_accent);
    }

    private void updatePreview() {
        if (DEBUG) Log.d(TAG, "updatePreview = " + mOverlayCompose);
        View main = findViewById(R.id.overlay_preview);
        ImageView image = findViewById(R.id.overlay_preview_image);
        TextView text = findViewById(R.id.overlay_preview_text);
        Switch sw = findViewById(R.id.overlay_preview_switch);
        TextView header = findViewById(R.id.overlay_preview_header);
        TextView text1 = findViewById(R.id.overlay_preview_text1);
        SeekBar seek = findViewById(R.id.overlay_preview_seek);
        Button button1 = findViewById(R.id.overlay_preview_button1);
        Button button2 = findViewById(R.id.overlay_preview_button2);

        int accentColor = mOverlayCompose.get(0).equals(OverlayUtils.KEY_THEMES_DISABLED)
                ? getSystemAccent()
                : mOverlayUtils.getThemeColor(mOverlayCompose.get(0), "omni_color5");
        int primaryColor = mOverlayCompose.get(1).equals(OverlayUtils.KEY_THEMES_DISABLED)
                ? getSystemPrimary()
                : mOverlayUtils.getThemeColor(mOverlayCompose.get(1), "omni_color2");
        int textColor = mOverlayCompose.get(1).equals(OverlayUtils.KEY_THEMES_DISABLED)
                ? getSystemTextColor()
                : mOverlayUtils.getThemeColor(mOverlayCompose.get(1), "omni_text1");
        int primaryColorDark = mOverlayCompose.get(1).equals(OverlayUtils.KEY_THEMES_DISABLED)
                ? getSystemPrimaryDark()
                : mOverlayUtils.getThemeColor(mOverlayCompose.get(1), "omni_color1");

        image.setColorFilter(accentColor);
        main.setBackgroundColor(primaryColor);
        text.setTextColor(textColor);
        text1.setTextColor(textColor);
        sw.getThumbDrawable().setColorFilter(accentColor, PorterDuff.Mode.SRC_ATOP);
        sw.setChecked(true);
        header.setBackgroundColor(primaryColorDark);
        header.setTextColor(accentColor);
        seek.setMax(100);
        seek.setProgress(65);
        seek.getThumb().setColorFilter(accentColor, PorterDuff.Mode.SRC_ATOP);
        seek.getProgressDrawable().setColorFilter(accentColor, PorterDuff.Mode.SRC_ATOP);
        button1.setTextColor(accentColor);
        button2.setTextColor(accentColor);
    }
}

