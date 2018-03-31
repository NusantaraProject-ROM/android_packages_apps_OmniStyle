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

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONObject;
import org.omnirom.omnistyle.HeaderUtils.DaylightHeaderInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrowseHeaderActivity extends Activity {
    private static final String TAG = "BrowseHeaderActivity";
    private static final String HEADERS_LIST_URI = "https://dl.omnirom.org/images/headers/thumbs/json_headers_xml.php";
    private static final String HEADERS_THUMB_URI = "https://dl.omnirom.org/images/headers/thumbs/";
    private static final String HEADERS_FULL_URI = "https://dl.omnirom.org/images/headers/";
    private static final String DEFAULT_CREATOR = "unkown";
    private static final String DEFAULT_TAG = "other";
    private static final boolean DEBUG = false;
    private static final int FILTER_BY_TAG = 1;

    private List<DaylightHeaderInfo> mHeadersList;
    private List<RemoteHeaderInfo> mRemoteHeadersList;
    private List<RemoteHeaderInfo> mFilterRemoteHeadersList;
    private Resources mRes;
    private String mPackageName;
    private String mHeaderName;
    private RecyclerView mHeaderListView;
    private Spinner mHeaderSelect;
    private Map<String, String> mHeaderMap;
    private List<String> mLabelList;
    private HeaderListAdapter mHeaderListAdapter;
    private RemoteHeaderListAdapter mRemoteHeaderListAdapter;
    private ProgressBar mProgress;
    private TextView mCreatorName;
    private String mCreatorLabel;
    private String mCreator;
    protected boolean mPickerMode;
    private Runnable mDoAfter;
    private Set<String> mTagList = new HashSet<>();
    private List<String> mTagSortedList = new ArrayList<>();
    private Set<String> mCreatorList = new HashSet<>();
    ArrayAdapter<CharSequence> mSelectSpinnerAdapter;
    private boolean mRemoteMode;
    private boolean mRemoteLoaded;
    private List<File> mCleanupTmpFiles = new ArrayList<>();
    private MenuItem mMenuItem;
    private boolean mReloading;

    private static final int PERMISSIONS_REQUEST_EXTERNAL_STORAGE = 0;

    private class RemoteHeaderInfo {
        public String mImage;
        public String mUri;
        public String mThumbUri;
        public String mCreator;
        public String mDisplayName;
        public String mTag;

        public boolean matches(int filterCriteria, String filterString) {
            if (filterCriteria == FILTER_BY_TAG) {
                return !TextUtils.isEmpty(mTag) && mTag.equals(filterString);
            }
            return false;
        }
    }

    public class HeaderListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.header_image, parent, false);
            return new HeaderImageHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int position) {
            final HeaderImageHolder holder = (HeaderImageHolder) h;

            DaylightHeaderInfo di = mHeadersList.get(position);
            int resId = mRes.getIdentifier(di.mImage, "drawable", mPackageName);
            if (resId != 0) {
                holder.mHeaderImage.setImageDrawable(mRes.getDrawable(resId, null));
            } else {
                holder.mHeaderImage.setImageDrawable(null);
            }
            holder.mHeaderName.setText(di.mName != null ? di.mName : di.mImage);
        }

        @Override
        public int getItemCount() {
            return mHeadersList.size();
        }
    }

    class HeaderImageHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public View rootView;
        public ImageView mHeaderImage;
        public TextView mHeaderName;

        HeaderImageHolder(View itemView) {
            super(itemView);
            rootView = itemView;
            mHeaderImage = (ImageView) itemView.findViewById(R.id.header_image);
            mHeaderName = (TextView) itemView.findViewById(R.id.header_name);
            rootView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (mReloading) {
                return;
            }
            int position = getAdapterPosition();
            if (mPickerMode) {
                DaylightHeaderInfo di = mHeadersList.get(position);
                Settings.System.putString(getContentResolver(), Settings.System.STATUS_BAR_CUSTOM_HEADER_IMAGE, mPackageName + "/" + di.mImage);
                Settings.System.putString(getContentResolver(), Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER, "static");
                Toast.makeText(BrowseHeaderActivity.this, R.string.custom_header_image_notice, Toast.LENGTH_LONG).show();
            }
        }
    }

    public class RemoteHeaderListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.header_image, parent, false);
            return new RemoteHeaderImageHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int position) {
            final RemoteHeaderImageHolder holder = (RemoteHeaderImageHolder) h;

            try {
                RemoteHeaderInfo di = mFilterRemoteHeadersList.get(position);
                Picasso.with(BrowseHeaderActivity.this).load(di.mThumbUri).into(holder.mHeaderImage);

                holder.mHeaderName.setVisibility(TextUtils.isEmpty(di.mDisplayName) ? View.GONE : View.VISIBLE);
                holder.mHeaderName.setText(di.mDisplayName);
            } catch (Exception e) {
                holder.mHeaderImage.setImageDrawable(null);
            }
        }

        @Override
        public int getItemCount() {
            return mFilterRemoteHeadersList.size();
        }
    }

    class RemoteHeaderImageHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        public View rootView;
        public ImageView mHeaderImage;
        public TextView mHeaderName;

        RemoteHeaderImageHolder(View itemView) {
            super(itemView);
            rootView = itemView;
            mHeaderImage = (ImageView) itemView.findViewById(R.id.header_image);
            mHeaderName = (TextView) itemView.findViewById(R.id.header_name);
            rootView.setOnClickListener(this);
            rootView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (mReloading) {
                return;
            }
            if (mPickerMode) {
                int position = getAdapterPosition();
                doSetRemoteHeader(position);
            }
        }

        @Override
        public boolean onLongClick(View view) {
            int position = getAdapterPosition();
            downloadRemoteHeader(position);
            return true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_browse);

        getActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);

        mProgress = (ProgressBar) findViewById(R.id.browse_progress);
        mHeaderSelect = (Spinner) findViewById(R.id.package_select);
        mCreatorName = (TextView) findViewById(R.id.meta_creator);
        mCreatorLabel = getResources().getString(R.string.header_creator_label);
        mHeaderMap = new HashMap<String, String>();
        mLabelList = new ArrayList<String>();
        getAvailableHeaderPacks(mHeaderMap);

        mSelectSpinnerAdapter = new ArrayAdapter(this,
                R.layout.spinner_item);
        mHeaderSelect.setAdapter(mSelectSpinnerAdapter);

        mHeaderSelect.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!mRemoteMode) {
                    String label = mLabelList.get(position);
                    loadHeaderPackage(mHeaderMap.get(label));
                    mCreatorName.setVisibility(TextUtils.isEmpty(mCreator) ? View.GONE : View.VISIBLE);
                    mCreatorName.setText(mCreatorLabel + mCreator);
                    mHeaderListAdapter.notifyDataSetChanged();
                } else {
                    filterRemoteWallpapers(FILTER_BY_TAG, mTagSortedList.get(position));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        mHeadersList = new ArrayList<DaylightHeaderInfo>();
        mHeaderListView = (RecyclerView) findViewById(R.id.package_images);
        layoutHeaderList(getResources().getConfiguration());

        mHeaderListView.setLayoutManager(new GridLayoutManager(this, 1));
        mHeaderListView.setHasFixedSize(false);
        mHeaderListAdapter = new HeaderListAdapter();

        loadHeaderPackage(mHeaderMap.get(mLabelList.get(0)));
        mHeaderListAdapter.notifyDataSetChanged();

        mRemoteHeadersList = new ArrayList<RemoteHeaderInfo>();
        mFilterRemoteHeadersList = new ArrayList<RemoteHeaderInfo>();
        mRemoteHeaderListAdapter = new RemoteHeaderListAdapter();

        showLocal();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (File file : mCleanupTmpFiles) {
            if (file.exists()) {
                file.delete();
            }
        }
        mCleanupTmpFiles.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.header_browse_menu, menu);
        mMenuItem = menu.findItem(R.id.header_location);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        } else if (item.getItemId() == R.id.header_location) {
            if (mRemoteMode) {
                showLocal();
            } else {
                if (isNetworkAvailable()) {
                    showRemote();
                } else {
                    Toast.makeText(BrowseHeaderActivity.this, R.string.no_network_message, Toast.LENGTH_LONG).show();
                }
            }
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        if (mMenuItem != null) {
            if (mRemoteMode) {
                mMenuItem.setTitle(getResources().getString(R.string.header_location_local));
            } else {
                mMenuItem.setTitle(getResources().getString(R.string.header_location_online));
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void getAvailableHeaderPacks(Map<String, String> headerMap) {
        Intent i = new Intent();
        PackageManager packageManager = getPackageManager();
        i.setAction("org.omnirom.DaylightHeaderPack");
        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
            String packageName = r.activityInfo.packageName;
            String label = r.activityInfo.loadLabel(getPackageManager()).toString();
            if (label == null) {
                label = r.activityInfo.packageName;
            }
            headerMap.put(label, packageName);
        }
        i.setAction("org.omnirom.DaylightHeaderPack1");
        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
            String packageName = r.activityInfo.packageName;
            String label = r.activityInfo.loadLabel(getPackageManager()).toString();
            if (label == null) {
                label = packageName;
            }
            headerMap.put(label, packageName + "/" + r.activityInfo.name);
        }
        mLabelList.addAll(headerMap.keySet());
        Collections.sort(mLabelList);
    }

    private void loadHeaderPackage(String label) {
        if (DEBUG) Log.i(TAG, "Load header pack " + label);
        mProgress.setVisibility(View.VISIBLE);
        int idx = label.indexOf("/");
        if (idx != -1) {
            String[] parts = label.split("/");
            mPackageName = parts[0];
            mHeaderName = parts[1];
        } else {
            mPackageName = label;
            mHeaderName = null;
        }
        try {
            PackageManager packageManager = getPackageManager();
            mRes = packageManager.getResourcesForApplication(mPackageName);
            mCreator = HeaderUtils.loadHeaders(mRes, mHeaderName, mHeadersList);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load header pack " + mHeaderName, e);
            mRes = null;
        }
        mProgress.setVisibility(View.GONE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        layoutHeaderList(newConfig);
        GridLayoutManager manager = (GridLayoutManager) mHeaderListView.getLayoutManager();
        manager.requestLayout();
    }

    private void layoutHeaderList(Configuration configuration) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(getResources().getDimensionPixelOffset(R.dimen.header_notification_width_landscape),
                    ViewGroup.LayoutParams.MATCH_PARENT);
            params.gravity = Gravity.CENTER_HORIZONTAL;
            mHeaderListView.setLayoutParams(params);
        } else {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            params.gravity = Gravity.CENTER_HORIZONTAL;
            mHeaderListView.setLayoutParams(params);
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


    private class FetchHeaderListTask extends AsyncTask<Void, Void, Void> {
        private boolean mError;

        @Override
        protected Void doInBackground(Void... params) {
            mReloading = true;
            mRemoteHeadersList.clear();
            mFilterRemoteHeadersList.clear();
            mTagList.clear();
            mCreatorList.clear();
            mError = false;
            List<RemoteHeaderInfo> wallpaperList = getHeadersList();
            if (wallpaperList != null) {
                mRemoteHeadersList.addAll(wallpaperList);
                mFilterRemoteHeadersList.addAll(wallpaperList);
            } else {
                mError = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void feed) {
            mProgress.setVisibility(View.GONE);
            if (mError) {
                Toast.makeText(BrowseHeaderActivity.this, R.string.download_wallpaper_list_failed_notice, Toast.LENGTH_LONG).show();
            } else {
                mRemoteLoaded = true;
                mTagSortedList.clear();
                mTagSortedList.addAll(mTagList);
                Collections.sort(mTagSortedList);
                if (!mTagList.contains(DEFAULT_TAG)) {
                    mTagSortedList.add(DEFAULT_TAG);
                }
                showRemote();
            }
            mReloading = false;
        }
    }

    private class FetchHeaderTask extends AsyncTask<String, Void, Void> {
        private String mHeaderFile;
        private boolean mDownloadOnly;

        public FetchHeaderTask(boolean downloadOnly) {
            mDownloadOnly = downloadOnly;
        }

        @Override
        protected Void doInBackground(String... params) {
            String uri = params[0];
            mHeaderFile = params[1];
            if (new File(mHeaderFile).exists()) {
                new File(mHeaderFile).delete();
            }
            NetworkUtils.downloadUrlFile(uri, new File(mHeaderFile));
            return null;
        }

        @Override
        protected void onPostExecute(Void feed) {
            mProgress.setVisibility(View.GONE);
            if (!mDownloadOnly) {
                doSetRemoteHeaderPost(mHeaderFile);
            } else {
                MediaScannerConnection.scanFile(BrowseHeaderActivity.this,
                        new String[] { mHeaderFile }, null, null);
            }
        }
    }

    private void runWithStoragePermissions(Runnable doAfter) {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            mDoAfter = doAfter;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_EXTERNAL_STORAGE);
        } else {
            doAfter.run();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_EXTERNAL_STORAGE: {
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

    private void doSetRemoteHeader(final int position) {
        runWithStoragePermissions(new Runnable() {
            @Override
            public void run() {
                try {
                    // must be unique filename
                    RemoteHeaderInfo ri = mFilterRemoteHeadersList.get(position);
                    File localHeaderFile = File.createTempFile("header_", null, getExternalCacheDir());
                    mCleanupTmpFiles.add(localHeaderFile);
                    mProgress.setVisibility(View.VISIBLE);
                    Toast.makeText(BrowseHeaderActivity.this, R.string.download_header_notice, Toast.LENGTH_SHORT).show();
                    FetchHeaderTask fetch = new FetchHeaderTask(false);
                    fetch.execute(ri.mUri, localHeaderFile.getAbsolutePath());
                } catch (IOException e){
                    Toast.makeText(BrowseHeaderActivity.this, R.string.download_header_failed_notice, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void downloadRemoteHeader(final int position) {
        runWithStoragePermissions(new Runnable() {
            @Override
            public void run() {
                // must be unique filename
                RemoteHeaderInfo ri = mFilterRemoteHeadersList.get(position);
                File localHeaderFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ri.mImage);
                mProgress.setVisibility(View.VISIBLE);
                Toast.makeText(BrowseHeaderActivity.this, R.string.download_header_notice, Toast.LENGTH_SHORT).show();
                FetchHeaderTask fetch = new FetchHeaderTask(true);
                fetch.execute(ri.mUri, localHeaderFile.getAbsolutePath());
            }
        });
    }

    private void doSetRemoteHeaderPost(String headerFile) {
        if (!new File(headerFile).exists()) {
            Toast.makeText(BrowseHeaderActivity.this, R.string.download_header_failed_notice, Toast.LENGTH_LONG).show();
            return;
        }
        final Uri imageUri = Uri.fromFile(new File(headerFile));
        Settings.System.putString(getContentResolver(), Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER, "file");
        Settings.System.putString(getContentResolver(), Settings.System.STATUS_BAR_FILE_HEADER_IMAGE, imageUri.toString());
        Toast.makeText(BrowseHeaderActivity.this, R.string.custom_header_image_notice, Toast.LENGTH_LONG).show();
    }

    private List<RemoteHeaderInfo> getHeadersList() {
        String headerData = NetworkUtils.downloadUrlMemoryAsString(HEADERS_LIST_URI);
        if (TextUtils.isEmpty(headerData)) {
            return null;
        }
        List<RemoteHeaderInfo> urlList = new ArrayList<RemoteHeaderInfo>();
        try {
            JSONArray headers = new JSONArray(headerData);
            for (int i = 0; i < headers.length(); i++) {
                JSONObject build = headers.getJSONObject(i);
                String fileName = build.getString("filename");
                String creator = null;
                if (build.has("creator")) {
                    creator = build.getString("creator");
                    mCreatorList.add(creator);
                }
                String displayName = null;
                if (build.has("name")) {
                    displayName = build.getString("name");
                }
                String tag = null;
                if (build.has("tag")) {
                    tag = build.getString("tag");
                } else {
                    tag = getDefaultTag(fileName);
                }
                mTagList.add(tag);

                if (fileName.lastIndexOf(".") != -1) {
                    String ext = fileName.substring(fileName.lastIndexOf("."));
                    if (ext.equals(".png") || ext.equals(".jpg")) {
                        RemoteHeaderInfo wi = new RemoteHeaderInfo();
                        wi.mImage = fileName;
                        wi.mThumbUri = HEADERS_THUMB_URI + fileName;
                        wi.mUri = HEADERS_FULL_URI + fileName;
                        wi.mCreator = TextUtils.isEmpty(creator) ? DEFAULT_CREATOR : creator;
                        wi.mDisplayName = TextUtils.isEmpty(displayName) ? fileName : displayName;
                        wi.mTag = tag;
                        urlList.add(wi);
                        if (DEBUG) Log.d(TAG, "add remote header = " + wi.mUri);
                    }
                }
            }
        } catch (Exception e) {
        }
        return urlList;
    }

    private String getDefaultTag(String headerName) {
        Pattern pattern = Pattern.compile("^[a-zA-Z]+");
        Matcher matcher = pattern.matcher(headerName);
        if (matcher.find()) {
            return matcher.group();
        }
        return DEFAULT_TAG;
    }

    private void showLocal() {
        mRemoteMode = false;
        mSelectSpinnerAdapter.clear();
        mSelectSpinnerAdapter.addAll(mLabelList);
        mHeaderListView.setAdapter(mHeaderListAdapter);
    }

    private void showRemote(){
        mHeaderListView.setAdapter(mRemoteHeaderListAdapter);
        if (!mRemoteLoaded) {
            mProgress.setVisibility(View.VISIBLE);
            FetchHeaderListTask fetch = new FetchHeaderListTask();
            fetch.execute();
        } else {
            mRemoteMode = true;
            mSelectSpinnerAdapter.clear();
            mSelectSpinnerAdapter.addAll(mTagSortedList);
            filterRemoteWallpapers(FILTER_BY_TAG, mTagSortedList.get(0));
        }
    }

    private void filterRemoteWallpapers(int filterCriteria, String filterString) {
        mFilterRemoteHeadersList.clear();
        for (RemoteHeaderInfo ri : mRemoteHeadersList) {
            if (ri.matches(filterCriteria, filterString)){
                mFilterRemoteHeadersList.add(ri);
            }
        }
        mRemoteHeaderListAdapter.notifyDataSetChanged();
    }
}
