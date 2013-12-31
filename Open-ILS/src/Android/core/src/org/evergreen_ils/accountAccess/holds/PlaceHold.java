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
package org.evergreen_ils.accountAccess.holds;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.MenuItem;
import org.evergreen_ils.R;
import org.evergreen_ils.accountAccess.AccountAccess;
import org.evergreen_ils.accountAccess.SessionNotFoundException;
import org.evergreen_ils.globals.GlobalConfigs;
import org.evergreen_ils.searchCatalog.RecordInfo;
import org.evergreen_ils.searchCatalog.SearchCatalogListView;
import org.evergreen_ils.views.AccountScreenDashboard;
import org.evergreen_ils.views.splashscreen.SplashActivity;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class PlaceHold extends ActionBarActivity {

    private final String TAG = PlaceHold.class.getName();

    private TextView recipient;

    private TextView title;

    private TextView author;

    private TextView physical_description;

    private TextView screen_title;

    private AccountAccess accountAccess;

    private EditText expiration_date;

    private EditText phone_number;

    private CheckBox phone_notification;

    private CheckBox email_notification;

    private Button placeHold;

    private Button cancel;

    private CheckBox suspendHold;

    private Spinner orgSelector;

    private DatePickerDialog datePicker = null;

    private DatePickerDialog thaw_datePicker = null;

    private EditText thaw_date_edittext;

    private Date expire_date = null;

    private Date thaw_date = null;

    private Runnable placeHoldRunnable;

    private GlobalConfigs globalConfigs = null;

    private int selectedOrgPos = 0;

    private Button homeButton;

    private Button myAccountButton;

    private TextView headerTitle;

    private ProgressDialog progressDialog;

    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SplashActivity.isAppInitialized()) {
            SplashActivity.restartApp(this);
            return;
        }

        setContentView(R.layout.place_hold);

        // set up action bar
        ActionBar actionBar = getSupportActionBar();
        actionBar.setSubtitle(AccountAccess.userName);
        actionBar.setDisplayHomeAsUpEnabled(true);

        globalConfigs = GlobalConfigs.getGlobalConfigs(this);
        RecordInfo record = (RecordInfo) getIntent().getSerializableExtra(
                "recordInfo");

        context = this;

        accountAccess = AccountAccess.getAccountAccess();

        recipient = (TextView) findViewById(R.id.hold_recipient);
        title = (TextView) findViewById(R.id.hold_title);
        author = (TextView) findViewById(R.id.hold_author);
        physical_description = (TextView) findViewById(R.id.hold_physical_description);
        cancel = (Button) findViewById(R.id.cancel_hold);
        placeHold = (Button) findViewById(R.id.place_hold);
        expiration_date = (EditText) findViewById(R.id.hold_expiration_date);
        phone_notification = (CheckBox) findViewById(R.id.hold_enable_phone_notification);
        phone_number = (EditText) findViewById(R.id.hold_contact_telephone);
        email_notification = (CheckBox) findViewById(R.id.hold_enable_email_notification);
        suspendHold = (CheckBox) findViewById(R.id.hold_suspend_hold);
        orgSelector = (Spinner) findViewById(R.id.hold_pickup_location);
        thaw_date_edittext = (EditText) findViewById(R.id.hold_thaw_date);

        recipient.setText(accountAccess.userName);
        title.setText(record.title);
        author.setText(record.author);
        physical_description.setText(record.physical_description);

        // hide edit text
        disableView(thaw_date_edittext);
        disableView(phone_number);

        cancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        final Integer record_id = record.doc_id;

        placeHoldRunnable = new Runnable() {

            @Override
            public void run() {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog = ProgressDialog.show(context,
                                getResources().getText(R.string.dialog_please_wait),
                                "Placing hold");
                    }
                });
                // TODO verify hold possible

                // accountAccess.getHoldPreCreateInfo(record_id, 4);
                // accountAccess.isHoldPossible(4, record_id);

                String expire_date_s = null;
                String thaw_date_s = null;
                if (expire_date != null)
                    expire_date_s = GlobalConfigs.getStringDate(expire_date);
                if (thaw_date != null)
                    thaw_date_s = GlobalConfigs.getStringDate(thaw_date);

                Log.d(TAG, "date expire: " + expire_date_s + " "
                        + expire_date);
                int selectedOrgID = -1;
                if (globalConfigs.organisations.size() > selectedOrgPos)
                    selectedOrgID = globalConfigs.organisations
                            .get(selectedOrgPos).id;

                String[] stringResponse = new String[] { "false" };
                try {
                    stringResponse = accountAccess.createHold(record_id,
                            selectedOrgID, email_notification.isChecked(),
                            phone_notification.isChecked(), phone_number
                                    .getText().toString(), suspendHold
                                    .isChecked(), expire_date_s, thaw_date_s);
                } catch (SessionNotFoundException e) {
                    try {
                        if (accountAccess.reauthenticate(PlaceHold.this))
                            stringResponse = accountAccess.createHold(
                                    record_id, selectedOrgID,
                                    email_notification.isChecked(),
                                    phone_notification.isChecked(),
                                    phone_number.getText().toString(),
                                    suspendHold.isChecked(), expire_date_s,
                                    thaw_date_s);
                    } catch (Exception e1) {
                    }
                }

                final String[] holdPlaced = stringResponse;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();

                        if (holdPlaced[0].equals("true")) {
                            Toast.makeText(context, "Hold Succesfully placed",
                                    Toast.LENGTH_LONG).show();
                            finish();
                        } else
                            Toast.makeText(context,
                                    "Error in placing hold : " + holdPlaced[2],
                                    Toast.LENGTH_LONG).show();

                    }
                });
            }
        };

        placeHold.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                Thread placeholdThread = new Thread(placeHoldRunnable);
                placeholdThread.start();
            }
        });

        phone_notification
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {

                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {

                        if (isChecked) {
                            enableView(phone_number);
                        } else
                            disableView(phone_number);
                    }
                });

        suspendHold.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {

                if (isChecked) {
                    enableView(thaw_date_edittext);
                } else {
                    disableView(thaw_date_edittext);
                }
            }
        });

        Calendar cal = Calendar.getInstance();

        datePicker = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {

                    public void onDateSet(DatePicker view, int year,
                            int monthOfYear, int dayOfMonth) {

                        Date chosenDate = new Date(year - 1900, monthOfYear,
                                dayOfMonth);
                        expire_date = chosenDate;
                        CharSequence strDate = DateFormat.format(
                                "MMMM dd, yyyy", chosenDate);
                        expiration_date.setText(strDate);
                        // set current date
                    }
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));

        expiration_date.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                datePicker.show();
            }
        });

        thaw_datePicker = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {

                    public void onDateSet(DatePicker view, int year,
                            int monthOfYear, int dayOfMonth) {

                        Date chosenDate = new Date(year - 1900, monthOfYear,
                                dayOfMonth);
                        thaw_date = chosenDate;
                        CharSequence strDate = DateFormat.format(
                                "MMMM dd, yyyy", chosenDate);
                        thaw_date_edittext.setText(strDate);
                        // set current date
                    }
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));

        thaw_date_edittext.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                thaw_datePicker.show();
            }
        });

        ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < globalConfigs.organisations.size(); i++) {
            list.add(globalConfigs.organisations.get(i).padding
                    + globalConfigs.organisations.get(i).name);

            if (globalConfigs.organisations.get(i).level - 1 == 0)
                selectedOrgPos = i;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, list);
        orgSelector.setAdapter(adapter);

        orgSelector.setSelection(selectedOrgPos);

        orgSelector.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int ID,
                    long arg3) {

                selectedOrgPos = ID;

            }

            public void onNothingSelected(android.widget.AdapterView<?> arg0) {
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void disableView(View view) {

        // view.setFocusable(false);
        view.setFocusable(false);

        view.setBackgroundColor(Color.argb(255, 100, 100, 100));
        // view.setVisibility(View.INVISIBLE);
    }

    public void enableView(View view) {
        // view.setVisibility(View.VISIBLE);

        view.setFocusableInTouchMode(true);

        view.setBackgroundColor(Color.argb(255, 255, 255, 255));
    }

}
