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
import org.evergreen_ils.accountAccess.bookbags.BookBag;
import org.evergreen_ils.accountAccess.bookbags.BookBagItem;
import org.evergreen_ils.accountAccess.checkout.CircRecord;
import org.evergreen_ils.accountAccess.fines.FinesRecord;
import org.evergreen_ils.accountAccess.holds.HoldRecord;
import org.evergreen_ils.auth.Const;
import org.evergreen_ils.system.EvergreenServer;
import org.evergreen_ils.system.Log;
import org.evergreen_ils.system.Utils;
import org.evergreen_ils.searchCatalog.RecordInfo;
import org.opensrf.net.http.HttpConnection;
import org.opensrf.util.OSRFObject;

import java.util.*;

/**
 * The Class AuthenticateUser. Singleton class
 */
public class AccountAccess {

    private final static String TAG = AccountAccess.class.getSimpleName();

    // Used for book bags
    public static String CONTAINER_CLASS_BIBLIO = "biblio";
    public static String CONTAINER_BUCKET_TYPE_BOOKBAG = "bookbag";

    private static AccountAccess mInstance = null;

    private String userName = null;
    private String authToken = null;
    private Integer userID = null;
    private String barcode = null;
    private Integer homeLibraryID = null;
    private Integer defaultPickupLibraryID = null;
    private Integer defaultSearchLibraryID = null;
    private ArrayList<BookBag> bookBags = new ArrayList<>();

    private void clearSession() {
        userName = null;
        authToken = null;
        userID = null;
        barcode = null;
        homeLibraryID = null;
        defaultPickupLibraryID = null;
        defaultSearchLibraryID = null;
        bookBags = new ArrayList<>();
    }

    private AccountAccess() {
    }

    public static AccountAccess getInstance() {
        if (mInstance == null)
            mInstance = new AccountAccess();
        return mInstance;
    }

    public String getUserName() { return userName; }

    public Integer getHomeLibraryID() {
        return homeLibraryID;
    }

    public Integer getDefaultPickupLibraryID() {
        if (defaultPickupLibraryID != null)
            return defaultPickupLibraryID;
        return homeLibraryID;
    }

    public Integer getDefaultSearchLibraryID() {
        if (defaultSearchLibraryID != null)
            return defaultSearchLibraryID;
        return homeLibraryID;
    }

    private HttpConnection conn() {
        return EvergreenServer.getInstance().gatewayConnection();
    }

    /**
     * Retrieve session.
     * @throws SessionNotFoundException
     */
    public boolean retrieveSession(String auth_token) throws SessionNotFoundException {
        Log.d(Const.AUTH_TAG, "retrieveSession " + auth_token);
        clearSession();
        this.authToken = auth_token;

        Object resp = Utils.doRequest(conn(), Api.AUTH,
                Api.AUTH_SESSION_RETRIEVE, auth_token, new Object[]{
                        authToken});
        if (resp != null) {
            OSRFObject au = (OSRFObject) resp;
            userID = au.getInt("id");
            homeLibraryID = au.getInt("home_ou");
            userName = au.getString("usrname");
            //email = au.getString("email");
            // todo: warn when account is nearing expiration
            //expireDate = Api.parseDate(au.getString("expire_date"));

            fleshUserSettings();
            return true;
        }
        throw new SessionNotFoundException();
    }

