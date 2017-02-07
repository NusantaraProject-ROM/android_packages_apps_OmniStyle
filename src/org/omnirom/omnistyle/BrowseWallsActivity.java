/*
 *  Copyright (C) 2017 The OmniROM Project
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
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import com.squareup.picasso.Picasso;

public class BrowseWallsActivity extends Activity {
    private static final String TAG = "BrowseWallsActivity";
    private static final String IMAGE_TYPE = "image/*";
    private static final int IMAGE_CROP_AND_SET = 1;
    private static final boolean DEBUG = true;
    private List<WallpaperInfo> mWallpaperList;
    private Resources mRes;
    private GridView mWallpaperView;
    private ProgressBar mProgress;
    private String mPackageName = "org.omnirom.omnistyle";
    private WallpaperListAdapter mAdapter;

    private class WallpaperInfo {
        public String mImage;
        public String mCreator;
    }

    public class WallpaperListAdapter extends ArrayAdapter<WallpaperInfo> {
        private final LayoutInflater mInflater;

        public WallpaperListAdapter(Context context) {
            super(context, R.layout.wallpaper_image, mWallpaperList);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            WallpaperImageHolder holder = WallpaperImageHolder.createOrRecycle(mInflater, convertView);
            convertView = holder.rootView;
            WallpaperInfo wi = mWallpaperList.get(position);
            int resId = mRes.getIdentifier(wi.mImage, "drawable", mPackageName);

            if (resId != 0) {
                Picasso.with(BrowseWallsActivity.this).load(resId).into(holder.mWallpaperImage);
            } else {
                holder.mWallpaperImage.setImageDrawable(null);
            }

            holder.mWallpaperName.setText(wi.mImage);
            holder.mWallpaperCreator.setVisibility(TextUtils.isEmpty(wi.mCreator) ? View.GONE : View.VISIBLE);
            holder.mWallpaperCreator.setText(wi.mCreator);

            return convertView;
        }
    }

    public static class WallpaperImageHolder {
        public View rootView;
        public ImageView mWallpaperImage;
        public TextView mWallpaperName;
        public TextView mWallpaperCreator;

        public static WallpaperImageHolder createOrRecycle(LayoutInflater inflater, View convertView) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.wallpaper_image, null);
                WallpaperImageHolder holder = new WallpaperImageHolder();
                holder.rootView = convertView;
                holder.mWallpaperImage = (ImageView) convertView.findViewById(R.id.wallpaper_image);
                holder.mWallpaperName = (TextView) convertView.findViewById(R.id.wallpaper_name);
                holder.mWallpaperCreator = (TextView) convertView.findViewById(R.id.wallpaper_creator);
                convertView.setTag(holder);
                return holder;
            } else {
                // Get the ViewHolder back to get fast access to the TextView
                // and the ImageView.
                return (WallpaperImageHolder)convertView.getTag();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_wallpapers);

        getActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP|ActionBar.DISPLAY_SHOW_TITLE);

        mProgress = (ProgressBar) findViewById(R.id.browse_progress);
        mWallpaperList = new ArrayList<WallpaperInfo>();

        PackageManager packageManager = getPackageManager();
        try {
            mRes = packageManager.getResourcesForApplication(mPackageName);
            getAvailableWallpapers();

            mWallpaperView = (GridView) findViewById(R.id.wallpaper_images);
            mWallpaperView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (!checkCropActivity()) {
                        AlertDialog.Builder noCropActivityDialog = new AlertDialog.Builder(BrowseWallsActivity.this);
                        noCropActivityDialog.setMessage(getResources().getString(R.string.no_crop_activity_dialog_text));
                        noCropActivityDialog.setTitle(getResources().getString(R.string.no_crop_activity_dialog_title));
                        noCropActivityDialog.setCancelable(false);
                        noCropActivityDialog.setPositiveButton(android.R.string.ok, null);
                        AlertDialog d = noCropActivityDialog.create();
                        d.show();
                        return;
                    }
                    WallpaperInfo wi = mWallpaperList.get(i);
                    WallpaperManager wpm = WallpaperManager.getInstance(getApplicationContext());
                    int resId = mRes.getIdentifier(wi.mImage, "drawable", mPackageName);
                    if (resId == 0) {
                        return;
                    }
                    Drawable image = mRes.getDrawable(resId, null);

                    int wpWidth = wpm.getDesiredMinimumWidth();
                    int wpHeight = wpm.getDesiredMinimumHeight();
                    Display disp = getWindowManager().getDefaultDisplay();
                    Point dispSize = new Point();
                    disp.getRealSize(dispSize);

                    // if that image ratio is close to the display size ratio
                    // assume this wall is meant to be fullscreen without scrolling
                    float displayRatio = (float) Math.round(((float) dispSize.x / dispSize.y) * 10) / 10;
                    float imageRatio = (float) Math.round(((float) image.getIntrinsicWidth() / image.getIntrinsicHeight()) * 10) / 10;
                    if (displayRatio == imageRatio) {
                        wpWidth = dispSize.x;
                        wpHeight = dispSize.y;
                    }

                    float spotlightX = (float) dispSize.x / wpWidth;
                    float spotlightY = (float) dispSize.y / wpHeight;

                    Uri imageUri = Uri.parse("android.resource://" + mPackageName + "/" + resId);

                    final Intent cropAndSetWallpaperIntent = getCropActivity()
                        .setDataAndType(imageUri, IMAGE_TYPE)
                        .putExtra(CropExtras.KEY_OUTPUT_X, wpWidth)
                        .putExtra(CropExtras.KEY_OUTPUT_Y, wpHeight)
                        .putExtra(CropExtras.KEY_ASPECT_X, wpWidth)
                        .putExtra(CropExtras.KEY_ASPECT_Y, wpHeight)
                        .putExtra(CropExtras.KEY_SPOTLIGHT_X, spotlightX)
                        .putExtra(CropExtras.KEY_SPOTLIGHT_Y, spotlightY)
                        .putExtra(CropExtras.KEY_SCALE, true)
                        .putExtra(CropExtras.KEY_SCALE_UP_IF_NEEDED, true);

                    AlertDialog.Builder wallpaperTypeDialog = new AlertDialog.Builder(BrowseWallsActivity.this);
                    wallpaperTypeDialog.setTitle(getResources().getString(R.string.wallpaper_type_dialog_title));
                    wallpaperTypeDialog.setItems(R.array.wallpaper_type_list, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int item) {
                            int wallpaperType = CropExtras.DEFAULT_WALLPAPER_TYPE;
                            if (item == 1) {
                                wallpaperType = WallpaperManager.FLAG_SYSTEM;
                            } else if (item == 2) {
                                wallpaperType = WallpaperManager.FLAG_LOCK;
                            }
                            cropAndSetWallpaperIntent.putExtra(CropExtras.KEY_SET_AS_WALLPAPER, true)
                                    .putExtra(CropExtras.KEY_WALLPAPER_TYPE, wallpaperType);
                            startActivityForResult(cropAndSetWallpaperIntent, IMAGE_CROP_AND_SET);
                        }
                    });
                    AlertDialog d = wallpaperTypeDialog.create();
                    d.show();
                }
            });
            mAdapter = new WallpaperListAdapter(this);
            mWallpaperView.setAdapter(mAdapter);
            mAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            Log.e(TAG, "init failed", e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMAGE_CROP_AND_SET && resultCode == Activity.RESULT_OK) {
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }

    private void getAvailableWallpapers() throws XmlPullParserException, IOException {
        mWallpaperList.clear();
        InputStream in = null;
        XmlPullParser parser = null;

        try {
            in = mRes.getAssets().open("wallpapers.xml");
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            parser = factory.newPullParser();
            parser.setInput(in, "UTF-8");
            loadResourcesFromXmlParser(parser);
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

    private void loadResourcesFromXmlParser(XmlPullParser parser) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        do {
            if (eventType != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equalsIgnoreCase("wallpaper")) {
                String image = parser.getAttributeValue(null, "image");
                if (image != null) {
                    String creator = parser.getAttributeValue(null, "creator");
                    WallpaperInfo wi = new WallpaperInfo();
                    wi.mImage = image;
                    wi.mCreator = creator;
                    if (DEBUG) Log.i(TAG, "add wallpaper " + image + " " + mRes.getIdentifier(image, "drawable", mPackageName));
                    mWallpaperList.add(wi);
                }
            }
        } while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT);
        if (DEBUG) Log.i(TAG, "loaded size = " + mWallpaperList.size());
    }

    private Intent getCropActivity() {
        final Intent cropAndSetWallpaperIntent = new Intent();
        cropAndSetWallpaperIntent.setComponent(new ComponentName("com.android.gallery3d",
                "com.android.gallery3d.filtershow.crop.CropActivity"));
        return cropAndSetWallpaperIntent;
    }

    private boolean checkCropActivity() {
        final Intent cropAndSetWallpaperIntent = getCropActivity();
        return cropAndSetWallpaperIntent.resolveActivityInfo(getPackageManager(), 0) != null;
    }
}
