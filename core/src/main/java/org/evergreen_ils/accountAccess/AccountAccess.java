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
package org.evergreen_ils.accountAccess;

import android.app.Activity;
import android.text.TextUtils;

import org.evergreen_ils.Api;
import org.evergreen_ils.Result;
import org.evergreen_ils.accountAccess.bookbags.BookBag;
import org.evergreen_ils.accountAccess.bookbags.BookBagItem;
import org.evergreen_ils.accountAccess.checkout.CircRecord;
import org.evergreen_ils.accountAccess.holds.HoldRecord;
import org.evergreen_ils.android.AccountUtils;
import org.evergreen_ils.android.App;
import org.evergreen_ils.auth.Const;
import org.evergreen_ils.data.Account;
import org.evergreen_ils.net.Gateway;
import org.evergreen_ils.searchCatalog.RecordInfo;
import org.evergreen_ils.system.Analytics;
import org.evergreen_ils.system.Log;
import org.evergreen_ils.system.Utils;
import org.opensrf.ShouldNotHappenException;
import org.opensrf.net.http.HttpConnection;
import org.opensrf.util.GatewayResult;
import org.opensrf.util.OSRFObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountAccess {

    private final static String TAG = AccountAccess.class.getSimpleName();

    // Used for book bags
    public static String CONTAINER_CLASS_BIBLIO = "biblio";
    public static String CONTAINER_BUCKET_TYPE_BOOKBAG = "bookbag";

    private static AccountAccess mInstance = null;

    private ArrayList<BookBag> bookBags = new ArrayList<>();

    private void clearSession() {
        bookBags = new ArrayList<>();
    }

    private AccountAccess() {
    }

    public static AccountAccess getInstance() {
        if (mInstance == null)
            mInstance = new AccountAccess();
        return mInstance;
    }

    private HttpConnection conn() {
        return Gateway.INSTANCE.getConn();
    }

    /** invalidate current auth token and get a new one
     *
     * @param activity
     * @return true if auth successful
     */
    public boolean reauthenticate(Activity activity) {
        Log.d(Const.AUTH_TAG, "reauthenticate");
        Account account = App.getAccount();
        AccountUtils.invalidateAuthToken(activity, account.getAuthToken());
        App.getAccount().setAuthToken(null);

        try {
            String auth_token = AccountUtils.getAuthTokenForAccount(activity, account.getUsername());
            if (TextUtils.isEmpty(auth_token))
                return false;
            account.clearAuthToken();
            return true;
        } catch (Exception e) {
            Log.d(Const.AUTH_TAG, "[auth] reauthenticate exception", e);
            return false;
        }
    }

    // ------------------------Checked Out Items Section
    // -------------------------//

    private CircRecord fleshCircRecord(String id, CircRecord.CircType circType) throws SessionNotFoundException {
        GatewayResult response = retrieveCircRecord(id);
        if (response.failed) {
            // PINES Crash #23
            Analytics.logException(new ShouldNotHappenException("failed circ retrieve, type:" + circType + " desc:" + response.errorMessage));
            return null;
        }
        OSRFObject circ = (OSRFObject) response.payload;
        CircRecord circRecord = new CircRecord(circ, circType, Integer.parseInt(id));
        fetchInfoForCheckedOutItem(circ.getInt("target_copy"), circRecord);
        return circRecord;
    }

    /**
     * Gets the items checked out.
     *
     * @return the items checked out
     * @throws SessionNotFoundException the session not found exception
     */
    public ArrayList<CircRecord> getItemsCheckedOut()
            throws SessionNotFoundException {

        ArrayList<CircRecord> circRecords = new ArrayList<CircRecord>();

        Account account = App.getAccount();
        Object resp = Utils.doRequest(conn(), Api.ACTOR,
                Api.CHECKED_OUT, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), account.getId() });
        if (resp == null)
            return circRecords;
        Map<String, ?> resp_map = ((Map<String, ?>) resp);

        // out => list_of_strings_or_integers
        List<String> ids = Api.parseIdsList(resp_map.get("out"));
        for (String id: ids) {
            CircRecord circRecord = fleshCircRecord(id, CircRecord.CircType.OUT);
            if (circRecord != null)
                circRecords.add(circRecord);
        }

        ids = Api.parseIdsList(resp_map.get("overdue"));
        for (String id: ids) {
            CircRecord circRecord = fleshCircRecord(id, CircRecord.CircType.OVERDUE);
            if (circRecord != null)
                circRecords.add(circRecord);
        }

        // todo handle other circ types LONG_OVERDUE, LOST, CLAIMS_RETURNED ?
        // resp_map.get("long_overdue")
        // resp_map.get("lost")
        // resp_map.get("claims_returned")

        Collections.sort(circRecords, new Comparator<CircRecord>() {
            @Override
            public int compare(CircRecord lhs, CircRecord rhs) {
                return lhs.getDueDate().compareTo(rhs.getDueDate());
            }
        });

        return circRecords;
    }

    /*
     * Retrieves the Circ record
     * 
     * @param : target_copy from circ
     * 
     * @returns : "circ" OSRFObject
     */
    /**
     * Retrieve circ record.
     *
     * @param id the id
     * @return the oSRF object
     * @throws SessionNotFoundException the session not found exception
     */
    private GatewayResult retrieveCircRecord(String id)
            throws SessionNotFoundException {

        Account account = App.getAccount();
        Object resp = Utils.doRequest(conn(), Api.CIRC,
                Api.CIRC_RETRIEVE, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), id });
        return GatewayResult.createFromObject(resp);
    }

    /**
     * Fetch info for checked out item.
     *
     * @param target_copy the target_copy
     * @param circRecord the circ record
     */
    private void fetchInfoForCheckedOutItem(Integer target_copy, CircRecord circRecord) {

        if (target_copy == null)
            return;

        OSRFObject info_mvr = fetchModsFromCopy(target_copy);

        try {
            circRecord.recordInfo = new RecordInfo(info_mvr);
            circRecord.recordInfo.updateFromMRAResponse(fetchRecordAttributes(info_mvr.getInt("doc_id")));
        } catch (Exception e) {
            Log.d(TAG, "caught", e);
        }

        // if title or author not inserted, request acp with copy_target
        // old comment I don't get: the logic to establish mvr or acp is copied from the opac
        circRecord.mvr = info_mvr;
        if (info_mvr == null
            || info_mvr.getString("title") == null
            || info_mvr.getString("author") == null)
        {
            circRecord.acp = fetchAssetCopy(target_copy);
        }
    }

    /**
     * Fetch mods from copy.
     *
     * @param target_copy the target_copy
     * @return the oSRF object
     */
    private OSRFObject fetchModsFromCopy(Integer target_copy) {
        OSRFObject mvr = (OSRFObject) Utils.doRequest(conn(), Api.SEARCH,
                Api.MODS_FROM_COPY, new Object[] { target_copy });

        return mvr;
    }

    public OSRFObject fetchRecordAttributes(int id) {
        return fetchRecordAttributes(Integer.valueOf(id).toString());
    }

    public OSRFObject fetchRecordAttributes(String id) {
        // This can happen when looking up checked out item borrowed from another system.
        if (id.equals("-1"))
            return null;

        Account account = App.getAccount();
        OSRFObject resp = null;
        try {
            resp = (OSRFObject) Utils.doRequest(conn(), Api.PCRUD,
                    Api.RETRIEVE_MRA, Api.ANONYMOUS, new Object[] {
                            account.getAuthToken(), id});
        } catch (SessionNotFoundException e) {
            return null;
        }
        return resp;
    }

    /**
     * Fetch asset copy.
     *
     * @param target_copy the target_copy
     * @return the oSRF object
     */
    private OSRFObject fetchAssetCopy(Integer target_copy) {
        OSRFObject acp = (OSRFObject) Utils.doRequest(conn(), Api.SEARCH,
                Api.ASSET_COPY_RETRIEVE, new Object[] { target_copy });

        return acp;
    }

    /**
     * Renew circ.
     *
     * @param target_copy the target_copy
     * @throws SessionNotFoundException the session not found exception
     */
    public GatewayResult renewCirc(Integer target_copy) throws SessionNotFoundException {

        Account account = App.getAccount();
        HashMap<String, Integer> param = new HashMap<>();
        param.put("patron", account.getId());
        param.put("copyid", target_copy);
        param.put("opac_renewal", 1);

        Object resp = Utils.doRequest(conn(), Api.CIRC,
                Api.CIRC_RENEW, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), param });

        return GatewayResult.createFromObject(resp);
    }

    // ------------------------Holds Section
    // --------------------------------------//

    /**
     * Gets the holds.
     *
     * @return the holds
     * @throws SessionNotFoundException the session not found exception
     */
    public List<HoldRecord> getHolds() throws SessionNotFoundException {

        ArrayList<HoldRecord> holds = new ArrayList<HoldRecord>();
        Account account = App.getAccount();

        Object resp = Utils.doRequest(conn(), Api.CIRC,
                Api.HOLDS_RETRIEVE, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), account.getId() });
        if (resp == null) {
            Log.d(TAG, "Result: null");
            return holds;
        }

        List<OSRFObject> listHoldsAhr = (List<OSRFObject>) resp;
        for (OSRFObject ahr_obj: listHoldsAhr) {
            HoldRecord hold = new HoldRecord(ahr_obj);
            fetchHoldTargetDetails(hold);
            fetchHoldQueueStats(hold);
            if (hold.recordInfo != null)
                hold.recordInfo.updateFromMRAResponse(fetchRecordAttributes(hold.target));
            holds.add(hold);
            Log.d(TAG, "hold email="+hold.email_notify+" phone_notify="+hold.phone_notify+" sms_notify="+hold.sms_notify+" title="+hold.title);
        }
        return holds;
    }

    private void fetchHoldTargetDetails(HoldRecord hold) {

        String holdType = (String) hold.ahr.get("hold_type");
        Integer target = hold.ahr.getInt("target");
        String method = null;

        OSRFObject holdInfo = null;
        if (holdType.equals("T") || holdType.equals("M")) {
            if (holdType.equals("M"))
                method = Api.METARECORD_MODS_SLIM_RETRIEVE;
            else //(holdType.equals("T"))
                method = Api.MODS_SLIM_RETRIEVE;
            holdInfo = (OSRFObject) Utils.doRequest(conn(), Api.SEARCH,
                    method, new Object[] {
                            target });

            // lame guard against Issue #6
            if (holdInfo == null) {
                hold.title = "Unknown Title";
                hold.author = "";
                Analytics.logException(new ShouldNotHappenException(6, "null holdInfo, ahr="+hold.ahr));
            } else {
                hold.title = holdInfo.getString("title");
                hold.author = holdInfo.getString("author");
            }
            hold.recordInfo = new RecordInfo(holdInfo);
        } else {
            fetchHoldTargetDetailsOthers(hold);
        }
    }

    private void fetchHoldTargetDetailsOthers(HoldRecord holdObj) {

        String type = (String) holdObj.ahr.get("hold_type");

        Log.d(TAG, "Hold Type " + type);
        if (type.equals("C")) {

            /*
             * steps asset.copy'->'asset.call_number'->'biblio.record_entry' or,
             * in IDL ids, acp->acn->bre
             */

            // fetch_copy
            OSRFObject copyObject = fetchAssetCopy(holdObj.ahr.getInt("target"));
            // fetch_volume from copyObject.call_number field
            Integer call_number = null;
            if (copyObject != null) call_number = copyObject.getInt("call_number");

            if (call_number != null) {

                OSRFObject volume = (OSRFObject) Utils.doRequest(conn(), Api.SEARCH,
                        Api.ASSET_CALL_NUMBER_RETRIEVE, new Object[] {
                                copyObject.getInt("call_number") });
                // in volume object : record
                Integer record = volume.getInt("record");

                // part label
                holdObj.part_label = volume.getString("label");

                Log.d(TAG, "Record " + record);
                OSRFObject holdInfo = (OSRFObject) Utils.doRequest(conn(),
                        Api.SEARCH, Api.MODS_SLIM_RETRIEVE,
                        new Object[] { record });

                holdObj.title = holdInfo.getString("title");
                holdObj.author = holdInfo.getString("author");
                holdObj.recordInfo = new RecordInfo(holdInfo);
            }
        } else if (type.equals("V")) {
            // fetch_volume
            OSRFObject volume = (OSRFObject) Utils.doRequest(conn(),
                    Api.SEARCH, Api.ASSET_CALL_NUMBER_RETRIEVE,
                    new Object[] { holdObj.ahr.getInt("target") });
            // in volume object : record

            // in volume object : record
            Integer record = volume.getInt("record");

            // part label
            holdObj.part_label = volume.getString("label");

            Log.d(TAG, "Record " + record);
            OSRFObject holdInfo = (OSRFObject) Utils.doRequest(conn(),
                    Api.SEARCH, Api.MODS_SLIM_RETRIEVE,
                    new Object[] { record });

            holdObj.title = holdInfo.getString("title");
            holdObj.author = holdInfo.getString("author");
            holdObj.recordInfo = new RecordInfo(holdInfo);
        } else if (type.equals("I")) {
            OSRFObject issuance = (OSRFObject) Utils.doRequest(conn(),
                    Api.SERVICE_SERIAL, Api.METHOD_FETCH_ISSUANCE,
                    new Object[] { holdObj.ahr.getInt("target") });
            // TODO

        } else if (type.equals("P")) {
            HashMap<String, Object> param = new HashMap<String, Object>();

            param.put("cache", 1);

            ArrayList<String> fieldsList = new ArrayList<String>();
            fieldsList.add("label");
            fieldsList.add("record");

            param.put("fields", fieldsList);
            HashMap<String, Integer> queryParam = new HashMap<String, Integer>();
            // PART_ID use "target field in hold"
            queryParam.put("id", holdObj.ahr.getInt("target"));
            param.put("query", queryParam);

            // returns [{record:id, label=part label}]

            List<Object> part = (List<Object>) Utils.doRequest(conn(),
                    Api.FIELDER, Api.FIELDER_BMP_ATOMIC,
                    new Object[] { param });

            Map<String, ?> partObj = (Map<String, ?>) part.get(0);

            Integer recordID = (Integer) partObj.get("record");
            String part_label = (String) partObj.get("label");

            OSRFObject holdInfo = (OSRFObject) Utils.doRequest(conn(),
                    Api.SEARCH, Api.MODS_SLIM_RETRIEVE,
                    new Object[] { recordID });

            holdObj.part_label = part_label;
            holdObj.title = holdInfo.getString("title");
            holdObj.author = holdInfo.getString("author");
            holdObj.recordInfo = new RecordInfo(holdInfo);
        }
    }

    public void fetchHoldQueueStats(HoldRecord hold)
            throws SessionNotFoundException {

        Account account = App.getAccount();
        Integer hold_id = hold.ahr.getInt("id");
        Object resp = Utils.doRequest(conn(), Api.CIRC,
                Api.HOLD_QUEUE_STATS, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), hold_id });
        hold.setQueueStats(resp);
    }

    /**
     * Cancel hold.
     *
     * @param hold the hold
     * @return true, if successful
     * @throws SessionNotFoundException the session not found exception
     */
    public boolean cancelHold(OSRFObject hold) throws SessionNotFoundException {
        Integer hold_id = hold.getInt("id");
        Account account = App.getAccount();
        String note = "Cancelled by mobile app";

        Object response = Utils.doRequest(conn(), Api.CIRC,
                Api.HOLD_CANCEL, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), hold_id, null, note });
        if (response != null && response.toString().equals("1"))
            return true;
        return false;
    }

    /**
     * Update hold.
     *
     * @param ahr the ahr
     * @param pickup_lib the pickup_lib
     * @param suspendHold the suspend hold
     * @param expire_time the expire_time
     * @param thaw_date the thaw_date
     * @return the object
     * @throws SessionNotFoundException the session not found exception
     */
    public Object updateHold(OSRFObject ahr, Integer pickup_lib,
            boolean suspendHold, String expire_time, String thaw_date)
            throws SessionNotFoundException {

        ahr.put("pickup_lib", pickup_lib);
        ahr.put("expire_time", expire_time);
        ahr.put("frozen", suspendHold);
        ahr.put("thaw_date", thaw_date);
        Account account = App.getAccount();

        Object response = Utils.doRequest(conn(), Api.CIRC,
                Api.HOLD_UPDATE, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), ahr });

        return response;
    }

    public Result testAndCreateHold(Integer recordID, Integer pickup_lib,
                                    boolean email_notify, String phone_notify,
                                    String sms_notify, Integer sms_carrier_id,
                                    String expire_time, boolean suspendHold, String thaw_date)
            throws SessionNotFoundException {
        Account account = App.getAccount();
        /*
        The named fields in the hash are:

        patronid     - ID of the hold recipient  (required)
        depth        - hold range depth          (default 0)
        pickup_lib   - destination for hold, fallback value for selection_ou
        selection_ou - ID of org_unit establishing hard and soft hold boundary settings
        issuanceid   - ID of the issuance to be held, required for Issuance level hold
        partid       - ID of the monograph part to be held, required for monograph part level hold
        titleid      - ID (BRN) of the title to be held, required for Title level hold
        volume_id    - required for Volume level hold
        copy_id      - required for Copy level hold
        mrid         - required for Meta-record level hold
        hold_type    - T, C (or R or F), I, V or M for Title, Copy, Issuance, Volume or Meta-record  (default "T")
         */
        HashMap<String, Object> args = new HashMap<>();
        args.put("patronid", account.getId());
        args.put("pickup_lib", pickup_lib);
        args.put("titleid", recordID);//is this required?
        args.put("hold_type", "T");
        args.put("email_notify", email_notify);
        args.put("expire_time", expire_time);
        if (!TextUtils.isEmpty(phone_notify))
            args.put("phone_notify", phone_notify);
        if (sms_carrier_id != null && !TextUtils.isEmpty(sms_notify)) {
            args.put("sms_carrier", sms_carrier_id);
            args.put("sms_notify", sms_notify);
        }
        if (suspendHold && thaw_date != null) {
            args.put("frozen", suspendHold);
            args.put("thaw_date", thaw_date);
        }

        ArrayList<Integer> ids = new ArrayList<>(1);
        ids.add(recordID);

        Object resp = Utils.doRequest(conn(), Api.CIRC,
                Api.HOLD_TEST_AND_CREATE, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), args, ids });

        Result result_obj = Result.createUnknownError();
        try {
            Map<String, ?> resp_map = ((Map<String, ?>) resp);
            Object result = resp_map.get("result");
            if (result instanceof Integer) {
                Integer hold_id = (Integer) result;
                if (hold_id > -1) {
                    result_obj = Result.createFromSuccess(result);
                }
            } else if (result instanceof List) {
                // List of error events
                List<?> l = (List<?>) result;
                result_obj = Result.createFromEvent(l.get(0));
            } else if (result instanceof Map) {
                Map<String, ?> result_map = (Map<String, ?>) result;
                result_obj = Result.createFromEvent(result_map.get("last_event"));
            } else {
                Log.d(TAG, "unknown response from test_and_create: "+result);
            }
        } catch (Exception e) {
            Log.d(TAG, "caught", e);
        }

        return result_obj;
    }

    // ---------------------------------------Book
    // bags-----------------------------------//

    /**
     * Retrieve bookbags from the server.
     *
     * @return the bookbags
     * @throws SessionNotFoundException the session not found exception
     */
    // todo: load on demand.  It takes ~750ms to load my 4 bookbags on startup.
    public boolean retrieveBookbags() throws SessionNotFoundException {
        Account account = App.getAccount();

        Object response = Utils.doRequest(conn(), Api.ACTOR,
                Api.CONTAINERS_BY_CLASS, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), account.getId(), CONTAINER_CLASS_BIBLIO, CONTAINER_BUCKET_TYPE_BOOKBAG });

        List<OSRFObject> bookbags = (List<OSRFObject>) response;

        ArrayList<BookBag> bookBagObj = new ArrayList<BookBag>();
        // in order to refresh bookbags
        this.bookBags = bookBagObj;

        if (bookbags == null)
            return true;

        for (int i = 0; i < bookbags.size(); i++) {

            BookBag bag = new BookBag(bookbags.get(i));
            getBookbagContent(bag, bookbags.get(i).getInt("id"));

            bookBagObj.add(bag);
        }

        Collections.sort(this.bookBags, new Comparator<BookBag>() {
            @Override
            public int compare(BookBag lhs, BookBag rhs) {
                return lhs.name.compareTo(rhs.name);
            }
        });

        return true;
    }
    
    public ArrayList<BookBag> getBookbags() {
        return this.bookBags;
    }

    /**
     * Gets the bookbag content.
     *
     * @param bag the bag
     * @param bookbagID the bookbag id
     * @return the bookbag content
     * @throws SessionNotFoundException the session not found exception
     */
    private Object getBookbagContent(BookBag bag, Integer bookbagID)
            throws SessionNotFoundException {
        Account account = App.getAccount();

        Map<String, ?> map = (Map<String, ?>) Utils.doRequest(conn(), Api.ACTOR,
                Api.CONTAINER_FLESH, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), CONTAINER_CLASS_BIBLIO, bookbagID });
        
        List<OSRFObject> items  = new ArrayList<OSRFObject>();
        
        try{
            items = (List<OSRFObject>) map.get("items");
    
            for (int i = 0; i < items.size(); i++) {
    
                BookBagItem bookBagItem = new BookBagItem(items.get(i));
    
                bag.items.add(bookBagItem);
        }

        }catch(Exception e){};
        
        return items;
    }

    /**
     * Removes the bookbag item.
     *
     * @param id the id
     * @throws SessionNotFoundException the session not found exception
     */
    public void removeBookbagItem(Integer id) throws SessionNotFoundException {

        removeContainerItem(CONTAINER_CLASS_BIBLIO, id);

    }

    /**
     * Creates the bookbag.
     *
     * @param name the name
     * @throws SessionNotFoundException the session not found exception
     */
    public void createBookbag(String name) throws SessionNotFoundException {
        Account account = App.getAccount();

        OSRFObject cbreb = new OSRFObject("cbreb");
        cbreb.put("btype", CONTAINER_BUCKET_TYPE_BOOKBAG);
        cbreb.put("name", name);
        cbreb.put("pub", false);
        cbreb.put("owner", account.getId());

        createContainer(CONTAINER_CLASS_BIBLIO, cbreb);
    }

    /**
     * Delete book bag.
     *
     * @param id the id
     * @throws SessionNotFoundException the session not found exception
     */
    public void deleteBookBag(Integer id) throws SessionNotFoundException {
        Account account = App.getAccount();

        Object response = Utils.doRequest(conn(), Api.ACTOR,
                Api.CONTAINER_FULL_DELETE, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), CONTAINER_CLASS_BIBLIO, id });
    }

    /**
     * Adds the record to book bag.
     *
     * @param record_id the record_id
     * @param bookbag_id the bookbag_id
     * @throws SessionNotFoundException the session not found exception
     */
    public void addRecordToBookBag(Integer record_id, Integer bookbag_id)
            throws SessionNotFoundException {
        Account account = App.getAccount();

        OSRFObject cbrebi = new OSRFObject("cbrebi");
        cbrebi.put("bucket", bookbag_id);
        cbrebi.put("target_biblio_record_entry", record_id);
        cbrebi.put("id", null);

        Object response = Utils.doRequest(conn(), Api.ACTOR,
                Api.CONTAINER_ITEM_CREATE, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), CONTAINER_CLASS_BIBLIO, cbrebi });
    }

    /**
     * Removes the container.
     *
     * @param container the container
     * @param id the id
     * @throws SessionNotFoundException the session not found exception
     */
    private void removeContainerItem(String container, Integer id)
            throws SessionNotFoundException {
        Account account = App.getAccount();

        Object response = Utils.doRequest(conn(), Api.ACTOR,
                Api.CONTAINER_ITEM_DELETE, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), container, id });
    }

    /**
     * Creates the container.
     *
     * @param container the container
     * @param parameter the parameter
     * @throws SessionNotFoundException the session not found exception
     */
    private void createContainer(String container, Object parameter)
            throws SessionNotFoundException {
        Account account = App.getAccount();

        Object response = Utils.doRequest(conn(), Api.ACTOR,
                Api.CONTAINER_CREATE, account.getAuthToken(), new Object[] {
                        account.getAuthToken(), container, parameter });
    }

    //todo replace callers of this method with RecordLoader.fetchRecordMODS
    private OSRFObject getItemShortInfo(Integer id) {
        OSRFObject response = (OSRFObject) Utils.doRequest(conn(), Api.SEARCH,
                Api.MODS_SLIM_RETRIEVE, new Object[] {
                        id });
        return response;
    }

    // todo replace callers with RecordLoader
    public ArrayList<RecordInfo> getRecordsInfo(ArrayList<Integer> ids) {

        ArrayList<RecordInfo> recordInfoArray = new ArrayList<RecordInfo>();

        for (int i = 0; i < ids.size(); i++) {
            RecordInfo recordInfo = new RecordInfo(getItemShortInfo(ids.get(i)));
            recordInfoArray.add(recordInfo);
        }

        return recordInfoArray;
    }
}