    // This could be done on demand, but coming in at ~75ms it is not worth it
    public void fleshUserSettings() {

        try {
            // How to get a patron's library card barcode:
            //
            // * open-ils.actor open-ils.actor.user.fleshed.retrieve(auth_token, userId, [opt_array_of_fields])
            // requires classes au,aua,ac,auact,cuat
            // "card" has the active card object with "barcode" key
            // "cards" has an array of cards including expired ones with active=f
            // "settings" - array of settings objects, e.g. name=opac.hold_notify value=":email"
            ArrayList<String> fields = new ArrayList<>();
            fields.add("card");
            //fields.add("cards"); // all cards including active=f
            fields.add("settings");
            Object resp = Utils.doRequest(conn(), Api.ACTOR,
                    Api.USER_FLESHED_RETRIEVE, new Object[]{
                            authToken, userID, fields});
            if (resp != null) {
                OSRFObject usr = (OSRFObject) resp;
                OSRFObject card = (OSRFObject) usr.get("card");
                barcode = card.getString("barcode");
//                List<OSRFObject> settings = (List<OSRFObject>) usr.get("settings");
//                for (OSRFObject setting : settings) {
//                    Log.d(TAG, "setting="+ setting);
//                }
            }
        } catch (Exception e) {
            Log.d(TAG, "caught", e);
        }

        // Things that didn't work:
        // * open-ils.pcrud open-ils.pcrud.search.ac auth_token, {id: cardId}
        // * open-ils.pcrud open-ils.pcrud.search.ac auth_token, {usr: userId}
        // * open-ils.pcrud open-ils.pcrud.retrieve.ac auth_token, cardId
        //   (patrons don't have permission to see their own records)
    }

    public boolean reauthenticate(Activity activity) throws SessionNotFoundException {
        return reauthenticate(activity, userName);
    }

    /** invalidate current auth token and get a new one
     *
     * @param activity
     * @return true if auth successful
     */
    public boolean reauthenticate(Activity activity, String user_name) throws SessionNotFoundException {
        Log.d(Const.AUTH_TAG, "reauthenticate " + user_name);
        AccountUtils.invalidateAuthToken(activity, authToken);
        clearSession();

        try {
            String auth_token = AccountUtils.getAuthTokenForAccount(activity, user_name);
            if (TextUtils.isEmpty(auth_token))
                return false;
            return retrieveSession(auth_token);
        } catch (Exception e) {
            Log.d(Const.AUTH_TAG, "reauth exception", e);
            return false;
        }
    }

    // ------------------------Checked Out Items Section
    // -------------------------//

