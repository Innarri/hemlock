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

import android.view.MenuItem;
import android.widget.*;
import org.evergreen_ils.Api;
import org.evergreen_ils.R;
import org.evergreen_ils.accountAccess.AccountAccess;
import org.evergreen_ils.accountAccess.SessionNotFoundException;
import org.evergreen_ils.android.App;
import org.evergreen_ils.data.EgOrg;
import org.evergreen_ils.system.Analytics;
import org.evergreen_ils.system.Log;
import org.evergreen_ils.data.Organization;
import org.evergreen_ils.utils.ui.ActionBarUtils;
import org.evergreen_ils.utils.ui.BaseActivity;
import org.evergreen_ils.utils.ui.OrgArrayAdapter;
import org.evergreen_ils.utils.ui.ProgressDialogSupport;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class HoldDetailsActivity extends BaseActivity {

    private final static String TAG = HoldDetailsActivity.class.getSimpleName();

    public static final int RESULT_CODE_DELETE_HOLD = 5;
    public static final int RESULT_CODE_UPDATE_HOLD = 6;
    public static final int RESULT_CODE_CANCEL = 7;
    private AccountAccess accountAccess;
    private EditText expirationDate;
    private DatePickerDialog datePicker = null;
    private CheckBox suspendHold;
    private DatePickerDialog thawDatePicker = null;
    private EditText thawDateEdittext;
    private Date expireDate = null;
    private Date thawDate = null;
    private int selectedOrgPos = 0;
    public Runnable updateHoldRunnable;
    private ProgressDialogSupport progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!App.isStarted()) {
            App.restartApp(this);
            return;
        }

        setContentView(R.layout.hold_details);
        ActionBarUtils.initActionBarForActivity(this);

        accountAccess = AccountAccess.getInstance();
        progress = new ProgressDialogSupport();

        final HoldRecord record = (HoldRecord) getIntent().getSerializableExtra("holdRecord");

        TextView title = findViewById(R.id.hold_title);
        TextView author = findViewById(R.id.hold_author);
        TextView format = findViewById(R.id.hold_format);
        TextView physical_description = findViewById(R.id.hold_physical_description);
        Button cancelHold = findViewById(R.id.cancel_hold_button);
        Button updateHold = findViewById(R.id.update_hold_button);
        suspendHold = findViewById(R.id.hold_suspend_hold);
        Spinner orgSelector = findViewById(R.id.hold_pickup_location);
        expirationDate = findViewById(R.id.hold_expiration_date);
        thawDateEdittext = findViewById(R.id.hold_thaw_date);

        title.setText(record.getTitle());
        author.setText(record.getAuthor());
        if (record.recordInfo != null) {
            format.setText(record.recordInfo.getIconFormatLabel());
            physical_description.setText(record.recordInfo.physical_description);
        }

        suspendHold.setChecked(record.isSuspended());
        if (record.isSuspended() && record.getThawDate() != null) {
            thawDate = record.getThawDate();
            thawDateEdittext.setText(DateFormat.format("MMMM dd, yyyy", thawDate));
        }

        if (record.getExpireTime() != null) {
            expireDate = record.getExpireTime();
            expirationDate.setText(DateFormat.format("MMMM dd, yyyy", expireDate));
        }

        thawDateEdittext.setEnabled(suspendHold.isChecked());

        cancelHold.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(HoldDetailsActivity.this);
                builder.setMessage(R.string.cancel_hold_dialog_message);
                builder.setNegativeButton(R.string.cancel_hold_negative_button, null);
                builder.setPositiveButton(R.string.cancel_hold_positive_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Analytics.logEvent("Holds: Cancel Hold");
                        cancelHold(record);
                    }
                });
                builder.create().show();
            }
        });

        updateHold.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Analytics.logEvent("Holds: Update Hold");
                updateHold(record);
            }
        });

        suspendHold.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                thawDateEdittext.setEnabled(isChecked);
            }
        });
        Calendar cal = Calendar.getInstance();

        datePicker = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {

                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        Date chosenDate = new Date(year - 1900, monthOfYear, dayOfMonth);
                        expireDate = chosenDate;
                        CharSequence strDate = DateFormat.format("MMMM dd, yyyy", chosenDate);
                        expirationDate.setText(strDate);
                    }
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        expirationDate.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                datePicker.show();
            }
        });

        thawDatePicker = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {

                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        Date chosenDate = new Date(year - 1900, monthOfYear, dayOfMonth);
                        thawDate = chosenDate;
                        CharSequence strDate = DateFormat.format("MMMM dd, yyyy", chosenDate);
                        thawDateEdittext.setText(strDate);
                    }
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        thawDateEdittext.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                thawDatePicker.show();
            }
        });

        ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < EgOrg.getOrgs().size(); i++) {
            Organization org = EgOrg.getOrgs().get(i);
            list.add(org.getTreeDisplayName());
            if (org.id == record.getPickupLib()) {
                selectedOrgPos = i;
            }
        }
        ArrayAdapter<String> adapter = new OrgArrayAdapter(this, R.layout.org_item_layout, list, true);
        orgSelector.setAdapter(adapter);
        orgSelector.setSelection(selectedOrgPos);

        orgSelector.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int ID, long arg3) {
                selectedOrgPos = ID;
            }

            public void onNothingSelected(android.widget.AdapterView<?> arg0) {
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (progress != null) progress.dismiss();
        super.onDestroy();
    }

    private void cancelHold(final HoldRecord record) {
        Log.d(TAG, "Remove hold with id" + record.ahr.getInt("id"));
        progress.show(this, "Canceling hold");
        Thread cancelHoldThread = new Thread(
                new Runnable() {

                    @Override
                    public void run() {
                        try {
                            accountAccess.cancelHold(record.ahr);
                        } catch (SessionNotFoundException e) {
                            try {
                                if (accountAccess.reauthenticate(HoldDetailsActivity.this))
                                    accountAccess.cancelHold(record.ahr);
                            } catch (Exception eauth) {
                                Log.d(TAG, "Exception in reAuth");
                            }
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progress.dismiss();
                                setResult(RESULT_CODE_DELETE_HOLD);
                                finish();
                            }
                        });
                    }
                });
        cancelHoldThread.start();
    }

    private void updateHold(final HoldRecord record) {
        updateHoldRunnable = new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                                  @Override
                                  public void run() {
                                      progress.show(HoldDetailsActivity.this, "Updating hold");
                                  }
                              });

                String expire_date_s = null;
                String thaw_date_s = null;
                if (expireDate != null)
                    expire_date_s = Api.formatDate(expireDate);
                if (thawDate != null)
                    thaw_date_s = Api.formatDate(thawDate);

                try {
                    accountAccess.updateHold(record.ahr, EgOrg.getOrgs().get(selectedOrgPos).id,
                            suspendHold.isChecked(), expire_date_s, thaw_date_s);
                } catch (SessionNotFoundException e) {
                    try {
                        if (accountAccess.reauthenticate(HoldDetailsActivity.this))
                            accountAccess.updateHold(record.ahr,
                                    EgOrg.getOrgs().get(selectedOrgPos).id,
                                    suspendHold.isChecked(), expire_date_s, thaw_date_s);
                    } catch (Exception eauth) {
                        Log.d(TAG, "Exception in reAuth");
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.dismiss();
                        Toast.makeText(HoldDetailsActivity.this, "Hold updated",
                                Toast.LENGTH_SHORT);
                        setResult(RESULT_CODE_UPDATE_HOLD);
                        finish();
                    }
                });
            }
        };

        Thread updateHoldThread = new Thread(updateHoldRunnable);
        updateHoldThread.start();
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
