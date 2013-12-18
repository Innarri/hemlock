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
package org.evergreen.android.accountAccess.bookbags;

import java.util.ArrayList;
import java.util.List;

import org.evergreen.android.R;
import org.evergreen.android.accountAccess.AccountAccess;
import org.evergreen.android.accountAccess.SessionNotFoundException;
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
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class BookbagsListView extends Activity {

    private final String TAG = BookbagsListView.class.getName();

    private AccountAccess accountAccess = null;

    private ListView lv;

    private BookBagsArrayAdapter listAdapter = null;

    private ArrayList<BookBag> bookBags = null;

    private Context context;

    private ProgressDialog progressDialog;

    private EditText bookbag_name;

    private Button create_bookbag;

    private Runnable getBookbagsRunnable;

    private Button homeButton;

    private Button myAccountButton;

    private TextView headerTitle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SplashActivity.isAppInitialized()) {
            SplashActivity.restartApp(this);
            return;
        }

        setContentView(R.layout.bookbag_list);

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
        homeButton.setText(R.string.bookbag_items_title);
        homeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(),
                        SearchCatalogListView.class);
                startActivity(intent);
            }
        });
        // end header portion actions

        context = this;
        accountAccess = AccountAccess.getAccountAccess();

        bookbag_name = (EditText) findViewById(R.id.bookbag_create_name);
        create_bookbag = (Button) findViewById(R.id.bookbag_create_button);
        lv = (ListView) findViewById(R.id.bookbag_list);
        bookBags = new ArrayList<BookBag>();
        listAdapter = new BookBagsArrayAdapter(context,
                R.layout.bookbag_list_item, bookBags);
        lv.setAdapter(listAdapter);

        lv.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1,
                    int position, long arg3) {

                Toast.makeText(getApplicationContext(), "Text",
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

            }

        });
        create_bookbag.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

            final String name = bookbag_name.getText().toString();

            Thread createBookbag = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (name.length() > 1) {
                        try {
                            accountAccess.createBookbag(name);
                        } catch (SessionNotFoundException e) {
                            try {
                                if (accountAccess.reauthenticate(BookbagsListView.this))
                                    accountAccess.createBookbag(name);
                            } catch (Exception eauth) {
                                Log.d(TAG, "Exception in reAuth");
                            }
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.dismiss();
                            }
                        });

                        Thread getBookBags = new Thread(getBookbagsRunnable);
                        getBookBags.start();
                    }
                }
            });

            if (name.length() > 1) {
                progressDialog = ProgressDialog.show(context,
                        getResources().getText(R.string.dialog_please_wait),
                        "Creating Bookbag");
                createBookbag.start();
            } else
                Toast.makeText(context,
                        "Bookbag name must be at least 2 characters long",
                        Toast.LENGTH_SHORT).show();
            }
        });

        getBookbagsRunnable = new Runnable() {

            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog = ProgressDialog.show(context,
                                getResources().getText(R.string.dialog_please_wait),
                                "Retrieving Bookbag data");
                    }
                });

                try {
                    accountAccess.retrieveBookbags();
                } catch (SessionNotFoundException e) {
                    try {
                        if (accountAccess.reauthenticate(BookbagsListView.this))
                            accountAccess.retrieveBookbags();
                    } catch (Exception eauth) {
                        Log.d(TAG, "Exception in reAuth");
                    }
                }
                bookBags = accountAccess.getBookbags();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        listAdapter.clear();
                        for (int i = 0; i < bookBags.size(); i++)
                            listAdapter.add(bookBags.get(i));

                        progressDialog.dismiss();

                        if (bookBags.size() == 0)
                            Toast.makeText(context, "No data",
                                    Toast.LENGTH_LONG);

                        listAdapter.notifyDataSetChanged();
                    }
                });

            }
        };

        new Thread(getBookbagsRunnable).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);

        switch (resultCode) {

        case BookBagDetails.RESULT_CODE_UPDATE: {
            Thread getBookBags = new Thread(getBookbagsRunnable);
            getBookBags.start();
        }
            break;

        }
    }

    class BookBagsArrayAdapter extends ArrayAdapter<BookBag> {
        private static final String tag = "BookbagArrayAdapter";

        private TextView name;
        private TextView items;
        private CheckBox shared;
        private Button detailsButton;

        private List<BookBag> records = new ArrayList<BookBag>();

        public BookBagsArrayAdapter(Context context, int textViewResourceId,
                List<BookBag> objects) {
            super(context, textViewResourceId, objects);
            this.records = objects;
        }

        public int getCount() {
            return this.records.size();
        }

        public BookBag getItem(int index) {
            return this.records.get(index);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;

            // Get item
            final BookBag record = getItem(position);

            // if it is the right type of view
            if (row == null) {

                Log.d(tag, "Starting XML Row Inflation ... ");
                LayoutInflater inflater = (LayoutInflater) this.getContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                row = inflater.inflate(R.layout.bookbag_list_item, parent,
                        false);
                Log.d(tag, "Successfully completed XML Row Inflation!");

            }

            name = (TextView) row.findViewById(R.id.bookbag_name);

            items = (TextView) row.findViewById(R.id.bookbag_items);

            shared = (CheckBox) row.findViewById(R.id.bookbag_shared);

            detailsButton = (Button) row.findViewById(R.id.details_button);

            name.setText(record.name + "");

            items.setText(record.items.size() + "");

            shared.setChecked(record.shared);

            detailsButton.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(context, BookBagDetails.class);
                    intent.putExtra("bookBag", record);
                    startActivityForResult(intent, 0);

                }
            });

            return row;
        }
    }
}