    /**
     * Gets the items checked out.
     *
     * @return the items checked out
     * @throws SessionNotFoundException the session not found exception
     */
    public ArrayList<CircRecord> getItemsCheckedOut()
            throws SessionNotFoundException {

        ArrayList<CircRecord> circRecords = new ArrayList<CircRecord>();

        Object resp = Utils.doRequest(conn(), Api.ACTOR,
                Api.CHECKED_OUT, authToken, new Object[] {
                        authToken, userID });
        if (resp == null)
            return circRecords;
        Map<String, ?> resp_map = ((Map<String, ?>) resp);

        if (resp_map.get("out") != null) {
            List<String> id = (List<String>) resp_map.get("out");
            for (int i = 0; i < id.size(); i++) {
                OSRFObject circ = retrieveCircRecord(id.get(i));
                CircRecord circRecord = new CircRecord(circ, CircRecord.CircType.OUT,
                        Integer.parseInt(id.get(i)));
                fetchInfoForCheckedOutItem(circ.getInt("target_copy"), circRecord);
                circRecords.add(circRecord);
            }
        }

        if (resp_map.get("overdue") != null) {
            List<String> id = (List<String>) resp_map.get("overdue");
            for (int i = 0; i < id.size(); i++) {
                OSRFObject circ = retrieveCircRecord(id.get(i));
                CircRecord circRecord = new CircRecord(circ, CircRecord.CircType.OVERDUE,
                        Integer.parseInt(id.get(i)));
                fetchInfoForCheckedOutItem(circ.getInt("target_copy"), circRecord);
                circRecords.add(circRecord);
            }
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
    private OSRFObject retrieveCircRecord(String id)
            throws SessionNotFoundException {

        OSRFObject circ = (OSRFObject) Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.CIRC_RETRIEVE, authToken, new Object[] {
                        authToken, id });
        return circ;
    }

    /*
     * Fetch info for Checked Out Items It uses two methods :
     * open-ils.search.biblio.mods_from_copy or in case of pre-cataloged records
     * it uses open-ils.search.asset.copy.retriev Usefull info : title and
     * author (for acp : dummy_title, dummy_author)
     */
    /**
     * Fetch info for checked out item.
     *
     * @param target_copy the target_copy
     * @param circRecord the circ record
     * @return the oSRF object
     */
    private OSRFObject fetchInfoForCheckedOutItem(Integer target_copy, CircRecord circRecord) {

        if (target_copy == null)
            return null;

        OSRFObject result;
        Log.d(TAG, "Mods from copy");
        OSRFObject info_mvr = fetchModsFromCopy(target_copy);

        try {
            circRecord.recordInfo = new RecordInfo(info_mvr);
            circRecord.recordInfo.setSearchFormat(fetchFormat(info_mvr.getInt("doc_id")));
        } catch (Exception e) {
            Log.d(TAG, "caught", e);
        }

        // if title or author not inserted, request acp with copy_target
        result = info_mvr;
        OSRFObject info_acp = null;

        // the logic to establish mvr or acp is copied from the opac
        if (info_mvr.getString("title") == null
                || info_mvr.getString("author") == null) {
            Log.d(TAG, "Asset");
            info_acp = fetchAssetCopy(target_copy);
            result = info_acp;
            circRecord.acp = info_acp;
            circRecord.circ_info_type = CircRecord.ACP_OBJ_TYPE;
        } else {
            circRecord.mvr = info_mvr;
            circRecord.circ_info_type = CircRecord.MVR_OBJ_TYPE;
        }
        return result;
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

    public String fetchFormat(int id) {
        return fetchFormat(Integer.valueOf(id).toString());
    }

    public String fetchFormat(String id) {
        // This can happen when looking up checked out item borrowed from another system.
        if (id.equals("-1"))
            return "";

        OSRFObject resp = null;
        try {
            // todo newer EG supports use of "ANONYMOUS" as the auth_token in the PCRUD request,
            // but there are some older EG installs out there that do not.
            resp = (OSRFObject) Utils.doRequest(conn(), Api.PCRUD_SERVICE,
                    Api.RETRIEVE_MRA, authToken, new Object[] {
                            authToken, id});
        } catch (SessionNotFoundException e) {
            return "";
        }
        return getSearchFormatFromMRAResponse(resp);
    }

    public static String getSearchFormatFromMRAResponse(Object response) {
        if (response == null)
            return ""; // todo log this

        OSRFObject resp = null;
        try {
            resp = (OSRFObject) response;
        } catch (ClassCastException ex) {
            Log.d(TAG, "caught", ex);
            return ""; // todo log this
        }

        // This is not beautiful.  This MRA record comes back with an 'attrs' field that
        // appears to have been serialized by perl Data::Dumper, e.g.
        // '"biog"=>"b", "conf"=>"0", "search_format"=>"ebook"'.
        String attrs = resp.getString("attrs");
        //Log.d(TAG, "attrs="+attrs);
        String[] attr_arr = TextUtils.split(attrs, ", ");
        String icon_format = "";
        String search_format = "";
        for (int i=0; i<attr_arr.length; ++i) {
            String[] kv = TextUtils.split(attr_arr[i], "=>");
            String key = kv[0].replace("\"", "");
            if (key.equalsIgnoreCase("icon_format")) {
                icon_format = kv[1].replace("\"", "");
            } else if (key.equalsIgnoreCase("search_format")) {
                search_format = kv[1].replace("\"", "");
            }
        }
        if (!icon_format.isEmpty()) {
            return icon_format;
        } else {
            return search_format;
        }
    }

    // experiment to handle parsing batch/atomic methods
    public static String getSearchFormatFromMRAList(Object response) {
        if (response == null)
            return ""; // todo log this

        OSRFObject resp = null;
        try {
            ArrayList<OSRFObject> resp_list = (ArrayList<OSRFObject>)response;
            resp = resp_list.get(0);
        } catch (ClassCastException ex) {
            Log.d(TAG, "caught", ex);
        }
        if (resp == null)
            return ""; // todo log this

        // This is not beautiful.  An MRA record comes back with an 'attrs' field that
        // appears to have been serialized by perl Data::Dumper, e.g.
        //     "biog"=>"b", "conf"=>"0", "search_format"=>"ebook"
        String attrs = resp.getString("attrs");
        //Log.d(TAG, "attrs="+attrs);
        String[] attr_arr = TextUtils.split(attrs, ", ");
        String icon_format = "";
        String search_format = "";
        for (int i=0; i<attr_arr.length; ++i) {
            String[] kv = TextUtils.split(attr_arr[i], "=>");
            String key = kv[0].replace("\"", "");
            if (key.equalsIgnoreCase("icon_format")) {
                icon_format = kv[1].replace("\"", "");
            } else if (key.equalsIgnoreCase("search_format")) {
                search_format = kv[1].replace("\"", "");
            }
        }
        if (!icon_format.isEmpty()) {
            return icon_format;
        } else {
            return search_format;
        }
    }

    // experiment to handle parsing batch/atomic methods
    public static String getSearchFormatFromMRAFList(Object response) {
        if (response == null)
            return ""; // todo log this

        ArrayList<OSRFObject> resp_list = null;
        try {
            resp_list = (ArrayList<OSRFObject>)response;
        } catch (ClassCastException ex) {
            Log.d(TAG, "caught", ex);
            return "";
        }
        if (resp_list == null)
            return ""; // todo log this

        // This is not beautiful.  An MRA record comes back with an 'attrs' field that
        // appears to have been serialized by perl Data::Dumper, e.g.
        //     "biog"=>"b", "conf"=>"0", "search_format"=>"ebook"
        OSRFObject resp = null; //bomb, this method was not fixed
        String attrs = resp.getString("attrs");
        //Log.d(TAG, "attrs="+attrs);
        String[] attr_arr = TextUtils.split(attrs, ", ");
        String icon_format = "";
        String search_format = "";
        for (int i=0; i<attr_arr.length; ++i) {
            String[] kv = TextUtils.split(attr_arr[i], "=>");
            String key = kv[0].replace("\"", "");
            if (key.equalsIgnoreCase("icon_format")) {
                icon_format = kv[1].replace("\"", "");
            } else if (key.equalsIgnoreCase("search_format")) {
                search_format = kv[1].replace("\"", "");
            }
        }
        if (!icon_format.isEmpty()) {
            return icon_format;
        } else {
            return search_format;
        }
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

    /*
     * Method used to renew a circulation record based on target_copy_id Returns
     * many objects, don't think they are needed
     */
    /**
     * Renew circ.
     *
     * @param target_copy the target_copy
     * @throws MaxRenewalsException the max renewals exception
     * @throws ServerErrorMessage the server error message
     * @throws SessionNotFoundException the session not found exception
     */
    public void renewCirc(Integer target_copy) throws MaxRenewalsException,
            ServerErrorMessage, SessionNotFoundException {

        HashMap<String, Integer> complexParam = new HashMap<>();
        complexParam.put("patron", this.userID);
        complexParam.put("copyid", target_copy);
        complexParam.put("opac_renewal", 1);

        Object a_lot = (Object) Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.CIRC_RENEW, authToken, new Object[] {
                        authToken, complexParam });

        Map<String, String> resp = (Map<String, String>) a_lot;

        if (resp.get("textcode") != null && !resp.get("textcode").equals("SUCCESS")) {
            if (resp.get("textcode").equals("MAX_RENEWALS_REACHED"))
                throw new MaxRenewalsException();
            throw new ServerErrorMessage(resp.get("desc").toString());
        }

    }

    // ------------------------orgs Section
    // --------------------------------------//

    // todo: call service=open-ils.actor&method=open-ils.actor.org_types.retrieve

    public OSRFObject fetchOrgTree() {
        Object response = Utils.doRequest(conn(), Api.ACTOR,
                Api.ORG_TREE_RETRIEVE, new Object[]{});
        return (OSRFObject) response;
    }

    /**
     * Fetch org settings.
     *
     * @param org_id the org_id
     * @param setting the setting
     * @return the object
     * @throws SessionNotFoundException the session not found exception
     */
//    public OSRFObject fetchOrgSettings(Integer org_id, String setting)
//            throws SessionNotFoundException {
//
//        OSRFObject response = (OSRFObject) Utils.doRequest(conn(), Api.ACTOR,
//                Api.ORG_SETTING_ANCESTOR, new Object[]{
//                        org_id, setting});
//        return response;
//    }

//    public void getOrgHiddentDepth() {
//
//        // logic can be found in the opac_utils.js file in web/opac/common/js
//
//        for (int i = 0; i < organizations.size(); i++) {
//            AccountAccess ac = AccountAccess.getInstance();
//            try {
//                Object obj = ac.fetchOrgSettings(organizations.get(i).id,
//                        "opac.org_unit_hiding.depth");
//            } catch (SessionNotFoundException e) {
//            }
//
//        }
//
//    }

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

        // fields of interest : expire_time
        List<OSRFObject> listHoldsAhr = null;

        Object resp = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.HOLDS_RETRIEVE, authToken, new Object[] {
                        authToken, userID });
        if (resp == null) {
            Log.d(TAG, "Result: null");
            return holds;
        }

        listHoldsAhr = (List<OSRFObject>) resp;

        for (int i = 0; i < listHoldsAhr.size(); i++) {
            HoldRecord hold = new HoldRecord(listHoldsAhr.get(i));
            fetchHoldTitleInfo(listHoldsAhr.get(i), hold);
            fetchHoldStatus(listHoldsAhr.get(i), hold);
            if (hold.recordInfo != null)
                hold.recordInfo.setSearchFormat(fetchFormat(hold.target));
            holds.add(hold);
        }
        return holds;
    }

