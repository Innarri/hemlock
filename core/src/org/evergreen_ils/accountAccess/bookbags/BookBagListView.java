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
package org.evergreen_ils.accountAccess.bookbags;

import java.util.ArrayList;
import java.util.List;

import android.support.v7.app.ActionBarActivity;
import android.view.WindowManager;
import org.evergreen_ils.R;
import org.evergreen_ils.accountAccess.AccountAccess;
import org.evergreen_ils.accountAccess.SessionNotFoundException;
import org.evergreen_ils.globals.Log;
import org.evergreen_ils.globals.Utils;
import org.evergreen_ils.utils.ui.ActionBarUtils;
import org.evergreen_ils.views.splashscreen.SplashActivity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class BookBagListView extends ActionBarActivity {

    private final static String TAG = BookBagListView.class.getSimpleName();

    private AccountAccess accountAccess = null;

    private ListView lv;

    private BookBagsArrayAdapter listAdapter = null;

    private ArrayList<BookBag> bookBags = null;

    private Context context;

    private ProgressDialog progressDialog;

    private EditText bookbag_name;

    private Button create_bookbag;

    private Runnable getBookbagsRunnable;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SplashActivity.isAppInitialized()) {
            SplashActivity.restartApp(this);
            return;
        }

        setContentView(R.layout.bookbag_list);
        ActionBarUtils.initActionBarForActivity(this);

        // prevent soft keyboard from popping up when the activity starts
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        context = this;
        accountAccess = AccountAccess.getInstance();

        bookbag_name = (EditText) findViewById(R.id.bookbag_create_name);
        create_bookbag = (Button) findViewById(R.id.bookbag_create_button);
        create_bookbag.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                createBookbag(bookbag_name.getText().toString());
            }
        });

        lv = (ListView) findViewById(R.id.bookbag_list);
        bookBags = new ArrayList<BookBag>();
        listAdapter = new BookBagsArrayAdapter(context, R.layout.bookbag_list_item, bookBags);
        lv.setAdapter(listAdapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BookBag item = (BookBag) lv.getItemAtPosition(position);
                Intent intent = new Intent(context, BookBagDetails.class);
                intent.putExtra("bookBag", item);
                startActivityForResult(intent, 0);
            }
        });

        initGetBookbagsRunnable();

        new Thread(getBookbagsRunnable).start();
    }

    private void showProgressDialog(CharSequence msg) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage(msg);
        }
        progressDialog.show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        dismissProgressDialog();
        super.onDestroy();
    }

    private void initGetBookbagsRunnable() {
        getBookbagsRunnable = new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showProgressDialog(getString(R.string.msg_retrieving_lists));
                    }
                });

                try {
                    accountAccess.retrieveBookbags();
                } catch (SessionNotFoundException e) {
                    try {
                        if (accountAccess.reauthenticate(BookBagListView.this))
                            accountAccess.retrieveBookbags();
                    } catch (Exception e2) {
                        Log.d(TAG, "caught", e2);
                    }
                }
                bookBags = accountAccess.getBookbags();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listAdapter.clear();
                        for (int i = 0; i < bookBags.size(); i++)
                            listAdapter.add(bookBags.get(i));

                        dismissProgressDialog();

                        if (bookBags.size() == 0)
                            Toast.makeText(context, getText(R.string.msg_no_lists), Toast.LENGTH_LONG).show();

                        listAdapter.notifyDataSetChanged();
                    }
                });
            }
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (resultCode) {
        case BookBagDetails.RESULT_CODE_UPDATE:
            new Thread(getBookbagsRunnable).start();
            break;
        }
    }

    private void createBookbag(final String name) {
        if (name.length() < 2) {
            Toast.makeText(context,
                    R.string.msg_list_name_too_short,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    accountAccess.createBookbag(name);
                } catch (SessionNotFoundException e) {
                    try {
                        if (accountAccess.reauthenticate(BookBagListView.this))
                            accountAccess.createBookbag(name);
                    } catch (Exception eauth) {
                        Log.d(TAG, "caught", eauth);
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dismissProgressDialog();
                    }
                });

                new Thread(getBookbagsRunnable).start();
            }
        });

        showProgressDialog(getString(R.string.msg_creating_list));
        thread.start();
    }

    class BookBagsArrayAdapter extends ArrayAdapter<BookBag> {
        private List<BookBag> records = new ArrayList<BookBag>();

        public BookBagsArrayAdapter(Context context, int textViewResourceId, List<BookBag> objects) {
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
                LayoutInflater inflater = (LayoutInflater) this.getContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                row = inflater.inflate(R.layout.bookbag_list_item, parent, false);
            }

            TextView nameText = (TextView) row.findViewById(R.id.bookbag_name);
            nameText.setText(Utils.safeString(record.name));
            TextView descText = (TextView) row.findViewById(R.id.bookbag_description);
            descText.setText(Utils.safeString(record.description));
            TextView itemsText = (TextView) row.findViewById(R.id.bookbag_items);
            itemsText.setText(getResources().getQuantityString(R.plurals.number_of_items,
                    record.items.size(), record.items.size()));

            return row;
        }
    }
}