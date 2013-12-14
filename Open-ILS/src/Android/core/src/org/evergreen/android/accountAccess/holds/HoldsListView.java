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
package org.evergreen.android.accountAccess.holds;

import java.util.ArrayList;
import java.util.List;

import org.evergreen.android.R;
import org.evergreen.android.accountAccess.AccountAccess;
import org.evergreen.android.accountAccess.SessionNotFoundException;
import org.evergreen.android.globals.GlobalConfigs;
import org.evergreen.android.searchCatalog.ImageDownloader;
import org.evergreen.android.searchCatalog.SearchCatalogListView;
import org.evergreen.android.views.AccountScreenDashboard;
import org.evergreen.android.views.splashscreen.SplashActivity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class HoldsListView extends Activity {

    private final String TAG = HoldsListView.class.getName();

    private AccountAccess accountAccess = null;

    private ListView lv;

    private HoldsArrayAdapter listAdapter = null;

    private List<HoldRecord> holdRecords = null;

    private Context context;

    Runnable getHoldsRunnable = null;

    private Button homeButton;

    private Button myAccountButton;

    private TextView headerTitle;

    private TextView holdsNoText;

    private ProgressDialog progressDialog;

    private ImageDownloader imageDownloader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SplashActivity.isAppInitialized()) {
            SplashActivity.restartApp(this);
            return;
        }

        setContentView(R.layout.holds_list);

        // header portion actions
        myAccountButton = (Button) findViewById(R.id.my_account_button);
        myAccountButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(),
                        AccountScreenDashboard.class);
                startActivity(intent);
            }
        });

        homeButton = (Button) findViewById(R.id.action_bar_home_button);
        homeButton.setText(R.string.hold_items_title);
        homeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(),
                        SearchCatalogListView.class);
                startActivity(intent);
            }
        });
        // end header portion actions

        holdsNoText = (TextView) findViewById(R.id.holds_number);

        imageDownloader = new ImageDownloader(40, 40, false);

        lv = (ListView) findViewById(R.id.holds_item_list);
        context = this;
        accountAccess = AccountAccess.getAccountAccess();

        holdRecords = new ArrayList<HoldRecord>();
        listAdapter = new HoldsArrayAdapter(context, R.layout.holds_list_item,
                holdRecords);
        lv.setAdapter(listAdapter);

        getHoldsRunnable = new Runnable() {
            @Override
            public void run() {

                try {
                    holdRecords = accountAccess.getHolds();
                } catch (SessionNotFoundException e) {
                    try {
                        if (accountAccess.reauthenticate(HoldsListView.this))
                            holdRecords = accountAccess.getHolds();
                    } catch (Exception eauth) {
                        Log.d(TAG, "Exception in reauth");
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listAdapter.clear();

                        for (int i = 0; i < holdRecords.size(); i++)
                            listAdapter.add(holdRecords.get(i));

                        holdsNoText.setText(" " + listAdapter.getCount());
                        progressDialog.dismiss();
                        listAdapter.notifyDataSetChanged();
                    }
                });
            }
        };

        progressDialog = new ProgressDialog(context);
        progressDialog.setMessage("Loading holds");
        progressDialog.show();

        // thread to retrieve holds
        Thread getHoldsThread = new Thread(getHoldsRunnable);
        getHoldsThread.start();

        lv.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1,
                    int position, long arg3) {
                HoldRecord record = (HoldRecord) lv.getItemAtPosition(position);

                Intent intent = new Intent(getApplicationContext(),
                        HoldDetails.class);

                intent.putExtra("holdRecord", record);

                // doae not matter request code, only result code
                startActivityForResult(intent, 0);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);

        switch (resultCode) {

        case HoldDetails.RESULT_CODE_CANCEL:
            // nothing
            Log.d(TAG, "Do nothing");
            break;

        case HoldDetails.RESULT_CODE_DELETE_HOLD:
        case HoldDetails.RESULT_CODE_UPDATE_HOLD:
            // refresh ui
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage("Loading holds");
            progressDialog.show();
            // thread to retrieve holds
            Thread getHoldsThread = new Thread(getHoldsRunnable);
            getHoldsThread.start();
            Log.d(TAG, "Update on result "+resultCode);
            break;
        }
    }

    class HoldsArrayAdapter extends ArrayAdapter<HoldRecord> {
        private static final String tag = "CheckoutArrayAdapter";

        private TextView holdTitle;
        private TextView holdAuthor;
        private TextView status;
        private ImageView hold_icon;

        private List<HoldRecord> records = new ArrayList<HoldRecord>();

        public HoldsArrayAdapter(Context context, int textViewResourceId,
                List<HoldRecord> objects) {
            super(context, textViewResourceId, objects);
            this.records = objects;
        }

        public int getCount() {
            return this.records.size();
        }

        public HoldRecord getItem(int index) {
            return this.records.get(index);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;

            // Get item
            final HoldRecord record = getItem(position);

            if (row == null) {

                Log.d(tag, "Starting XML view more infaltion ... ");
                LayoutInflater inflater = (LayoutInflater) this.getContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                row = inflater.inflate(R.layout.holds_list_item, parent, false);
                Log.d(tag, "Successfully completed XML view more Inflation!");

            }

            hold_icon = (ImageView) row.findViewById(R.id.hold_resource_icon);

            // Get reference to TextView - title
            holdTitle = (TextView) row.findViewById(R.id.hold_title);

            // Get reference to TextView author
            holdAuthor = (TextView) row.findViewById(R.id.hold_author);

            // Get hold status
            status = (TextView) row.findViewById(R.id.hold_status);

            // set text
            String imageResourceHref = GlobalConfigs.httpAddress
                    + GlobalConfigs.hold_icon_address
                    + record.types_of_resource + ".jpg";

            if (imageResourceHref.contains(" ")) {
                imageResourceHref = imageResourceHref.replace(" ", "%20");
            }

            imageDownloader.download(imageResourceHref, hold_icon);

            // set raw information
            holdTitle.setText(record.title);
            holdAuthor.setText(record.author);
            status.setText(record.getHoldStatus(getResources()));

            return row;
        }
    }
}