    // hold_type    - T, C (or R or F), I, V or M for Title, Copy, Issuance, Volume or Meta-record  (default "T")

    /**
     * Fetch hold title info.
     *
     * @param holdArhObject the hold arh object
     * @param hold the hold
     * @return the object
     */
    private Object fetchHoldTitleInfo(OSRFObject holdArhObject, HoldRecord hold) {

        String holdType = (String) holdArhObject.get("hold_type");
        Integer target = holdArhObject.getInt("target");
        String method = null;

        OSRFObject holdInfo = null;
        if (holdType.equals("T") || holdType.equals("M")) {
            if (holdType.equals("M"))
                method = Api.METARECORD_MODS_SLIM_RETRIEVE;
            else //(holdType.equals("T"))
                method = Api.RECORD_MODS_SLIM_RETRIEVE;
            holdInfo = (OSRFObject) Utils.doRequest(conn(), Api.SEARCH,
                    method, new Object[] {
                            target });

            // Log.d(TAG, "Hold here " + holdInfo);
            hold.title = holdInfo.getString("title");
            hold.author = holdInfo.getString("author");
            hold.recordInfo = new RecordInfo(holdInfo);
        } else {
            // multiple objects per hold ????
            holdInfo = holdFetchObjects(holdArhObject, hold);
        }
        return holdInfo;
    }

