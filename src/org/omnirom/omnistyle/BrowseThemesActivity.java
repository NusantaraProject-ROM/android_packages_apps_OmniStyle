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
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BrowseThemesActivity extends Activity {
    private static final String TAG = "BrowseThemesActivity";
    private static final float BITMAP_SCALE = 0.5f;
    private static final float BLUR_RADIUS = 7.5f;
    private static final boolean DEBUG = false;

    private List<OverlayUtils.ThemeInfo> mThemeList = new ArrayList();
    private RecyclerView mThemeView;
    private ThemeListAdapter mAdapter;
    private ProgressBar mProgressBar;
    private OverlayUtils mOverlayUtils;
    private OverlayUtils.ThemeInfo mCurrentTheme;
    private int mDefaultColor;
    private boolean mReloading;

    class ImageHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public View rootView;
        public ImageView mThemeImage;
        public TextView mThemeName;
        public ImageView mThemeImageOverlay;

        ImageHolder(View itemView) {
            super(itemView);
            rootView = itemView;
            mThemeImage = (ImageView) itemView.findViewById(R.id.theme_image);
            mThemeName = (TextView) itemView.findViewById(R.id.theme_name);
            mThemeImageOverlay = (ImageView) itemView.findViewById(R.id.theme_image_overlay);
            rootView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (mReloading) {
                return;
            }
            int position = getAdapterPosition();
            try {
                OverlayUtils.ThemeInfo wi = mThemeList.get(position);
                if (wi.mComposePlaceholder) {
                    Intent composeIntent = new Intent(BrowseThemesActivity.this, ComposeThemeActivity.class);
                    startActivity(composeIntent);
                } else {
                    List<String> overlayList = new ArrayList<>();
                    overlayList.add(wi.mAccent);
                    overlayList.add(wi.mPrimary);
                    overlayList.add(wi.mNotification);

                    if (!wi.mAccent.equals(OverlayUtils.KEY_THEMES_DISABLED)
                        || !wi.mPrimary.equals(OverlayUtils.KEY_THEMES_DISABLED)) {
                        final List<String> appOverlays = new ArrayList();
                        appOverlays.addAll(Arrays.asList(mOverlayUtils.getAvailableThemes(OverlayUtils.OMNI_APP_THEME_PREFIX)));
                        overlayList.addAll(appOverlays);
                    }
        
                    mOverlayUtils.enableThemeList(overlayList);
                }
            } catch (Exception e) {
                // you shall not pass
            }
        }
    }

    public class ThemeListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.theme_image, parent, false);
            return new ImageHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int position) {
            final ImageHolder holder = (ImageHolder) h;
            OverlayUtils.ThemeInfo wi = mThemeList.get(position);
            holder.mThemeImageOverlay.setVisibility(wi.mComposePlaceholder ? View.VISIBLE : View.GONE);
            if (wi.mThumbNail != null) {
                if (wi.mComposePlaceholder) {
                    holder.mThemeImage.setImageDrawable(wi.mThumbNail);
                } else if (wi.mThumbNailBlur != null && !wi.equals(mCurrentTheme)) {
                    holder.mThemeImage.setImageBitmap(wi.mThumbNailBlur);
                } else {
                    holder.mThemeImage.setImageDrawable(wi.mThumbNail);
                }
            } else {
                holder.mThemeImage.setImageDrawable(new ColorDrawable(Color.DKGRAY));
            }
            if (!wi.mComposePlaceholder) {
                if (wi.mDefaultPlaceholder) {
                    holder.mThemeName.setTextColor(mDefaultColor);
                } else {
                    holder.mThemeName.setTextColor(mOverlayUtils.getThemeColor(wi.mAccent, "omni_color5"));
                }
            } else {
                holder.mThemeName.setTextColor(Color.WHITE);
            }
            holder.mThemeName.setText(wi.mName);
        }

        @Override
        public int getItemCount() {
            return mThemeList.size();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_themes);
        getActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);

        mOverlayUtils = new OverlayUtils(getApplicationContext());
        mProgressBar = (ProgressBar) findViewById(R.id.browse_progress);
        mThemeView = (RecyclerView) findViewById(R.id.theme_images);
        mThemeView.setLayoutManager(new GridLayoutManager(this, 2));
        mThemeView.setHasFixedSize(false);
        mAdapter = new ThemeListAdapter();
        mThemeView.setAdapter(mAdapter);
        mDefaultColor = getColor(R.color.system_default_accent);
    }

    @Override
    public void onResume() {
        super.onResume();
        mProgressBar.setVisibility(View.VISIBLE);
        new LoadThemesTask().execute();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int spanCount = getResources().getInteger(R.integer.wallpapers_column_count);
        GridLayoutManager manager = (GridLayoutManager) mThemeView.getLayoutManager();
        manager.setSpanCount(spanCount);
        manager.requestLayout();
    }

    private BitmapDrawable getDefaultThemeThumbnail()  {
        InputStream in = null;
        try {
            in = getResources().getAssets().open("default_theme.png");
            Bitmap b = BitmapFactory.decodeStream(in);
            return new BitmapDrawable(getResources(), b);
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

    private void reloadThemes() {
        mThemeList.clear();
        List<OverlayUtils.ThemeInfo> themeList = mOverlayUtils.getThemeList();
        for (OverlayUtils.ThemeInfo theme : themeList) {
            BitmapDrawable thumbnail = mOverlayUtils.getThemeThumbnail(theme);
            theme.mDefaultPlaceholder = false;
            theme.mComposePlaceholder = false;
            theme.mThumbNail = thumbnail;
            if (thumbnail != null) {
                theme.mThumbNailBlur = blur(thumbnail.getBitmap());
            }
            mThemeList.add(theme);
        }
        Collections.sort(mThemeList);
        OverlayUtils.ThemeInfo defaultItem = new OverlayUtils.ThemeInfo();
        defaultItem.mDefaultPlaceholder = true;
        defaultItem.mComposePlaceholder = false;
        defaultItem.mName = getResources().getString(R.string.theme_disable);
        defaultItem.mAccent = OverlayUtils.KEY_THEMES_DISABLED;
        defaultItem.mPrimary = OverlayUtils.KEY_THEMES_DISABLED;
        defaultItem.mNotification = OverlayUtils.KEY_THEMES_DISABLED;
        defaultItem.mThumbNail = getDefaultThemeThumbnail();
        defaultItem.mThumbNailBlur = blur(defaultItem.mThumbNail.getBitmap());
        mThemeList.add(0, defaultItem);

        OverlayUtils.ThemeInfo composeItem = new OverlayUtils.ThemeInfo();
        composeItem.mName = getResources().getString(R.string.theme_compose);
        composeItem.mComposePlaceholder = true;
        composeItem.mDefaultPlaceholder = false;
        BitmapDrawable defaultThumbnail = getDefaultThemeThumbnail();
        defaultThumbnail = new BitmapDrawable(getResources(), blur(defaultThumbnail.getBitmap()));
        int shadow = Color.argb(128, 0, 0, 0);
        defaultThumbnail.setColorFilter(shadow, PorterDuff.Mode.SRC_ATOP);
        composeItem.mThumbNail = defaultThumbnail;
        mThemeList.add(0, composeItem);
    
        if (DEBUG) Log.d(TAG, "mThemeList = " + mThemeList);
    }

    private Bitmap blur(Bitmap image) {
        int width = Math.round(image.getWidth() * BITMAP_SCALE);
        int height = Math.round(image.getHeight() * BITMAP_SCALE);

        Bitmap inputBitmap = Bitmap.createScaledBitmap(image, width, height, false);
        Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

        RenderScript rs = RenderScript.create(this);
        ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
        Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
        theIntrinsic.setRadius(BLUR_RADIUS);
        theIntrinsic.setInput(tmpIn);
        theIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(outputBitmap);

        return outputBitmap;
    }

    private class LoadThemesTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            mReloading = true;
            reloadThemes();
            return null;
        }

        @Override
        protected void onPostExecute(Void feed) {
            mProgressBar.setVisibility(View.GONE);
            mAdapter.notifyDataSetChanged();
            mReloading = false;
        }
    }

}
