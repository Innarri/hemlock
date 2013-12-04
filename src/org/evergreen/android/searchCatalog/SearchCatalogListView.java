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
package org.evergreen.android.searchCatalog;

import java.util.ArrayList;
import java.util.List;

import org.evergreen.android.R;
import org.evergreen.android.R.layout;
import org.evergreen.android.accountAccess.AccountAccess;
import org.evergreen.android.accountAccess.SessionNotFoundException;
import org.evergreen.android.accountAccess.bookbags.BookBag;
import org.evergreen.android.accountAccess.holds.PlaceHold;
import org.evergreen.android.barcodescan.CaptureActivity;
import org.evergreen.android.globals.GlobalConfigs;
import org.evergreen.android.globals.Utils;
import org.evergreen.android.views.AccountScreenDashboard;
import org.evergreen.android.views.ApplicationPreferences;
import org.evergreen.android.views.splashscreen.SplashActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class SearchCatalogListView extends Activity {

    private String TAG = "SearchCatalogListView";

    private ArrayList<RecordInfo> recordList;

    private EditText searchText;

    private ImageButton searchButton;

    private SearchCatalog search;

    private ListView lv;

    private SearchArrayAdapter adapter;

    private Context context;

    private ProgressDialog progressDialog;

    private ArrayList<RecordInfo> searchResults;

    private Spinner choseOrganisation;

    private GlobalConfigs globalConfigs;

    private static final int PLACE_HOLD = 0;

    private static final int DETAILS = 1;

    private static final int BOOK_BAG = 2;

    private TextView searchResultsNumber;

    private ArrayList<BookBag> bookBags;

    private Integer bookbag_selected = -1;

    private final ImageDownloader imageDownloader = new ImageDownloader();

    private Runnable searchForResultsRunnable = null;

    private View searchOptionsMenu = null;

    private Button advancedSearchButton = null;

    private Button libraryHoursButton = null;

    private Button preferenceButton = null;

    private Button barcodeScanButton = null;

    private Button homeButton = null;

    private Button myAccountButton = null;

    private String advancedSearchString = null;

    // marks when the fetching record thread is started
    private boolean loadingElements = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SplashActivity.isAppInitialized()) {
            SplashActivity.restartApp(this);
            return;
        }

        setContentView(R.layout.search_result_list);
        setTitle("Browse catalog");

        myAccountButton = (Button) findViewById(R.id.my_account_button);

        myAccountButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(),
                        AccountScreenDashboard.class);
                startActivity(intent);
            }
        });

        homeButton = (Button) findViewById(R.id.library_logo);

        homeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                searchOptionsMenu.setVisibility(View.VISIBLE);
                searchResultsNumber.setVisibility(View.INVISIBLE);
            }
        });
        // end header portion actions
        
        advancedSearchButton = (Button) findViewById(R.id.menu_advanced_search_button);

        advancedSearchButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // show advanced view dialog
                Intent advancedSearch = new Intent(context,
                        AdvancedSearchActivity.class);
                startActivityForResult(advancedSearch, 2);
            }
        });
        // get bookbags
        bookBags = AccountAccess.getAccountAccess().getBookbags();

        libraryHoursButton = (Button) findViewById(R.id.library_hours_button);
        libraryHoursButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
            }
        });

        preferenceButton = (Button) findViewById(R.id.preference_button);
        preferenceButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(),
                        ApplicationPreferences.class);
                startActivity(intent);
            }
        });

        barcodeScanButton = (Button) findViewById(R.id.barcode_scan_button);
        barcodeScanButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent barcodeScan = new Intent(getApplicationContext(),
                        CaptureActivity.class);
                startActivityForResult(barcodeScan, 10);
            }
        });
        // singleton initialize necessary IDL and Org data
        globalConfigs = GlobalConfigs.getGlobalConfigs(this);

        context = this;
        search = SearchCatalog
                .getInstance((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE));

        recordList = new ArrayList<RecordInfo>();

        // Create a customized ArrayAdapter
        adapter = new SearchArrayAdapter(getApplicationContext(),
                R.layout.search_result_item, recordList);

        searchResultsNumber = (TextView) findViewById(R.id.search_result_number);

        // Get reference to ListView holder
        lv = (ListView) this.findViewById(R.id.search_results_list);

        searchOptionsMenu = findViewById(R.id.search_preference_options);

        progressDialog = new ProgressDialog(context);

        // Set the ListView adapter
        lv.setAdapter(adapter);

        searchResults = new ArrayList<RecordInfo>();

        registerForContextMenu(lv);

        searchForResultsRunnable = new Runnable() {

            @Override
            public void run() {

                final String text = searchText.getText().toString();

                if (text.length() < 1)
                    return;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(
                                searchText.getWindowToken(), 0);

                        searchOptionsMenu.setVisibility(View.GONE);
                        searchResultsNumber.setVisibility(View.VISIBLE);

                        progressDialog = ProgressDialog.show(
                                context,
                                getResources().getText(
                                        R.string.dialog_please_wait),
                                getResources().getText(
                                        R.string.dialog_fetching_data_message));
                    }
                });

                searchResults = search.getSearchResults(text, 0);

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        recordList.clear();

                        if (searchResults.size() > 0) {

                            for (int j = 0; j < searchResults.size(); j++)
                                recordList.add(searchResults.get(j));

                            // add extra record to display more option button
                            /*
                             * if (search.visible > recordList.size()) {
                             * recordList.add(new RecordInfo());
                             * searchResultsNumber.setText(+recordList.size() -
                             * 1 + " out of " + search.visible); } else
                             */
                        }
                        searchResultsNumber.setText(+recordList.size()
                                + " out of " + search.visible);

                        adapter.notifyDataSetChanged();
                        progressDialog.dismiss();

                    }
                });

            }
        };

        lv.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1,
                    int position, long arg3) {

                RecordInfo info = (RecordInfo) lv.getItemAtPosition(position);

                if (info.dummy == true) {
                    // this is the more view item button
                    progressDialog = new ProgressDialog(context);

                    progressDialog.setMessage("Fetching data");
                    progressDialog.show();
                    final String text = searchText.getText().toString();

                    Thread searchThreadwithOffset = new Thread(new Runnable() {

                        @Override
                        public void run() {

                            searchResults.clear();

                            searchResults = search.getSearchResults(text,
                                    recordList.size());

                            runOnUiThread(new Runnable() {

                                @Override
                                public void run() {

                                    // don't clear record list
                                    // recordList.clear();
                                    if (searchResults.size() > 0) {

                                        // remove previous more button
                                        recordList.remove(recordList.size() - 1);

                                        for (int j = 0; j < searchResults
                                                .size(); j++)
                                            recordList.add(searchResults.get(j));

                                        // add extra record to display more
                                        // option button
                                        if (search.visible > recordList.size()) {
                                            recordList.add(new RecordInfo());
                                            searchResultsNumber.setText(adapter
                                                    .getCount()
                                                    - 1
                                                    + " out of "
                                                    + search.visible);
                                        } else
                                            searchResultsNumber.setText(adapter
                                                    .getCount()
                                                    + " out of "
                                                    + search.visible);
                                    } else {
                                        searchResultsNumber.setText(adapter
                                                .getCount()
                                                + " out of "
                                                + search.visible);
                                    }
                                    adapter.notifyDataSetChanged();
                                    progressDialog.dismiss();
                                }
                            });

                        }
                    });

                    searchThreadwithOffset.start();
                } else {
                    // start activity with book details

                    Intent intent = new Intent(getBaseContext(),
                            SampleUnderlinesNoFade.class);
                    // serialize object and pass it to next activity
                    intent.putExtra("recordInfo", info);
                    intent.putExtra("orgID", search.selectedOrganization.id);
                    intent.putExtra("depth",
                            (search.selectedOrganization.level - 1));

                    intent.putExtra("recordList", recordList);
                    intent.putExtra("recordPosition", position);
                    startActivityForResult(intent, 10);
                }
            }
        });

        lv.setOnScrollListener(new OnScrollListener() {

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                    int visibleItemCount, int totalItemCount) {
                // TODO Auto-generated method stub

                if (!loadingElements) {

                    Log.d(TAG, " Scroll adapter " + totalItemCount + " "
                            + visibleItemCount + " " + firstVisibleItem + " "
                            + adapter.getCount() + " " + search.visible);
                    if (totalItemCount > 0
                            && (((totalItemCount - visibleItemCount) <= (firstVisibleItem)) && adapter
                                    .getCount() < search.visible)) {
                        loadingElements = true;
                        Log.d(TAG, "Load more data");
                        progressDialog = new ProgressDialog(context);

                        progressDialog.setMessage(getResources().getText(
                                R.string.dialog_load_more_message));
                        progressDialog.show();

                        Thread searchThreadwithOffset = new Thread(
                                new Runnable() {

                                    @Override
                                    public void run() {

                                        String text = searchText.getText()
                                                .toString();
                                        searchResults.clear();

                                        searchResults = search
                                                .getSearchResults(text,
                                                        adapter.getCount());

                                        runOnUiThread(new Runnable() {

                                            @Override
                                            public void run() {

                                                // don't clear record list
                                                // recordList.clear();
                                                System.out.println("Returned "
                                                        + searchResults.size()
                                                        + " elements from search");
                                                if (searchResults.size() > 0) {

                                                    for (int j = 0; j < searchResults
                                                            .size(); j++)
                                                        recordList
                                                                .add(searchResults
                                                                        .get(j));

                                                }

                                                searchResultsNumber.setText(adapter
                                                        .getCount()
                                                        + " out of "
                                                        + search.visible);

                                                adapter.notifyDataSetChanged();
                                                progressDialog.dismiss();
                                                loadingElements = false;
                                            }
                                        });

                                    }
                                });

                        searchThreadwithOffset.start();
                    }
                }
            }
        });

        searchText = (EditText) findViewById(R.id.searchText);

        // enter key now is labeled "Search" on virtual keyboard
        searchText.setImeActionLabel("Search", EditorInfo.IME_ACTION_SEARCH);
        searchText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        // enter key on virtual keyboard starts the search
        searchText.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN)
                        && ((keyCode == KeyEvent.KEYCODE_ENTER) || keyCode == EditorInfo.IME_ACTION_SEARCH)) {
                    // Perform action on key press
                    Thread searchThread = new Thread(searchForResultsRunnable);
                    searchThread.start();
                    return true;
                }
                return false;
            }
        });


        searchButton = (ImageButton) findViewById(R.id.searchButton);
        searchButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Thread searchThread = new Thread(searchForResultsRunnable);
                searchThread.start();
            }
        });

        int selectedPos = 0;
        int homeLibrary = 0;
        if (AccountAccess.getAccountAccess() != null) {
            homeLibrary = AccountAccess.getAccountAccess().getHomeLibraryID();
        }
        ArrayList<String> list = new ArrayList<String>();
        Log.d("kcxxx", "Org scanning ...");
        if (globalConfigs.organisations != null) {
            for (int i = 0; i < globalConfigs.organisations.size(); i++) {
                Organisation org = globalConfigs.organisations.get(i);
                list.add(org.padding + org.name);
                if (org.id == homeLibrary)
                    selectedPos = i;
            }
        }
        Log.d("kcxxx", "Org scanning ...done");
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                layout.spinner_layout, list);
        choseOrganisation = (Spinner) findViewById(R.id.chose_organisation);
        choseOrganisation.setAdapter(adapter);
        choseOrganisation.setSelection(selectedPos);
        choseOrganisation
                .setOnItemSelectedListener(new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> arg0, View arg1,
                            int ID, long arg3) {
                        // select the specific organization
                        search.selectOrganisation(globalConfigs.organisations
                                .get(ID));
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> arg0) {

                    }

                });

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {

        Log.d(TAG, "context menu");
        if (v.getId() == R.id.search_results_list) {

            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            menu.setHeaderTitle("Options");

            menu.add(Menu.NONE, DETAILS, 0, "Details");
            menu.add(Menu.NONE, PLACE_HOLD, 1, "Place Hold");
            menu.add(Menu.NONE, BOOK_BAG, 2, "Add to bookbag");

        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo menuArrayItem = (AdapterView.AdapterContextMenuInfo) item
                .getMenuInfo();
        int menuItemIndex = item.getItemId();

        final RecordInfo info = (RecordInfo) lv
                .getItemAtPosition(menuArrayItem.position);
        // start activity with book details

        switch (item.getItemId()) {

        case DETAILS: {

            Intent intent = new Intent(getBaseContext(),
                    SampleUnderlinesNoFade.class);
            // serialize object and pass it to next activity
            intent.putExtra("recordInfo", info);
            intent.putExtra("orgID", search.selectedOrganization.id);
            intent.putExtra("depth", (search.selectedOrganization.level - 1));

            intent.putExtra("recordList", recordList);
            // TODO put total number
            intent.putExtra("recordPosition", menuArrayItem.position);
            startActivity(intent);
        }
            break;
        case PLACE_HOLD: {

            Intent intent = new Intent(getBaseContext(), PlaceHold.class);

            intent.putExtra("recordInfo", info);

            startActivity(intent);
        }
            break;
        case BOOK_BAG: {

            if (bookBags.size() > 0) {
                String array_spinner[] = new String[bookBags.size()];

                for (int i = 0; i < array_spinner.length; i++)
                    array_spinner[i] = bookBags.get(i).name;

                AlertDialog.Builder builder;

                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService(LAYOUT_INFLATER_SERVICE);
                View layout = inflater.inflate(R.layout.bookbag_spinner, null);

                Spinner s = (Spinner) layout.findViewById(R.id.bookbag_spinner);

                Button add = (Button) layout
                        .findViewById(R.id.add_to_bookbag_button);
                ArrayAdapter adapter = new ArrayAdapter(context,
                        android.R.layout.simple_spinner_item, array_spinner);

                s.setAdapter(adapter);
                builder = new AlertDialog.Builder(context);
                builder.setView(layout);
                final AlertDialog alertDialog = builder.create();

                add.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // TODO Auto-generated method stub
                        Thread addtoBookbag = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                AccountAccess ac = AccountAccess
                                        .getAccountAccess();
                                try {
                                    ac.addRecordToBookBag(info.doc_id,
                                            bookBags.get(bookbag_selected).id);
                                } catch (SessionNotFoundException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        progressDialog.dismiss();
                                        alertDialog.dismiss();
                                    }
                                });

                            }
                        });
                        progressDialog = ProgressDialog.show(context,
                                "Please wait", "Adding to bookbag");
                        addtoBookbag.start();

                    }
                });
                alertDialog.show();

                s.setOnItemSelectedListener(new OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> arg0, View arg1,
                            int position, long arg3) {
                        bookbag_selected = position;
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> arg0) {
                    }

                });
            } else
                Toast.makeText(context, "No bookbags", Toast.LENGTH_SHORT)
                        .show();
        }
            break;
        }

        return super.onContextItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (resultCode) {

        case SampleUnderlinesNoFade.RETURN_DATA : {
            
            ArrayList<RecordInfo> records = (ArrayList)data.getSerializableExtra("recordList");
        
            recordList.clear();
            for(int i=0;i<records.size();i++){
                recordList.add(records.get(i));
            }
            adapter.notifyDataSetChanged();
            
            searchResultsNumber.setText(adapter
                    .getCount()
                    + " out of "
                    + search.visible);
        }
        break;
        
        case AdvancedSearchActivity.RESULT_ADVANCED_SEARCH: {
            Log.d(TAG,
                    "result text" + data.getStringExtra("advancedSearchText"));
            searchText.setText(data.getStringExtra("advancedSearchText"));
            Thread searchThread = new Thread(searchForResultsRunnable);
            searchThread.start();
        }
            break;

        case CaptureActivity.BARCODE_SEARCH: {
            searchText.setText("identifier|isbn: "
                    + data.getStringExtra("barcodeValue"));
            Thread searchThread = new Thread(searchForResultsRunnable);
            searchThread.start();
        }

        }
    }

    class SearchArrayAdapter extends ArrayAdapter<RecordInfo> {

        private static final String tag = "SearchArrayAdapter";
        private Context context;
        private ImageView recordImage;
        private TextView recordTitle;
        private TextView recordAuthor;
        private TextView recordPublisher;

        private List<RecordInfo> records = new ArrayList<RecordInfo>();

        public SearchArrayAdapter(Context context, int textViewResourceId,
                List<RecordInfo> objects) {
            super(context, textViewResourceId, objects);
            this.context = context;
            this.records = objects;
        }

        public int getCount() {
            return this.records.size();
        }

        public RecordInfo getItem(int index) {
            return this.records.get(index);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;

            // Get item
            RecordInfo record = getItem(position);

                // if it is the right type of view
                if (row == null
                        || row.findViewById(R.id.search_record_title) == null) {

                    Log.d(tag, "Starting XML Row Inflation ... ");
                    LayoutInflater inflater = (LayoutInflater) this
                            .getContext().getSystemService(
                                    Context.LAYOUT_INFLATER_SERVICE);
                    row = inflater.inflate(R.layout.search_result_item, parent,
                            false);
                    Log.d(tag, "Successfully completed XML Row Inflation!");

                }

                Log.d(TAG, "reord image value " + recordImage);
                // Get reference to ImageView
                recordImage = (ImageView) row
                        .findViewById(R.id.search_record_img);
                String imageHref = GlobalConfigs.httpAddress
                        + "/opac/extras/ac/jacket/small/" + record.isbn;
                // start async download of image
                imageDownloader.download(imageHref, recordImage);
                // Get reference to TextView - title
                recordTitle = (TextView) row
                        .findViewById(R.id.search_record_title);

                // Get reference to TextView - author
                recordAuthor = (TextView) row
                        .findViewById(R.id.search_record_author);

                // Get referance to TextView - record Publisher date+publisher
                recordPublisher = (TextView) row
                        .findViewById(R.id.search_record_publishing);

                // set text
                recordTitle.setText(record.title);
                recordAuthor.setText(record.author);
                recordPublisher
                        .setText(record.pubdate + " " + record.publisher);
            
            return row;
        }
    }
}
