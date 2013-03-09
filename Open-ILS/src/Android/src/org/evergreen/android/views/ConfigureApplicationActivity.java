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
package org.evergreen.android.views;

import org.evergreen.android.R;
import org.evergreen.android.accountAccess.AccountAccess;
import org.evergreen.android.globals.GlobalConfigs;
import org.evergreen.android.globals.NoAccessToServer;
import org.evergreen.android.globals.Utils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class ConfigureApplicationActivity extends Activity {

    private String TAG = "ConfigureApplicationActivity";

    private ProgressDialog progressDialog = null;

    private EditText server_http;

    private EditText username;

    private EditText password;

    private Context context;

    public static final int RESULT_CONFIGURE_SUCCESS = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        setContentView(R.layout.configure_application);
        context = this;
        server_http = (EditText) findViewById(R.id.server_http_adress);
        username = (EditText) findViewById(R.id.username);
        password = (EditText) findViewById(R.id.password);

        
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        username.setText(preferences.getString("username", ""));
        server_http.setText(preferences.getString("library_url", ""));  
        password.setText(preferences.getString("password", ""));
        
        Button connect = (Button) findViewById(R.id.connect_button);

        connect.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                Thread checkConn = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        boolean server_address = false;
                        boolean auth = false;

                        try {
                            server_address = Utils
                                    .checkIfNetAddressIsReachable(server_http
                                            .getText().toString());
                        } catch (NoAccessToServer e) {
                            server_address = false;
                        }

                        if (server_address == true) {
                            
                            SharedPreferences preferences = PreferenceManager
                                    .getDefaultSharedPreferences(context);
                            SharedPreferences.Editor editor = preferences
                                    .edit();
                            // store into preference
                            editor.putString("library_url", server_http
                                    .getText().toString());
                            
                            editor.putString("username", username.getText()
                                    .toString());
                            editor.putString("password", password.getText()
                                    .toString());

                            editor.commit();
                            GlobalConfigs.httpAddress = server_http.getText()
                                    .toString();
                            AccountAccess accountAccess = AccountAccess
                                    .getAccountAccess(
                                            GlobalConfigs.httpAddress,
                                            (ConnectivityManager) getSystemService(Service.CONNECTIVITY_SERVICE));

                            AccountAccess.setAccountInfo(username.getText()
                                    .toString(), password.getText().toString());

                            try {
                                auth = accountAccess.authenticate();
                                Log.d(TAG, "Auth " + auth);
                            } catch (Exception e) {
                                System.out.println("Exception "
                                        + e.getMessage());
                            }

                            if (auth == true) {

                                runOnUiThread(new Runnable() {

                                    @Override
                                    public void run() {
                                        progressDialog.dismiss();
                                        setResult(RESULT_CONFIGURE_SUCCESS);
                                        finish();

                                    }
                                });

                            } else {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        progressDialog.dismiss();
                                        Toast.makeText(context,
                                                "Bad user login information",
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                            }

                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressDialog.dismiss();
                                    Toast.makeText(context,
                                            "Bad library server url",
                                            Toast.LENGTH_LONG).show();
                                }
                            });

                        }

                    }
                });

                progressDialog = ProgressDialog.show(context, "Please wait",
                        "Checking server and credentials");
                checkConn.start();
            }

        });

    }

}
