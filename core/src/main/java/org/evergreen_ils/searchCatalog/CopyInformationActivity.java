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
package org.evergreen_ils.searchCatalog;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.evergreen_ils.Api;
import org.evergreen_ils.R;
import org.evergreen_ils.android.App;
import org.evergreen_ils.api.EvergreenService;
import org.evergreen_ils.net.Gateway;
import org.evergreen_ils.net.GatewayJsonObjectRequest;
import org.evergreen_ils.net.VolleyWrangler;
import org.evergreen_ils.system.Log;
import org.evergreen_ils.system.Organization;
import org.evergreen_ils.utils.ui.ActionBarUtils;
import org.evergreen_ils.system.Analytics;
import org.evergreen_ils.utils.ui.TextViewUtils;
import org.opensrf.util.GatewayResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CopyInformationActivity extends AppCompatActivity {

    private static final String TAG = CopyInformationActivity.class.getSimpleName();
    private RecordInfo record;
    private Integer orgID;
    private boolean groupBySystem;
    private ListView lv;
    private ArrayList<CopyLocationCounts> copyInfoRecords;
    private CopyInformationArrayAdapter listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Analytics.initialize(this);
        if (!App.isStarted()) {
            App.restartApp(this);
            return;
        }

        setContentView(R.layout.copy_information_list);
        ActionBarUtils.initActionBarForActivity(this);

        if (savedInstanceState != null) {
            record = (RecordInfo) savedInstanceState.getSerializable("recordInfo");
            orgID = savedInstanceState.getInt("orgID");
        } else {
            record = (RecordInfo) getIntent().getSerializableExtra("recordInfo");
            orgID = getIntent().getIntExtra("orgID", 1);
        }

        groupBySystem = getResources().getBoolean(R.bool.ou_group_copy_info_by_system);

        lv = findViewById(R.id.copy_information_list);
        copyInfoRecords = new ArrayList<>();
        listAdapter = new CopyInformationArrayAdapter(this,
                R.layout.copy_information_item, copyInfoRecords);
        lv.setAdapter(listAdapter);
        if (getResources().getBoolean(R.bool.ou_enable_copy_info_web_links)) {
            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    CopyLocationCounts record = (CopyLocationCounts) lv.getItemAtPosition(position);
                    String url = EvergreenService.Companion.getOrgInfoPageUrl(record.org_id);
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                }
            });
        } else {
            lv.setSelector(android.R.color.transparent);
        }

        TextView summaryText = (TextView) findViewById(R.id.copy_information_summary);
        summaryText.setText(RecordLoader.getCopySummary(record, orgID, this));

        initCopyLocationCounts();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("recordInfo", record);
        outState.putInt("orgID", orgID);
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

    static int safeCompareTo(String a, String b) {
        if (a == null && b == null) { return 0; }
        else if (a == null) { return -1; }
        else if (b == null) { return 1; }
        else return a.compareTo(b);
    }

    public void updateCopyInfo(List<CopyLocationCounts> copyLocationCountsList) {
        if (copyLocationCountsList == null)
            return;

        copyInfoRecords.clear();
        for (CopyLocationCounts info: copyLocationCountsList) {
            Organization org = EvergreenService.Companion.findOrg(info.org_id);
            // if a branch is not opac visible, its copies should not be visible
            if (org != null && org.opac_visible) {
                copyInfoRecords.add(info);
            }
        }

        if (groupBySystem) {
            // sort by system, then by branch, like http://gapines.org/eg/opac/record/5700567?locg=1
            Collections.sort(copyInfoRecords, new Comparator<CopyLocationCounts>() {
                @Override
                public int compare(CopyLocationCounts a, CopyLocationCounts b) {
                    Organization a_org = EvergreenService.Companion.findOrg(a.org_id);
                    Organization b_org = EvergreenService.Companion.findOrg(b.org_id);
                    String a_system_name = EvergreenService.Companion.getOrgNameSafe(a_org.parent_ou);
                    String b_system_name = EvergreenService.Companion.getOrgNameSafe(b_org.parent_ou);
                    int system_cmp = safeCompareTo(a_system_name, b_system_name);
                    if (system_cmp != 0)
                        return system_cmp;
                    return a_org.name.compareTo(b_org.name);
                }
            });
        } else {
            Collections.sort(copyInfoRecords, new Comparator<CopyLocationCounts>() {
                @Override
                public int compare(CopyLocationCounts a, CopyLocationCounts b) {
                    return EvergreenService.Companion.getOrgNameSafe(a.org_id).compareTo(EvergreenService.Companion.getOrgNameSafe(b.org_id));
                }
            });
        }
        listAdapter.notifyDataSetChanged();
    }

    private void initCopyLocationCounts() {
        final long start_ms = System.currentTimeMillis();
        Organization org = EvergreenService.Companion.findOrg(orgID);
        String url = Gateway.INSTANCE.buildUrl(
                Api.SEARCH, Api.COPY_LOCATION_COUNTS,
                new Object[]{record.doc_id, org.id, org.level});
        GatewayJsonObjectRequest r = new GatewayJsonObjectRequest(
                url,
                Request.Priority.NORMAL,
                new Response.Listener<GatewayResult>() {
                    @Override
                    public void onResponse(GatewayResult response) {
                        long duration_ms = System.currentTimeMillis() - start_ms;
                        Log.d(TAG, "fetch "+record.doc_id+" took " + duration_ms + "ms");
                        updateCopyInfo(RecordInfo.parseCopyLocationCounts(record, response));
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(TAG, "caught", error);
                        updateCopyInfo(RecordInfo.parseCopyLocationCounts(record, null));
                    }
                });
        VolleyWrangler.getInstance(this).addToRequestQueue(r);
    }

    class CopyInformationArrayAdapter extends ArrayAdapter<CopyLocationCounts> {
        private TextView majorLocationText;
        private TextView minorLocationText;
        private TextView copyCallNumberText;
        private TextView copyLocationText;
        private TextView copyStatusesText;
        private List<CopyLocationCounts> records;

        public CopyInformationArrayAdapter(Context context, int textViewResourceId, List<CopyLocationCounts> objects) {
            super(context, textViewResourceId, objects);
            records = objects;
        }

        public int getCount() {
            return this.records.size();
        }

        public CopyLocationCounts getItem(int index) {
            return this.records.get(index);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;

            final CopyLocationCounts item = getItem(position);

            if (row == null) {
                LayoutInflater inflater = (LayoutInflater) this.getContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                row = inflater.inflate(R.layout.copy_information_item, parent, false);
            }

            majorLocationText = (TextView) row.findViewById(R.id.copy_information_major_location);
            minorLocationText = (TextView) row.findViewById(R.id.copy_information_minor_location);
            copyCallNumberText = (TextView) row.findViewById(R.id.copy_information_call_number);
            copyLocationText = (TextView) row.findViewById(R.id.copy_information_copy_location);
            copyStatusesText = (TextView) row.findViewById(R.id.copy_information_statuses);

            Organization org = EvergreenService.Companion.findOrg(item.org_id);
            if (groupBySystem) {
                majorLocationText.setText(EvergreenService.Companion.getOrgNameSafe(org.parent_ou));
                //minorLocationText.setText(EvergreenService.Companion.getOrgNameSafe(item.org_id));
                String url = EvergreenService.Companion.getOrgInfoPageUrl(item.org_id);
                TextViewUtils.setTextHtml(minorLocationText, TextViewUtils.makeLinkHtml(url, org.name));
            } else {
                majorLocationText.setText(EvergreenService.Companion.getOrgNameSafe(item.org_id));
                minorLocationText.setVisibility(View.GONE);
            }
            copyCallNumberText.setText(item.getCallNumber());
            copyLocationText.setText(item.copy_location);

            List<String> statuses = item.getCountsByStatus();
            copyStatusesText.setText(TextUtils.join("\n", statuses));

            return row;
        }
    }
}
