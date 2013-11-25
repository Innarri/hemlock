/*
 * Copyright (C) 2012 Evergreen Open-ILS
 * @author Daniel-Octavian Rizea
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * or the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be usefull,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 * 
 */
package org.evergreen.android.views.splashscreen;

import org.evergreen.android.R;
import org.evergreen.android.accountAccess.AccountAccess;
import org.evergreen.android.globals.GlobalConfigs;
import org.evergreen.android.searchCatalog.SearchCatalogListView;
import org.evergreen.android.views.splashscreen.LoadingTask.LoadingTaskListener;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class SplashActivity extends Activity implements LoadingTaskListener {

    private TextView mProgressText;
    private Context mContext;
    private ProgressBar mProgressBar;
    private String TAG = "SplashActivity";
    private AlertDialog mAlertDialog;
    private Button mRetryButton;
    //private SharedPreferences prefs;
    private LoadingTask mTask;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        this.mContext = this;

        mProgressText = (TextView) findViewById(R.id.action_in_progress);
        mProgressBar = (ProgressBar) findViewById(R.id.activity_splash_progress_bar);
        mRetryButton = (Button) findViewById(R.id.activity_splash_retry_button);
        Log.d(TAG, "onCreate>");
        mRetryButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startTask();
            }
        });

        GlobalConfigs.httpAddress = mContext.getString(R.string.ou_library_url);
        //prefs= PreferenceManager.getDefaultSharedPreferences(context);
        //String username = prefs.getString("username", "");
        startTask();
    }
    
    protected void startTask() {
        Log.d(TAG, "startTask> task="+mTask);
        if (mTask != null)
            return;
        mTask = new LoadingTask(this, this);
        mTask.execute();
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if(mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult> "+requestCode+" "+resultCode);
    }

    private void startApp() {
        Intent intent = new Intent(SplashActivity.this, SearchCatalogListView.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onPreExecute() {
        mRetryButton.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onProgressUpdate(String value) {
        Log.d(TAG, "onProgressUpdate> "+value);
        mProgressText.setText(value);
    }

    @Override
    public void onPostExecute(String result) {
        Log.d(TAG, "onPostExecute> "+result);
        mTask = null;
        Log.d(TAG, "progressbar...gone");
        mProgressBar.setVisibility(View.GONE);
        if (TextUtils.equals(result, LoadingTask.TASK_OK)) {
            Log.d(TAG, "startApp");
            startApp();
        } else {
            String extra_text;
            if (!TextUtils.isEmpty(result))
                extra_text = "...Failed:\n" + result;
            else
                extra_text = "...Cancelled";
            Log.d(TAG, "progresstext += "+extra_text);
            mProgressText.setText(mProgressText.getText() + extra_text);
            Log.d(TAG, "retrybutton...visible");
            mRetryButton.setVisibility(View.VISIBLE);
        }
    }
}