    /**
     * Hold fetch objects.
     *
     * @param hold the hold
     * @param holdObj the hold obj
     * @return the oSRF object
     */
    private OSRFObject holdFetchObjects(OSRFObject hold, HoldRecord holdObj) {

        String type = (String) hold.get("hold_type");

        Log.d(TAG, "Hold Type " + type);
        if (type.equals("C")) {

            /*
             * steps asset.copy'->'asset.call_number'->'biblio.record_entry' or,
             * in IDL ids, acp->acn->bre
             */

            // fetch_copy
            OSRFObject copyObject = fetchAssetCopy(hold.getInt("target"));
            // fetch_volume from copyObject.call_number field
            Integer call_number = copyObject.getInt("call_number");

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
                        Api.SEARCH, Api.RECORD_MODS_SLIM_RETRIEVE,
                        new Object[] { record });

                holdObj.title = holdInfo.getString("title");
                holdObj.author = holdInfo.getString("author");
                holdObj.recordInfo = new RecordInfo(holdInfo);
            }

            return copyObject;
        } else if (type.equals("V")) {
            // must test

            // fetch_volume
            OSRFObject volume = (OSRFObject) Utils.doRequest(conn(),
                    Api.SEARCH, Api.ASSET_CALL_NUMBER_RETRIEVE,
                    new Object[] { hold.getInt("target") });
            // in volume object : record

            // in volume object : record
            Integer record = volume.getInt("record");

            // part label
            holdObj.part_label = volume.getString("label");

            Log.d(TAG, "Record " + record);
            OSRFObject holdInfo = (OSRFObject) Utils.doRequest(conn(),
                    Api.SEARCH, Api.RECORD_MODS_SLIM_RETRIEVE,
                    new Object[] { record });

            holdObj.title = holdInfo.getString("title");
            holdObj.author = holdInfo.getString("author");
            holdObj.recordInfo = new RecordInfo(holdInfo);
        } else if (type.equals("I")) {
            OSRFObject issuance = (OSRFObject) Utils.doRequest(conn(),
                    Api.SERVICE_SERIAL, Api.METHOD_FETCH_ISSUANCE,
                    new Object[] { hold.getInt("target") });
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
            queryParam.put("id", hold.getInt("target"));
            param.put("query", queryParam);

            // returns [{record:id, label=part label}]

            List<Object> part = (List<Object>) Utils.doRequest(conn(),
                    Api.FIELDER, Api.FIELDER_BMP_ATOMIC,
                    new Object[] { param });

            Map<String, ?> partObj = (Map<String, ?>) part.get(0);

            Integer recordID = (Integer) partObj.get("record");
            String part_label = (String) partObj.get("label");

            OSRFObject holdInfo = (OSRFObject) Utils.doRequest(conn(),
                    Api.SEARCH, Api.RECORD_MODS_SLIM_RETRIEVE,
                    new Object[] { recordID });

            holdObj.part_label = part_label;
            holdObj.title = holdInfo.getString("title");
            holdObj.author = holdInfo.getString("author");
            holdObj.recordInfo = new RecordInfo(holdInfo);
        }

        return null;
    }

    /**
     * Fetch hold status.
     *
     * @param hold the hold
     * @param holdObj the hold obj
     * @throws SessionNotFoundException the session not found exception
     */
    public void fetchHoldStatus(OSRFObject hold, HoldRecord holdObj)
            throws SessionNotFoundException {

        Integer hold_id = hold.getInt("id");
        Object resp = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.HOLD_QUEUE_STATS, authToken, new Object[] {
                        authToken, hold_id });

        Map<String, Integer> map = (Map<String, Integer>)resp;
        holdObj.status = map.get("status");
        holdObj.potentialCopies = map.get("potential_copies");
        holdObj.estimatedWaitInSeconds = map.get("estimated_wait");
        holdObj.queuePosition = map.get("queue_position");
        holdObj.totalHolds = map.get("total_holds");
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

        Object response = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.HOLD_CANCEL, authToken, new Object[] {
                        authToken, hold_id });

        // delete successful
        if (response.toString().equals("1"))
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

        Object response = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.HOLD_UPDATE, authToken, new Object[] {
                        authToken, ahr });

        return response;
    }

    /**
     * Creates the hold.
     *
     * @param recordID the record id
     * @param pickup_lib the pickup_lib
     * @param email_notify the email_notify
     * @param phone_notify the phone_notify
     * @param phone the phone
     * @param suspendHold the suspend hold
     * @param expire_time the expire_time
     * @param thaw_date the thaw_date
     * @return the string[]
     * @throws SessionNotFoundException the session not found exception
     */
    public String[] createHold(Integer recordID, Integer pickup_lib,
            boolean email_notify, boolean phone_notify, String phone,
            boolean suspendHold, String expire_time, String thaw_date)
            throws SessionNotFoundException {

        OSRFObject ahr = new OSRFObject("ahr");
        ahr.put("target", recordID);
        ahr.put("usr", userID);
        ahr.put("requestor", userID);
        ahr.put("hold_type", "T");
        ahr.put("pickup_lib", pickup_lib);
        ahr.put("phone_notify", phone);
        ahr.put("email_notify", email_notify);
        ahr.put("expire_time", expire_time);
        ahr.put("frozen", suspendHold);
        ahr.put("thaw_date", thaw_date);

        Object response = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.HOLD_CREATE, authToken, new Object[] {
                        authToken, ahr });

        String[] resp = new String[] {"false",null,null};
        // if we can get hold ID then we return true
        try {

            Integer id = Integer.parseInt(response.toString());
            if (id > -1)
                resp[0] = "true";

        } catch (Exception e) {

            List<?> respErrorMessage = (List<?>) response;

            Object map = respErrorMessage.get(0);
            resp[0] = "false";
            resp[1] = ((Map<String, String>) map).get("textcode");
            resp[2] = ((Map<String, String>) map).get("desc");
        }

        Log.d(TAG, "Result " + resp[1] + " " + resp[2]);
        return resp;
    }

    public String[] testAndCreateHold(Integer recordID, Integer pickup_lib,
                                      boolean email_notify, boolean phone_notify, String phone,
                                      boolean suspendHold, String expire_time, String thaw_date)
            throws SessionNotFoundException {
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
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("patronid", userID);
        map.put("pickup_lib", pickup_lib);
        map.put("titleid", recordID);
        map.put("hold_type", "T");
        map.put("email_notify", email_notify);
        map.put("expire_time", expire_time);
        if (suspendHold && thaw_date != null) {
            map.put("frozen", suspendHold);
            map.put("thaw_date", thaw_date);
        }
        ArrayList<Integer> ids = new ArrayList<Integer>(1);
        ids.add(recordID);
        Object response = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.HOLD_TEST_AND_CREATE, authToken, new Object[] {
                        authToken, map, ids });

        String[] resp = new String[] {"false",null,null};
        Map<String, ?> resp_map = ((Map<String, ?>) response);
        try {
            Object result = resp_map.get("result");
            if (result instanceof List) {
                // List of error events
                List<?> l = (List<?>) result;
                Map<String, ?> event0 = (Map<String, ?>) l.get(0);
                resp[0] = "false";
                resp[1] = (String) event0.get("textcode");
                resp[2] = (String) event0.get("desc");
            } else if (result instanceof Integer) {
                Integer hold_id = (Integer) result;
                if (hold_id > -1) {
                    resp[0] = "true";
                }
            } else {
                Log.d(TAG, "unknown response from test_and_create: "+result);
            }

        } catch (Exception e) {
            resp[0] = "false";
            resp[1] = "";
            resp[2] = "Unknown error";
        }

        Log.d(TAG, "Result " + resp[1] + " " + resp[2]);
        return resp;
    }

    /**
     * Checks if is hold possible.
     *
     * @param pickup_lib the pickup_lib
     * @param recordID the record id
     * @return the object
     * @throws SessionNotFoundException the session not found exception
     */
    public Object isHoldPossible(Integer pickup_lib, Integer recordID)
            throws SessionNotFoundException {

        HashMap<String, Integer> map = getHoldPreCreateInfo(recordID, pickup_lib);
        map.put("pickup_lib", pickup_lib);
        map.put("hold_type", null);
        map.put("patronid", userID);
        map.put("volume_id", null);
        map.put("issuanceid", null);
        map.put("copy_id", null);
        map.put("depth", 0);
        map.put("part_id", null);
        map.put("holdable_formats", null);
        // {"titleid":63,"mrid":60,"volume_id":null,"issuanceid":null,"copy_id":null,"hold_type":"T","holdable_formats":null,
        // "patronid":2,"depth":0,"pickup_lib":"8","partid":null}

        Object response = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.HOLD_IS_POSSIBLE, authToken, new Object[] {
                        authToken, map });

        return response;
    }

    // return
    /**
     * Gets the hold pre create info.
     *
     * @param recordID the record id
     * @param pickup_lib the pickup_lib
     * @return the hold pre create info
     */
    public HashMap<String, Integer> getHoldPreCreateInfo(Integer recordID, Integer pickup_lib) {

        HashMap<String, Integer> param = new HashMap<String, Integer>();

        param.put("pickup_lib", pickup_lib);
        param.put("record", recordID);

        Map<String, ?> response = (Map<String, ?>) Utils.doRequest(conn(),
                Api.SEARCH,
                Api.METABIB_RECORD_TO_DESCRIPTORS,
                new Object[] { param });

        Object obj = response.get("metarecord");
        Log.d(TAG, "metarecord="+obj);
        Integer metarecordID = Integer.parseInt(obj.toString());

        HashMap<String, Integer> map = new HashMap<String, Integer>();
        map.put("titleid", recordID);
        map.put("mrid", metarecordID);

        return map;
        /*
         * Methods to get necessary info on hold
         * open-ils.search.metabib.record_to_descriptors
         * 
         * open-ils.search.biblio.record_hold_parts
         */
    }

    // ----------------------------Fines
    // Summary------------------------------------//

    /**
     * Gets the fines summary.
     *
     * @return the fines summary
     * @throws SessionNotFoundException the session not found exception
     */
    public float[] getFinesSummary() throws SessionNotFoundException {

        // mous object
        OSRFObject finesSummary = (OSRFObject) Utils.doRequest(conn(), Api.ACTOR,
                Api.FINES_SUMMARY, authToken, new Object[] {
                        authToken, userID });

        float fines[] = new float[3];
        try {
            fines[0] = Float.parseFloat(finesSummary.getString("total_owed"));
            fines[1] = Float.parseFloat(finesSummary.getString("total_paid"));
            fines[2] = Float.parseFloat(finesSummary.getString("balance_owed"));
        } catch (Exception e) {
            Log.d(TAG, "Error parsing fines", e);
        }

        return fines;
    }

    /**
     * Gets the transactions.
     *
     * @return the transactions
     * @throws SessionNotFoundException the session not found exception
     */
    public ArrayList<FinesRecord> getTransactions()
            throws SessionNotFoundException {

        ArrayList<FinesRecord> finesRecords = new ArrayList<FinesRecord>();

        Object transactions = Utils.doRequest(conn(), Api.ACTOR,
                Api.TRANSACTIONS_WITH_CHARGES, authToken, new Object[] {
                        authToken, userID });

        // get Array

        List<Map<String, OSRFObject>> list = (List<Map<String, OSRFObject>>) transactions;

        for (int i = 0; i < list.size(); i++) {

            Map<String, OSRFObject> item = list.get(i);
            FinesRecord record = new FinesRecord(item.get("circ"), item.get("record"), item.get("transaction"));
            finesRecords.add(record);
        }

        return finesRecords;
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

        Object response = Utils.doRequest(conn(), Api.ACTOR,
                Api.CONTAINERS_BY_CLASS, authToken, new Object[] {
                        authToken, userID, CONTAINER_CLASS_BIBLIO, CONTAINER_BUCKET_TYPE_BOOKBAG });

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

        Map<String, ?> map = (Map<String, ?>) Utils.doRequest(conn(), Api.ACTOR,
                Api.CONTAINER_FLESH, authToken, new Object[] {
                        authToken, CONTAINER_CLASS_BIBLIO, bookbagID });
        
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

        OSRFObject cbreb = new OSRFObject("cbreb");
        cbreb.put("btype", CONTAINER_BUCKET_TYPE_BOOKBAG);
        cbreb.put("name", name);
        cbreb.put("pub", false);
        cbreb.put("owner", userID);

        createContainer(CONTAINER_CLASS_BIBLIO, cbreb);
    }

    /**
     * Delete book bag.
     *
     * @param id the id
     * @throws SessionNotFoundException the session not found exception
     */
    public void deleteBookBag(Integer id) throws SessionNotFoundException {

        Object response = Utils.doRequest(conn(), Api.ACTOR,
                Api.CONTAINER_FULL_DELETE, authToken, new Object[] {
                        authToken, CONTAINER_CLASS_BIBLIO, id });
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

        OSRFObject cbrebi = new OSRFObject("cbrebi");
        cbrebi.put("bucket", bookbag_id);
        cbrebi.put("target_biblio_record_entry", record_id);
        cbrebi.put("id", null);

        Object response = Utils.doRequest(conn(), Api.ACTOR,
                Api.CONTAINER_ITEM_CREATE, authToken, new Object[] {
                        authToken, CONTAINER_CLASS_BIBLIO, cbrebi });
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

        Object response = Utils.doRequest(conn(), Api.ACTOR,
                Api.CONTAINER_ITEM_DELETE, authToken, new Object[] {
                        authToken, container, id });
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

        Object response = Utils.doRequest(conn(), Api.ACTOR,
                Api.CONTAINER_CREATE, authToken, new Object[] {
                        authToken, container, parameter });
    }

    public String getAuthToken() {
        return authToken;
    }
}
