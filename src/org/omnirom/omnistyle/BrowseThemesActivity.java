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
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
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
import java.util.List;

public class BrowseThemesActivity extends Activity {
    private static final String TAG = "BrowseThemesActivity";
    private static final float BITMAP_SCALE = 0.5f;
    private static final float BLUR_RADIUS = 7.5f;

    private List<ThemeInfo> mThemeList;
    private RecyclerView mThemeView;
    private ThemeListAdapter mAdapter;
    private ProgressBar mProgressBar;
    private OverlayUtils mOverlayUtils;
    private String mCurrentTheme;
    private int mAccentColor;

    private class ThemeInfo {
        public String mPackageName;
        public CharSequence mName;
        public BitmapDrawable mThumbNail;
        public Bitmap mThumbNailBlur;
    }

    class ImageHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public View rootView;
        public ImageView mThemeImage;
        public TextView mThemeName;

        ImageHolder(View itemView) {
            super(itemView);
            rootView = itemView;
            mThemeImage = (ImageView) itemView.findViewById(R.id.theme_image);
            mThemeName = (TextView) itemView.findViewById(R.id.theme_name);
            rootView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getAdapterPosition();
            ThemeInfo wi = mThemeList.get(position);
            mOverlayUtils.enableTheme(wi.mPackageName);
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
            ThemeInfo wi = mThemeList.get(position);
            if (wi.mThumbNail != null) {
                if (!wi.mPackageName.equals(mCurrentTheme)) {
                    holder.mThemeImage.setImageBitmap(wi.mThumbNailBlur);
                } else {
                    holder.mThemeImage.setImageDrawable(wi.mThumbNail);
                }
            } else {
                holder.mThemeImage.setImageDrawable(new ColorDrawable(Color.DKGRAY));
            }
            if (wi.mPackageName.equals(mCurrentTheme)) {
                holder.mThemeName.setTextColor(mAccentColor);
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

        mCurrentTheme = mOverlayUtils.getCurrentTheme();
        if (mCurrentTheme == null) {
            mCurrentTheme = OverlayUtils.KEY_THEMES_DISABLED;
        }
        mAccentColor = getColorAttr(android.R.attr.colorAccent);

        mThemeList = reloadThemes();
        mAdapter.notifyDataSetChanged();
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

    private List<ThemeInfo> reloadThemes() {
        List<ThemeInfo> themeList = new ArrayList<ThemeInfo>();
        String[] themes = mOverlayUtils.getAvailableThemes();
        for (String theme : themes) {
            CharSequence name = mOverlayUtils.getThemeLabel(theme);
            BitmapDrawable thumbnail = mOverlayUtils.getThemeThumbnail(theme);
            ThemeInfo wi = new ThemeInfo();
            wi.mPackageName = theme;
            wi.mName = name;
            wi.mThumbNail = thumbnail;
            if (thumbnail != null) {
                wi.mThumbNailBlur = blur(thumbnail.getBitmap());
            }
            themeList.add(wi);
        }
        ThemeInfo wi = new ThemeInfo();
        wi.mPackageName = OverlayUtils.KEY_THEMES_DISABLED;
        wi.mName = getResources().getString(R.string.theme_disable);
        wi.mThumbNail = getDefaultThemeThumbnail();
        if (wi.mThumbNail != null) {
            wi.mThumbNailBlur = blur(wi.mThumbNail.getBitmap());
        }
        themeList.add(0, wi);
        return themeList;
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

    private int getColorAttr(int attr) {
        TypedArray ta = this.obtainStyledAttributes(new int[]{attr});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }
}
