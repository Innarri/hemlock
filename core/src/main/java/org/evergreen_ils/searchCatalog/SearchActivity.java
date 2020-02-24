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

import java.util.*;

import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.widget.SwitchCompat;

import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.evergreen_ils.R;
import org.evergreen_ils.accountAccess.AccountAccess;
import org.evergreen_ils.accountAccess.bookbags.BookBag;
import org.evergreen_ils.accountAccess.bookbags.BookBagUtils;
import org.evergreen_ils.views.holds.PlaceHoldActivity;
import org.evergreen_ils.android.App;
import org.evergreen_ils.barcodescan.CaptureActivity;
import org.evergreen_ils.data.EgCodedValueMap;
import org.evergreen_ils.data.EgOrg;
import org.evergreen_ils.android.Analytics;
import org.evergreen_ils.utils.IntUtils;
import org.evergreen_ils.utils.ui.AppState;
import org.evergreen_ils.android.Log;
import org.evergreen_ils.data.Organization;
import org.evergreen_ils.utils.ui.BaseActivity;
import org.evergreen_ils.utils.ui.OrgArrayAdapter;
import org.evergreen_ils.utils.ui.ProgressDialogSupport;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView.OnItemSelectedListener;

public class SearchActivity extends BaseActivity {

    private static final String TAG = SearchActivity.class.getSimpleName();

    public static final String SEARCH_OPTIONS_VISIBLE = "search_options_visible";

    private EditText searchText;
    private SwitchCompat searchOptionsButton;
    private View searchOptionsLayout;
    private Button searchButton;
    private Spinner orgSpinner;
    private Spinner searchClassSpinner;
    private Spinner searchFormatSpinner;
    private TextView searchResultsSummary;
    private SearchResultsFragment searchResultsFragment;

    private SearchCatalog search;
    private ArrayList<RecordInfo> recordList;
    private ProgressDialogSupport progress;
    private ArrayList<RecordInfo> searchResults;
    private ArrayList<BookBag> bookBags;
    private Runnable searchForResultsRunnable = null;
    private Boolean haveSearched = false;

    private ContextMenuRecordInfo contextMenuRecordInfo;

    private String getSearchText() {
        return searchText.getText().toString();
    }

    private String getSearchClass() {
        return searchClassSpinner.getSelectedItem().toString().toLowerCase();
    }

    private String getSearchFormatCode() {
        return EgCodedValueMap.searchFormatCode(searchFormatSpinner.getSelectedItem().toString());
    }

    private class ContextMenuRecordInfo implements ContextMenuInfo {
        public RecordInfo record;
        public int position;
    }

//    @Override
//    protected void onSaveInstanceState(Bundle outState) {
//        super.onSaveInstanceState(outState);
//        //outState.putSerializable("recordList", recordList);
//    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
//        Log.d(TAG, "[kcxxx] onCreate");
        super.onCreate(savedInstanceState);
        if (isRestarting()) return;

        setContentView(R.layout.activity_search);

        search = SearchCatalog.getInstance();
        bookBags = AccountAccess.getInstance().getBookbags();
        progress = new ProgressDialogSupport();

        searchResults = new ArrayList<>();
        clearResults();
//        if (savedInstanceState == null) {
//            recordList = new ArrayList<>();
//        } else {
//            recordList = (ArrayList<RecordInfo>) savedInstanceState.getSerializable("recordList");
//        }
        recordList = search.getResults();

        // create search results fragment
        if (savedInstanceState == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            searchResultsFragment = new SearchResultsFragment();
//            Bundle args = new Bundle();
//            args.putSerializable("recordList", recordList);
//            searchResultsFragment.setArguments(args);
            transaction.replace(R.id.search_results_list, searchResultsFragment);
            transaction.commit();
        } else {
            searchResultsFragment = (SearchResultsFragment) getSupportFragmentManager().findFragmentById(R.id.search_results_list);
        }

        searchText = findViewById(R.id.searchText);
        searchOptionsButton = findViewById(R.id.search_options_button);
        searchOptionsLayout = findViewById(R.id.search_options_layout);
        searchButton = findViewById(R.id.search_button);
        searchClassSpinner = findViewById(R.id.search_qtype_spinner);
        searchFormatSpinner = findViewById(R.id.search_format_spinner);
        orgSpinner = findViewById(R.id.search_org_spinner);
        searchResultsSummary = findViewById(R.id.search_result_number);

        initSearchOptionsVisibility();
        initSearchText();
        initSearchOptionsButton();
        initSearchButton();
        initSearchFormatSpinner();
        initSearchOrgSpinner();
        initSearchRunnable();
        initRecordClickListener();
        updateSearchResultsSummary();
    }

