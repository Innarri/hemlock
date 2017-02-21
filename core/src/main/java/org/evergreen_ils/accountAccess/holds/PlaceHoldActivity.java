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
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import org.evergreen_ils.Api;
import org.evergreen_ils.R;
import org.evergreen_ils.accountAccess.AccountAccess;
import org.evergreen_ils.accountAccess.SessionNotFoundException;
import org.evergreen_ils.net.GatewayJsonObjectRequest;
import org.evergreen_ils.net.VolleyWrangler;
import org.evergreen_ils.system.EvergreenServer;
import org.evergreen_ils.system.EvergreenServerLoader;
import org.evergreen_ils.system.Log;
import org.evergreen_ils.system.Organization;
import org.evergreen_ils.searchCatalog.RecordInfo;
import org.evergreen_ils.system.Utils;
import org.evergreen_ils.utils.ui.ActionBarUtils;
import org.evergreen_ils.utils.ui.ProgressDialogSupport;
import org.evergreen_ils.views.splashscreen.SplashActivity;
import org.opensrf.util.GatewayResponse;

import android.app.DatePickerDialog;
import android.content.Context;
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

import com.android.volley.Request;
import com.android.volley.Response;

public class PlaceHoldActivity extends ActionBarActivity {

    private static final String TAG = PlaceHoldActivity.class.getSimpleName();
    private TextView title;
    private TextView author;
    private TextView format;
    private TextView physical_description;
    private AccountAccess accountAccess;
    private EditText expiration_date;
    private EditText phone_number;
    private CheckBox phone_notification;
    private CheckBox email_notification;
    private CheckBox sms_notification;
    private Spinner sms_spinner;
    private Button placeHold;
    private CheckBox suspendHold;
    private Spinner orgSpinner;
    private DatePickerDialog datePicker = null;
    private DatePickerDialog thaw_datePicker = null;
    private EditText thaw_date_edittext;
    private Date expire_date = null;
    private Date thaw_date = null;
    private Runnable placeHoldRunnable;
    private EvergreenServer eg = null;
    private int selectedOrgPos = 0;
    private ProgressDialogSupport progress;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SplashActivity.isAppInitialized()) {
            SplashActivity.restartApp(this);
            return;
        }

        setContentView(R.layout.place_hold);
        ActionBarUtils.initActionBarForActivity(this);

        eg = EvergreenServer.getInstance();
        RecordInfo record = (RecordInfo) getIntent().getSerializableExtra("recordInfo");

        context = this;
        accountAccess = AccountAccess.getInstance();
        progress = new ProgressDialogSupport();

        title = (TextView) findViewById(R.id.hold_title);
        author = (TextView) findViewById(R.id.hold_author);
        format = (TextView) findViewById(R.id.hold_format);
        physical_description = (TextView) findViewById(R.id.hold_physical_description);
        placeHold = (Button) findViewById(R.id.place_hold);
        expiration_date = (EditText) findViewById(R.id.hold_expiration_date);
        phone_notification = (CheckBox) findViewById(R.id.hold_enable_phone_notification);
        phone_number = (EditText) findViewById(R.id.hold_contact_telephone);
        email_notification = (CheckBox) findViewById(R.id.hold_enable_email_notification);
        sms_notification = (CheckBox) findViewById(R.id.hold_enable_sms_notification);
        sms_spinner = (Spinner) findViewById(R.id.hold_sms_carrier);
        suspendHold = (CheckBox) findViewById(R.id.hold_suspend_hold);
        orgSpinner = (Spinner) findViewById(R.id.hold_pickup_location);
        thaw_date_edittext = (EditText) findViewById(R.id.hold_thaw_date);

        title.setText(record.title);
        author.setText(record.author);
        format.setText(RecordInfo.getFormatLabel(record));
        physical_description.setText(record.physical_description);

        initSMSButton(eg.getSMSEnabled());
        initPlaceHoldRunnable(record);
        initPlaceHoldButton();
        initSuspendHoldButton();
        initDatePickers();
        initOrgSpinner();
    }

    private void initPlaceHoldRunnable(RecordInfo record) {
        final Integer record_id = record.doc_id;
        placeHoldRunnable = new Runnable() {

            @Override
            public void run() {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.show(context, "Placing hold");
                    }
                });

                String expire_date_s = null;
                if (expire_date != null)
                    expire_date_s = Api.formatDate(expire_date);
                String thaw_date_s = null;
                if (thaw_date != null)
                    thaw_date_s = Api.formatDate(thaw_date);

                int selectedOrgID = -1;
                if (eg.getInstance().getOrganizations().size() > selectedOrgPos)
                    selectedOrgID = eg.getInstance().getOrganizations().get(selectedOrgPos).id;

                String[] stringResponse = new String[] { "false" };
                try {
                    stringResponse = accountAccess.testAndCreateHold(record_id, selectedOrgID,
                            email_notification.isChecked(),
                            phone_notification.isChecked(),
                            phone_number.getText().toString(),
                            suspendHold.isChecked(), expire_date_s, thaw_date_s);
                } catch (SessionNotFoundException e) {
                    try {
                        if (accountAccess.reauthenticate(PlaceHoldActivity.this))
                            stringResponse = accountAccess.testAndCreateHold(
                                    record_id, selectedOrgID,
                                    email_notification.isChecked(),
                                    phone_notification.isChecked(),
                                    phone_number.getText().toString(),
                                    suspendHold.isChecked(), expire_date_s, thaw_date_s);
                    } catch (Exception e1) {
                    }
                }

                final String[] holdPlaced = stringResponse;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.dismiss();

                        if (holdPlaced[0].equals("true")) {
                            Toast.makeText(context, "Hold successfully placed",
                                    Toast.LENGTH_LONG).show();
                            startActivity(new Intent(context, HoldsListView.class));
                            finish();
                        } else
                            Toast.makeText(context,
                                    "Error placing hold: " + holdPlaced[2],
                                    Toast.LENGTH_LONG).show();

                    }
                });
            }
        };
    }

    private void initPlaceHoldButton() {
        placeHold.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Thread placeholdThread = new Thread(placeHoldRunnable);
                placeholdThread.start();
            }
        });
    }

    private void initSuspendHoldButton() {
        suspendHold.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                thaw_date_edittext.setEnabled(isChecked);
            }
        });
    }

    private void initSMSButton(boolean sms_enabled) {
        if (sms_enabled) {
            sms_notification.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    sms_spinner.setEnabled(isChecked);
                    phone_number.setEnabled(isChecked);
                }
            });
            boolean sms_notify = sms_notification.isChecked();
            sms_spinner.setEnabled(sms_notify);
            phone_number.setEnabled(sms_notify);
        } else {
            sms_notification.setVisibility(View.GONE);
            sms_spinner.setVisibility(View.GONE);
            phone_number.setVisibility(View.GONE);
        }
    }

    private void initDatePickers() {
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
    }

    private void initOrgSpinner() {
        int defaultLibraryID = AccountAccess.getInstance().getDefaultPickupLibraryID();
        ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < eg.getInstance().getOrganizations().size(); i++) {
            Organization org = eg.getInstance().getOrganizations().get(i);
            list.add(org.getTreeDisplayName());
            if (org.id == defaultLibraryID) {
                selectedOrgPos = i;
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.org_item_layout, list) {
            @Override
            public boolean isEnabled(int pos) {
                Organization org = eg.getInstance().getOrganizations().get(pos);
                return org.isPickupLocation();
            }
        };
        orgSpinner.setAdapter(adapter);
        orgSpinner.setSelection(selectedOrgPos);
        orgSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int ID, long arg3) {
                selectedOrgPos = ID;
            }

            @Override
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
}
