/*
 * Copyright (c) 2020 Kenneth H. Cox
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.evergreen_ils.views.bookbags;

import java.util.ArrayList;
import java.util.List;

import android.view.WindowManager;
import org.evergreen_ils.R;
import org.evergreen_ils.accountAccess.AccountAccess;
import org.evergreen_ils.accountAccess.SessionNotFoundException;
import org.evergreen_ils.accountAccess.bookbags.BookBag;
import org.evergreen_ils.android.Analytics;
import org.evergreen_ils.android.Log;
import org.evergreen_ils.utils.StringUtils;
import org.evergreen_ils.utils.ui.BaseActivity;
import org.evergreen_ils.utils.ui.ProgressDialogSupport;

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

public class BookBagsActivity extends BaseActivity {

    private final static String TAG = BookBagsActivity.class.getSimpleName();

    private AccountAccess accountAccess = null;

    private ListView lv;

    private BookBagsArrayAdapter listAdapter = null;

    private ArrayList<BookBag> bookBags = null;

    private ProgressDialogSupport progress;

    private EditText bookbag_name;

    private Button create_bookbag;

    private Runnable getBookbagsRunnable;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mRestarting) return;

        setContentView(R.layout.activity_bookbags);

        // prevent soft keyboard from popping up when the activity starts
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        accountAccess = AccountAccess.getInstance();
        progress = new ProgressDialogSupport();

        bookbag_name = findViewById(R.id.bookbag_create_name);
        create_bookbag = findViewById(R.id.bookbag_create_button);
        create_bookbag.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                createBookbag(bookbag_name.getText().toString());
            }
        });

        lv = findViewById(R.id.bookbag_list);
        bookBags = new ArrayList<BookBag>();
        listAdapter = new BookBagsArrayAdapter(this, R.layout.bookbag_list_item, bookBags);
        lv.setAdapter(listAdapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Analytics.logEvent("Lists: Tap List");
                BookBag item = (BookBag) lv.getItemAtPosition(position);
                Intent intent = new Intent(BookBagsActivity.this, BookBagDetailsActivity.class);
                intent.putExtra("bookBag", item);
                startActivityForResult(intent, 0);
            }
        });

        initGetBookbagsRunnable();

        new Thread(getBookbagsRunnable).start();
    }

    @Override
    protected void onDestroy() {
        if (progress != null) progress.dismiss();
        super.onDestroy();
    }

    private void initGetBookbagsRunnable() {
        getBookbagsRunnable = new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) progress.show(BookBagsActivity.this, getString(R.string.msg_retrieving_lists));
                    }
                });

                try {
                    accountAccess.retrieveBookbags();
                } catch (SessionNotFoundException e) {
                    try {
                        if (accountAccess.reauthenticate(BookBagsActivity.this))
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
                        for (BookBag bookBag : bookBags)
                            listAdapter.add(bookBag);

                        listAdapter.notifyDataSetChanged();

                        progress.dismiss();
                    }
                });
            }
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (resultCode) {
        case BookBagDetailsActivity.RESULT_CODE_UPDATE:
            new Thread(getBookbagsRunnable).start();
            break;
        }
    }

    private void createBookbag(final String bookBagName) {
        String name = bookBagName.trim();
        if (name.isEmpty()) {
            bookbag_name.setError(getString(R.string.error_list_name_empty));
            return;
        }
        Analytics.logEvent("Lists: Create List");

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    accountAccess.createBookbag(name);
                } catch (SessionNotFoundException e) {
                    try {
                        if (accountAccess.reauthenticate(BookBagsActivity.this))
                            accountAccess.createBookbag(name);
                    } catch (Exception eauth) {
                        Log.d(TAG, "caught", eauth);
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.dismiss();
                    }
                });

                new Thread(getBookbagsRunnable).start();
            }
        });

        progress.show(this, getString(R.string.msg_creating_list));
        thread.start();
    }

    class BookBagsArrayAdapter extends ArrayAdapter<BookBag> {
        private List<BookBag> records;

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
            nameText.setText(StringUtils.safeString(record.name));
            TextView descText = (TextView) row.findViewById(R.id.bookbag_description);
            descText.setText(StringUtils.safeString(record.description));
            TextView itemsText = (TextView) row.findViewById(R.id.bookbag_items);
            itemsText.setText(getResources().getQuantityString(R.plurals.number_of_items,
                    record.items.size(), record.items.size()));

            return row;
        }
    }
}
