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

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public class BrowseWallsFilterActivity extends Activity {
    private static final String TAG = "BrowseWallsActivity";
    private static final String IMAGE_TYPE = "image/*";
    private static final int IMAGE_CROP_AND_SET = 1;
    private static final String WALLPAPER_LIST_URI = "https://dl.omnirom.org/images/wallpapers/thumbs/json_wallpapers_xml.php";
    private static final String WALLPAPER_THUMB_URI = "https://dl.omnirom.org/images/wallpapers/thumbs/";
    private static final String WALLPAPER_FULL_URI = "https://dl.omnirom.org/images/wallpapers/";
    private static final String EMPTY_CREATOR = "ZZZ";

    private static final boolean DEBUG = false;
    private List<RemoteWallpaperInfo> mWallpaperUrlList;
    private RecyclerView mWallpaperView;
    private WallpaperRemoteListAdapter mAdapterRemote;
    private Runnable mDoAfter;
    private TextView mNoNetworkMessage;
    private ProgressBar mProgressBar;
    private String mFilterTag;

    private static final int HTTP_READ_TIMEOUT = 30000;
    private static final int HTTP_CONNECTION_TIMEOUT = 30000;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 0;

    private class RemoteWallpaperInfo {
        public String mImage;
        public String mUri;
        public String mThumbUri;
        public String mCreator;
        public String mDisplayName;
        public String mTag;
    }

    class ImageHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public View rootView;
        public ImageView mWallpaperImage;
        public TextView mWallpaperName;
        public TextView mWallpaperCreator;

        ImageHolder(View itemView) {
            super(itemView);
            rootView = itemView;
            mWallpaperImage = (ImageView) itemView.findViewById(R.id.wallpaper_image);
            mWallpaperName = (TextView) itemView.findViewById(R.id.wallpaper_name);
            mWallpaperCreator = (TextView) itemView.findViewById(R.id.wallpaper_creator);
            rootView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getAdapterPosition();
            if (!checkCropActivity()) {
                AlertDialog.Builder noCropActivityDialog = new AlertDialog.Builder(BrowseWallsFilterActivity.this);
                noCropActivityDialog.setMessage(getResources().getString(R.string.no_crop_activity_dialog_text));
                noCropActivityDialog.setTitle(getResources().getString(R.string.no_crop_activity_dialog_title));
                noCropActivityDialog.setCancelable(false);
                noCropActivityDialog.setPositiveButton(android.R.string.ok, null);
                AlertDialog d = noCropActivityDialog.create();
                d.show();
                return;
            }
            doSetRemoteWallpaper(position);
        }
    }

    public class WallpaperRemoteListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.wallpaper_image, parent, false);
            return new ImageHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int position) {
            final ImageHolder holder = (ImageHolder) h;

            try {
                RemoteWallpaperInfo wi = mWallpaperUrlList.get(position);
                Picasso.with(BrowseWallsFilterActivity.this).load(wi.mThumbUri).into(holder.mWallpaperImage);

                holder.mWallpaperName.setVisibility(TextUtils.isEmpty(wi.mDisplayName) ? View.GONE : View.VISIBLE);
                holder.mWallpaperName.setText(wi.mDisplayName);
                holder.mWallpaperCreator.setVisibility(TextUtils.isEmpty(wi.mCreator) ||  wi.mCreator.equals(EMPTY_CREATOR)? View.GONE : View.VISIBLE);
                holder.mWallpaperCreator.setText(wi.mCreator);
            } catch (Exception e) {
                holder.mWallpaperImage.setImageDrawable(null);
            }
        }

        @Override
        public int getItemCount() {
            return mWallpaperUrlList.size();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mFilterTag = "theme";
        if (intent.hasExtra("filter")) {
            mFilterTag = intent.getStringExtra("filter");
        }
        setContentView(R.layout.content_wallpapers_filter);
        getActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);

        mWallpaperUrlList = new ArrayList<RemoteWallpaperInfo>();
        mNoNetworkMessage = (TextView) findViewById(R.id.no_network_message);
        mProgressBar = (ProgressBar) findViewById(R.id.browse_progress);

        try {
            mWallpaperView = (RecyclerView) findViewById(R.id.wallpaper_images);
            mWallpaperView.setLayoutManager(new GridLayoutManager(this, 2));
            mWallpaperView.setHasFixedSize(false);

            mAdapterRemote = new WallpaperRemoteListAdapter();

            if (isNetworkAvailable()) {
                mNoNetworkMessage.setVisibility(View.GONE);
                mWallpaperView.setAdapter(mAdapterRemote);
                mProgressBar.setVisibility(View.VISIBLE);
                FetchWallpaperListTask fetch = new FetchWallpaperListTask();
                fetch.execute();
            } else {
                mNoNetworkMessage.setVisibility(View.VISIBLE);
            }

            mAdapterRemote.notifyDataSetChanged();
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

    private void doCallCropActivity(Uri imageUri, Point dispSize, int wpWidth, int wpHeight) {
        float spotlightX = (float) dispSize.x / wpWidth;
        float spotlightY = (float) dispSize.y / wpHeight;

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

        AlertDialog.Builder wallpaperTypeDialog = new AlertDialog.Builder(BrowseWallsFilterActivity.this);
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

    private HttpsURLConnection setupHttpsRequest(String urlStr) {
        URL url;
        HttpsURLConnection urlConnection = null;
        try {
            url = new URL(urlStr);
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(HTTP_CONNECTION_TIMEOUT);
            urlConnection.setReadTimeout(HTTP_READ_TIMEOUT);
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoInput(true);
            urlConnection.connect();
            int code = urlConnection.getResponseCode();
            if (code != HttpsURLConnection.HTTP_OK) {
                Log.d(TAG, "response:" + code);
                return null;
            }
            return urlConnection;
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to server", e);
            return null;
        }
    }

    private String downloadUrlMemoryAsString(String url) {
        if (DEBUG) Log.d(TAG, "download: " + url);

        HttpsURLConnection urlConnection = null;
        try {
            urlConnection = setupHttpsRequest(url);
            if (urlConnection == null) {
                return null;
            }

            InputStream is = urlConnection.getInputStream();
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            int byteInt;

            while ((byteInt = is.read()) >= 0) {
                byteArray.write(byteInt);
            }

            byte[] bytes = byteArray.toByteArray();
            if (bytes == null) {
                return null;
            }
            String responseBody = new String(bytes, StandardCharsets.UTF_8);

            return responseBody;
        } catch (Exception e) {
            // Download failed for any number of reasons, timeouts, connection
            // drops, etc. Just log it in debugging mode.
            Log.e(TAG, "", e);
            return null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private List<RemoteWallpaperInfo> getWallpaperList() {
        String wallData = downloadUrlMemoryAsString(WALLPAPER_LIST_URI);
        if (TextUtils.isEmpty(wallData)) {
            return null;
        }
        List<RemoteWallpaperInfo> urlList = new ArrayList<RemoteWallpaperInfo>();
        try {
            JSONArray walls = new JSONArray(wallData);
            for (int i = 0; i < walls.length(); i++) {
                JSONObject build = walls.getJSONObject(i);
                String fileName = build.getString("filename");
                String creator = null;
                if (build.has("creator")) {
                    creator = build.getString("creator");
                }
                String displayName = null;
                if (build.has("name")) {
                    displayName = build.getString("name");
                }
                String tag = null;
                if (build.has("tag")) {
                    tag = build.getString("tag");
                }
                if (!TextUtils.isEmpty(mFilterTag)) {
                    if (TextUtils.isEmpty(tag) || !tag.equals(mFilterTag)) {
                        continue;
                    }
                }
                if (fileName.lastIndexOf(".") != -1) {
                    String ext = fileName.substring(fileName.lastIndexOf("."));
                    if (ext.equals(".png") || ext.equals(".jpg")) {
                        RemoteWallpaperInfo wi = new RemoteWallpaperInfo();
                        wi.mImage = fileName;
                        wi.mThumbUri = WALLPAPER_THUMB_URI + fileName;
                        wi.mUri = WALLPAPER_FULL_URI + fileName;
                        wi.mCreator = TextUtils.isEmpty(creator) ? EMPTY_CREATOR : creator;
                        wi.mDisplayName = TextUtils.isEmpty(displayName) ? "" : displayName;
                        wi.mTag = TextUtils.isEmpty(tag) ? "" : tag;
                        urlList.add(wi);
                        if (DEBUG) Log.d(TAG, "add remote wallpaper = " + wi.mUri);
                    }
                }
            }
        } catch (Exception e) {
        }
        return urlList;
    }

    private boolean downloadUrlFile(String url, File f) {
        if (DEBUG) Log.d(TAG, "download:" + url);

        HttpsURLConnection urlConnection = null;

        if (f.exists())
            f.delete();

        try {
            urlConnection = setupHttpsRequest(url);
            if (urlConnection == null) {
                return false;
            }
            long len = urlConnection.getContentLength();
            if ((len > 0) && (len < 4L * 1024L * 1024L * 1024L)) {
                byte[] buffer = new byte[262144];

                InputStream is = urlConnection.getInputStream();
                FileOutputStream os = new FileOutputStream(f, false);
                try {
                    int r;
                    while ((r = is.read(buffer)) > 0) {
                        os.write(buffer, 0, r);
                    }
                } finally {
                    os.close();
                }

                return true;
            }
            return false;
        } catch (Exception e) {
            // Download failed for any number of reasons, timeouts, connection
            // drops, etc. Just log it in debugging mode.
            Log.e(TAG, "", e);
            return false;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private class FetchWallpaperListTask extends AsyncTask<Void, Void, Void> {
        private boolean mError;

        @Override
        protected Void doInBackground(Void... params) {
            mWallpaperUrlList.clear();
            mError = false;
            List<RemoteWallpaperInfo> wallpaperList = getWallpaperList();
            if (wallpaperList != null) {
                mWallpaperUrlList.addAll(wallpaperList);
            } else {
                mError = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void feed) {
            mProgressBar.setVisibility(View.GONE);
            if (mError) {
                Toast.makeText(BrowseWallsFilterActivity.this, R.string.download_wallpaper_list_failed_notice, Toast.LENGTH_LONG).show();
            }
            mAdapterRemote.notifyDataSetChanged();
        }
    }

    private class FetchWallpaperTask extends AsyncTask<String, Void, Void> {
        private String mWallpaperFile;

        @Override
        protected Void doInBackground(String... params) {
            String uri = params[0];
            mWallpaperFile = params[1];
            if (new File(mWallpaperFile).exists()) {
                new File(mWallpaperFile).delete();
            }
            downloadUrlFile(uri, new File(mWallpaperFile));
            return null;
        }

        @Override
        protected void onPostExecute(Void feed) {
            mProgressBar.setVisibility(View.GONE);
            doSetRemoteWallpaperPost(mWallpaperFile);
        }
    }

    private void runWithStoragePermissions(Runnable doAfter) {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            mDoAfter = doAfter;
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            doAfter.run();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (mDoAfter != null) {
                        mDoAfter.run();
                        mDoAfter = null;
                    }
                }
            }
        }
    }

    private void doSetRemoteWallpaper(final int position) {
        runWithStoragePermissions(new Runnable() {
            @Override
            public void run() {
                // no need to save for later - just always overwrite
                String fileName = "tmp_wallpaper";
                RemoteWallpaperInfo ri = mWallpaperUrlList.get(position);
                File localWallpaperFile = new File(getExternalCacheDir(), fileName);
                mProgressBar.setVisibility(View.VISIBLE);
                Toast.makeText(BrowseWallsFilterActivity.this, R.string.download_wallpaper_notice, Toast.LENGTH_SHORT).show();
                FetchWallpaperTask fetch = new FetchWallpaperTask();
                fetch.execute(ri.mUri, localWallpaperFile.getAbsolutePath());
            }
        });
    }

    private void doSetRemoteWallpaperPost(String wallpaperFile) {
        if (!new File(wallpaperFile).exists()) {
            Toast.makeText(BrowseWallsFilterActivity.this, R.string.download_wallpaper_failed_notice, Toast.LENGTH_LONG).show();
            return;
        }
        WallpaperManager wpm = WallpaperManager.getInstance(getApplicationContext());
        final int wpWidth = wpm.getDesiredMinimumWidth();
        final int wpHeight = wpm.getDesiredMinimumHeight();
        Display disp = getWindowManager().getDefaultDisplay();
        final Point dispSize = new Point();
        disp.getRealSize(dispSize);

        Bitmap image = BitmapFactory.decodeFile(wallpaperFile);
        if (image == null) {
            Toast.makeText(BrowseWallsFilterActivity.this, R.string.download_wallpaper_failed_notice, Toast.LENGTH_LONG).show();
            return;
        }
        final Uri uri = Uri.fromFile(new File(wallpaperFile));
        if (DEBUG) Log.d(TAG, "crop uri = " + uri);

        // if that image ratio is close to the display size ratio
        // assume this wall is meant to be fullscreen without scrolling
        float displayRatio = (float) Math.round(((float) dispSize.x / dispSize.y) * 10) / 10;
        float imageRatio = (float) Math.round(((float) image.getWidth() / image.getHeight()) * 10) / 10;
        if (displayRatio != imageRatio) {
            // ask if scrolling wallpaper should be used original size
            // or if it should be cropped to image size
            AlertDialog.Builder scrollingWallDialog = new AlertDialog.Builder(BrowseWallsFilterActivity.this);
            scrollingWallDialog.setMessage(getResources().getString(R.string.scrolling_wall_dialog_text));
            scrollingWallDialog.setTitle(getResources().getString(R.string.scrolling_wall_dialog_title));
            scrollingWallDialog.setCancelable(false);
            scrollingWallDialog.setPositiveButton(R.string.scrolling_wall_yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    doCallCropActivity(uri, dispSize, wpWidth, wpHeight);
                }
            });
            scrollingWallDialog.setNegativeButton(R.string.scrolling_wall_no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    doCallCropActivity(uri, dispSize, dispSize.x, dispSize.y);
                }
            });
            AlertDialog d = scrollingWallDialog.create();
            d.show();
        } else {
            doCallCropActivity(uri, dispSize, dispSize.x, dispSize.y);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        if (activeNetwork != null && activeNetwork.isConnected()) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int spanCount = getResources().getInteger(R.integer.wallpapers_column_count);
        GridLayoutManager manager = (GridLayoutManager) mWallpaperView.getLayoutManager();
        manager.setSpanCount(spanCount);
        manager.requestLayout();
    }
}