//    @Override
//    protected void onStop() {
//        Log.d(TAG, "[kcxxx] onStop");
//        super.onStop();
//    }
//
//    @Override
//    protected void onStart() {
//        Log.d(TAG, "[kcxxx] onStart");
//        super.onStart();
//    }
//
//    @Override
//    protected void onPause() {
//        Log.d(TAG, "[kcxxx] onPause");
//        super.onPause();
//    }
//
//    @Override
//    protected void onResume() {
//        Log.d(TAG, "[kcxxx] onResume");
//        super.onResume();
//    }

    @Override
    protected void onDestroy() {
        if (progress != null) progress.dismiss();
//        Log.d(TAG, "[kcxxx] onDestroy");
        clearResults();
        super.onDestroy();
    }

//    @Override
//    public void onBackPressed() {
//        super.onBackPressed();
//        Log.d(TAG, "[kcxxx] onBackPressed");
//    }

    private void clearResults() {
        haveSearched = false;
        searchResults.clear();
        search.clearResults();
    }

    private void initSearchButton() {
        searchButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startSearchThread();
            }
        });
    }

    private void initSearchOptionsVisibility() {
        boolean last_state = AppState.getBoolean(SEARCH_OPTIONS_VISIBLE, true);
        searchOptionsButton.setChecked(last_state);
        setSearchOptionsVisibility(last_state);
    }

    private void setSearchOptionsVisibility(boolean visible) {
        searchOptionsLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
        AppState.setBoolean(SEARCH_OPTIONS_VISIBLE, visible);
    }

    private void initSearchOptionsButton() {
        searchOptionsButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setSearchOptionsVisibility(isChecked);
            }
        });
    }

    private void initSearchText() {
        searchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_SEARCH) {
                    startSearchThread();
                    return true;
                }
                return false;
            }
        });
    }

    private void initSearchRunnable() {
        searchForResultsRunnable = new Runnable() {

            @Override
            public void run() {

                final String text = getSearchText();
                if (text.length() < 1)
                    return;
                int searchQueryType = searchClassSpinner.getSelectedItemPosition();
                Log.d(TAG, "type="+searchQueryType+" class="+getSearchClass()+" format="+getSearchFormatCode());

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(searchText.getWindowToken(), 0);
                        searchResultsSummary.setVisibility(View.VISIBLE);
                        progress.show(SearchActivity.this, getString(R.string.dialog_fetching_data_message));
                    }
                });

                searchResults = search.getSearchResults(text, getSearchClass(), getSearchFormatCode(), getString(R.string.ou_sort_by), 0);
                try {
                    Organization search_org = search.selectedOrganization;
                    Organization home_org = EgOrg.INSTANCE.findOrg(App.getAccount().getHomeOrg());
                    String search_org_val = TextUtils.equals(search_org.name, home_org.name) ? "home" :
                            ((search_org.isConsortium()) ? search_org.shortname : "other");
                    Analytics.logEvent("Search: Execute",
                            "num_results", search.visible,
                            "search_org", search_org_val,
                            "search_type", getSearchClass(),
                            "search_format", getSearchFormatCode());
                } catch (Exception e) {
                    Analytics.logException(e);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        recordList.clear();
                        for (RecordInfo record : searchResults) {
                            recordList.add(record);
                        }

                        searchResultsFragment.notifyDatasetChanged();
                        updateSearchResultsSummary();
                        progress.dismiss();
                    }
                });
            }
        };
    }

    private void updateSearchResultsSummary() {
        String s = null;
        if (recordList.size() < search.visible) {
            s = getString(R.string.first_n_of_m_results, recordList.size(), search.visible);
        } else if (recordList.size() > 0 || haveSearched) {
            s = getString(R.string.n_results, search.visible);
        }
        searchResultsSummary.setText(s);
    }

    private void initSearchOrgSpinner() {
        int selectedOrgPos = 0;
        Integer defaultLibraryID = App.getAccount().getSearchOrg();
        ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < EgOrg.getOrgs().size(); i++) {
            Organization org = EgOrg.getOrgs().get(i);
            list.add(org.getTreeDisplayName());
            if (IntUtils.equals(org.id, defaultLibraryID)) {
                selectedOrgPos = i;
            }
        }
        ArrayAdapter<String> adapter = new OrgArrayAdapter(this, R.layout.org_item_layout, list, false);
        orgSpinner.setAdapter(adapter);
        orgSpinner.setSelection(selectedOrgPos);
        search.selectOrganisation(EgOrg.getOrgs().get(selectedOrgPos));
        orgSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int ID, long arg3) {
                search.selectOrganisation(EgOrg.getOrgs().get(ID));
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
    }

    private void initSearchFormatSpinner() {
        List<String> labels = EgCodedValueMap.getSearchFormatSpinnerLabels();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        searchFormatSpinner.setAdapter(adapter);
    }

    private void initRecordClickListener() {
        registerForContextMenu(findViewById(R.id.search_results_list));
        searchResultsFragment.setOnRecordClickListener(new RecordInfo.OnRecordClickListener() {
            @Override
            public void onClick(RecordInfo record, int position) {
                Intent intent = new Intent(getBaseContext(), SampleUnderlinesNoFade.class);
                intent.putExtra("orgID", search.selectedOrganization.id);
                //intent.putExtra("recordList", recordList);
                intent.putExtra("recordPosition", position);
                intent.putExtra("numResults", search.visible);
                startActivityForResult(intent, 10);
            }
        });
        searchResultsFragment.setOnRecordLongClickListener(new RecordInfo.OnRecordLongClickListener() {
            @Override
            public void onLongClick(RecordInfo record, int position) {
                contextMenuRecordInfo = new ContextMenuRecordInfo();
                contextMenuRecordInfo.record = record;
                contextMenuRecordInfo.position = position;
                openContextMenu(findViewById(R.id.search_results_list));
            }
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (v.getId() == R.id.search_results_list) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            menu.add(Menu.NONE, App.ITEM_SHOW_DETAILS, 0, getString(R.string.show_details_message));
            menu.add(Menu.NONE, App.ITEM_PLACE_HOLD, 1, getString(R.string.hold_place_title));
            menu.add(Menu.NONE, App.ITEM_ADD_TO_LIST, 2, getString(R.string.add_to_my_list_message));
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        //ContextMenuRecordInfo info = (ContextMenuRecordInfo)item.getMenuInfo();
        ContextMenuRecordInfo info = contextMenuRecordInfo;
        if (info == null)
            return false;

        switch (item.getItemId()) {
        case App.ITEM_SHOW_DETAILS:
            Intent intent = new Intent(getBaseContext(), SampleUnderlinesNoFade.class);
            intent.putExtra("orgID", search.selectedOrganization.id);
//            intent.putExtra("recordList", recordList);
            intent.putExtra("recordPosition", info.position);
            intent.putExtra("numResults", search.visible);
            startActivity(intent);
            return true;
        case App.ITEM_PLACE_HOLD:
            Intent hold_intent = new Intent(getBaseContext(), PlaceHoldActivity.class);
            hold_intent.putExtra("recordInfo", info.record);
            startActivity(hold_intent);
            return true;
        case App.ITEM_ADD_TO_LIST:
            if (bookBags.size() > 0) {
                Analytics.logEvent("Lists: Add to List", "via", "results_long_press");
                BookBagUtils.showAddToListDialog(this, bookBags, info.record);
            } else {
                Toast.makeText(this, getText(R.string.msg_no_lists), Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        return super.onContextItemSelected(item);
    }

    //// TODO: 4/30/2017 pull up
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_search, menu);
        String url = getFeedbackUrl();
        if (TextUtils.isEmpty(url))
            menu.removeItem(R.id.action_feedback);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_advanced_search) {
            Analytics.logEvent("Advanced Search: Open", "via", "options_menu");
            startActivityForResult(new Intent(getApplicationContext(), AdvancedSearchActivity.class), 2);
            return true;
        } else if (id == R.id.action_logout) {
            Analytics.logEvent("Account: Logout", "via", "options_menu");
            logout();
            App.restartApp(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void startSearchThread() {
        haveSearched = true;
        Thread searchThread = new Thread(searchForResultsRunnable);
        searchThread.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // todo we should not switch on resultCode here, we should switch on requestCode
        switch (resultCode) {
            case SampleUnderlinesNoFade.RETURN_DATA: {
//                try {
//                    ArrayList<RecordInfo> resultRecords = (ArrayList<RecordInfo>) data.getSerializableExtra("recordList");
//                    replaceRecordListIfLarger((ArrayList) resultRecords);
//                } catch (Exception e) {
//                    Log.d(TAG, "caught", e);
//                }
            }
            break;

            case AdvancedSearchActivity.RESULT_ADVANCED_SEARCH: {
                Log.d(TAG, "result text:" + data.getStringExtra("advancedSearchText"));
                searchText.setText(data.getStringExtra("advancedSearchText"));
                startSearchThread();
            }
            break;

            case CaptureActivity.BARCODE_SEARCH: {
                searchText.setText("identifier|isbn: "
                        + data.getStringExtra("barcodeValue"));
                startSearchThread();
            }
        }
    }
}
