/*
 * Copyright 2011 Uwe Trottmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.battlelancer.seriesguide.ui;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;

import com.actionbarsherlock.app.ActionBar;
import com.battlelancer.seriesguide.items.Series;
import com.battlelancer.seriesguide.util.DBUtils;
import com.battlelancer.seriesguide.util.TaskManager;
import com.battlelancer.seriesguide.util.UpdateTask;
import com.battlelancer.seriesguide.util.Utils;
import com.battlelancer.thetvdbapi.TheTVDB;
import com.google.analytics.tracking.android.EasyTracker;
import com.uwetrottmann.androidutils.AndroidUtils;
import com.uwetrottmann.seriesguide.R;

import java.nio.charset.Charset;

/**
 * Hosts an {@link OverviewFragment}.
 */
public class OverviewActivity extends BaseActivity implements CreateNdefMessageCallback {

    private Fragment mFragment;
    private NfcAdapter mNfcAdapter;
    private int mShowId;

    @Override
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.overview);

        mShowId = getIntent().getIntExtra(OverviewFragment.InitBundle.SHOW_TVDBID, -1);
        if (mShowId == -1) {
            finish();
            return;
        }

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(getString(R.string.description_overview));
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            mFragment = new OverviewFragment();
            mFragment.setArguments(getIntent().getExtras());

            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
            ft.replace(R.id.fragment_overview, mFragment).commit();
        }

        if (AndroidUtils.isICSOrHigher()) {
            // register for Android Beam
            mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (mNfcAdapter != null) {
                mNfcAdapter.setNdefPushMessageCallback(this, this);
            }
        }

        // try to update this show
        onUpdate();
    }

    @Override
    protected void onStart() {
        super.onStart();
        EasyTracker.getInstance().activityStart(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        EasyTracker.getInstance().activityStop(this);
    }

    private int getShowId() {
        return getIntent().getIntExtra(OverviewFragment.InitBundle.SHOW_TVDBID, -1);
    }

    private void onUpdate() {
        // only update this show if no global update is running and we have a
        // connection
        if (!TaskManager.getInstance(this).isUpdateTaskRunning(false)
                && Utils.isAllowedConnection(this)) {
            final SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(getApplicationContext());

            // check if auto-update is enabled
            final boolean isAutoUpdateEnabled = prefs.getBoolean(
                    SeriesGuidePreferences.KEY_AUTOUPDATE, true);
            if (isAutoUpdateEnabled) {
                String showId = String.valueOf(mShowId);
                boolean isTime = TheTVDB.isUpdateShow(showId, System.currentTimeMillis(), this);

                // look if we need to update
                if (isTime) {
                    UpdateTask updateTask = new UpdateTask(showId, this);
                    TaskManager.getInstance(this).tryUpdateTask(updateTask, false, -1);
                }
            }

        }
    }

    @Override
    public boolean onSearchRequested() {
        // refine search with the show's title
        int showId = getShowId();
        if (showId == -1) {
            return false;
        }

        final Series show = DBUtils.getShow(this, String.valueOf(showId));
        final String showTitle = show.getTitle();

        Bundle args = new Bundle();
        args.putString(SearchFragment.InitBundle.SHOW_TITLE, showTitle);
        startSearch(null, false, args, false);
        return true;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        NdefMessage msg = new NdefMessage(new NdefRecord[] {
                createMimeRecord(
                        "application/com.battlelancer.seriesguide.beam", String.valueOf(mShowId)
                                .getBytes())
        });
        return msg;
    }

    /**
     * Creates a custom MIME type encapsulated in an NDEF record
     * 
     * @param mimeType
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public NdefRecord createMimeRecord(String mimeType, byte[] payload) {
        byte[] mimeBytes = mimeType.getBytes(Charset.forName("US-ASCII"));
        NdefRecord mimeRecord = new NdefRecord(
                NdefRecord.TNF_MIME_MEDIA, mimeBytes, new byte[0], payload);
        return mimeRecord;
    }
}
