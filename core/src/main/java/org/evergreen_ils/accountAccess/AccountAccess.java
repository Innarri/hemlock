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
import org.evergreen_ils.accountAccess.fines.FinesRecord;
import org.evergreen_ils.accountAccess.holds.HoldRecord;
import org.evergreen_ils.auth.Const;
import org.evergreen_ils.system.EvergreenServer;
import org.evergreen_ils.android.Log;
import org.evergreen_ils.system.Organization;
import org.evergreen_ils.system.Utils;
import org.evergreen_ils.searchCatalog.RecordInfo;
import org.evergreen_ils.android.Analytics;
import org.opensrf.ShouldNotHappenException;
import org.opensrf.net.http.HttpConnection;
import org.opensrf.util.GatewayResponse;
import org.opensrf.util.OSRFObject;

import java.util.*;

import androidx.annotation.NonNull;

import static org.evergreen_ils.utils.StringUtils.safeString;

public class AccountAccess {

    private final static String TAG = AccountAccess.class.getSimpleName();

    // Used for book bags
    public static String CONTAINER_CLASS_BIBLIO = "biblio";
    public static String CONTAINER_BUCKET_TYPE_BOOKBAG = "bookbag";

    private static AccountAccess mInstance = null;

    private String userName = null;
    private String authToken = null;
    private Integer userID = null;
    private String dayPhone = null;
    private String barcode = null;
    private Integer homeOrgID = null;
    private Integer pickupOrgID = null;
    private Integer searchOrgID = null;
    private Integer defaultSMSCarrierID = null;
    private String defaultSMSNumber = null;
    private String notifyPhoneNumber = null;
    private Boolean notifyByEmail = null;
    private Boolean notifyByPhone = null;
    private Boolean notifyBySMS = null;
    private ArrayList<BookBag> bookBags = new ArrayList<>();

    private void clearSession() {
        userName = null;
        authToken = null;
        userID = null;
        dayPhone = null;
        barcode = null;
        homeOrgID = null;
        pickupOrgID = null;
        searchOrgID = null;
        defaultSMSCarrierID = null;
        defaultSMSNumber = null;
        notifyPhoneNumber = null;
        notifyByEmail = null;
        notifyByPhone = null;
        notifyBySMS = null;
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

    public String getBarcode() { return barcode; }

    public Integer getHomeOrgID() {
        return homeOrgID;
    }

    public Integer getPickupOrgID() {
        if (pickupOrgID != null)
            return pickupOrgID;
        return homeOrgID;
    }

    public Integer getSearchOrgID() {
        if (searchOrgID != null)
            return searchOrgID;
        return homeOrgID;
    }

    public static String safeGetOrganizationShortName(Integer orgID) {
        if (orgID == null) return "";
        Organization org = EvergreenServer.getInstance().getOrganization(orgID);
        if (org == null) return "!";
        return org.shortname;
    }

    public boolean getNotifyByEmail() {
        return Utils.safeBool(notifyByEmail);
    }

    public boolean getNotifyByPhone() {
        return Utils.safeBool(notifyByPhone);
    }

    public String getNotifyPhoneNumber() {
        if (!TextUtils.isEmpty(notifyPhoneNumber))
            return notifyPhoneNumber;
        return dayPhone;
    }

    public boolean getNotifyBySMS() {
        return Utils.safeBool(notifyBySMS);
    }

    public Integer getNotifySMSCarrierID() {
        return defaultSMSCarrierID;
    }

    public String getNotifySMSNumber() {
        return defaultSMSNumber;
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
                Api.AUTH_SESSION_RETRIEVE, authToken, new Object[]{
                        authToken});
        if (resp != null) {
            OSRFObject au = (OSRFObject) resp;
            userID = au.getInt("id");
            homeOrgID = au.getInt("home_ou");
            userName = au.getString("usrname");
            dayPhone = au.getString("day_phone");
            //email = au.getString("email");
            // todo: warn when account is nearing expiration
            //expireDate = Api.parseDate(au.getString("expire_date"));

            fleshUserSettings();
            return true;
        }
        throw new SessionNotFoundException();
    }

    // Fix stupid setting that is returned with extra quotes
    private String removeStupidExtraQuotes(String s) {
        if (s.startsWith("\"")) s = s.replace("\"", ""); // setting has extra quotes
        return s;
    }

    // This could be done on demand, but coming in at ~75ms it is not worth it
    public void fleshUserSettings() {

        String holdNotifySetting = "email:phone"; // OPAC default
        try {
            // Array of fields is optional; the default does not include settings
            ArrayList<String> fields = new ArrayList<>();
            fields.add("card");    // active library card
            fields.add("settings");// array of settings objects, e.g. name=opac.hold_notify value=":email"
            Object resp = Utils.doRequest(conn(), Api.ACTOR,
                    Api.USER_FLESHED_RETRIEVE, new Object[]{
                            authToken, userID, fields});
            if (resp != null) {
                OSRFObject usr = (OSRFObject) resp;
                OSRFObject card = (OSRFObject) usr.get("card");
                barcode = card.getString("barcode");
                List<OSRFObject> settings = (List<OSRFObject>) usr.get("settings");
                for (OSRFObject setting : settings) {
                    String name = setting.getString("name");
                    String value = removeStupidExtraQuotes(setting.getString(Api.VALUE));
                    if (name.equals(Api.USER_SETTING_DEFAULT_PICKUP_LOCATION)) {
                        pickupOrgID = Api.parseInteger(value);
                    } else if (name.equals(Api.USER_SETTING_DEFAULT_PHONE)) {
                        notifyPhoneNumber = value;
                    } else if (name.equals(Api.USER_SETTING_DEFAULT_SEARCH_LOCATION)) {
                        searchOrgID = Api.parseInteger(value);
                    } else if (name.equals(Api.USER_SETTING_DEFAULT_SMS_CARRIER)) {
                        defaultSMSCarrierID = Api.parseInteger(value);
                    } else if (name.equals(Api.USER_SETTING_DEFAULT_SMS_NOTIFY)) {
                        defaultSMSNumber = value;
                    } else if (name.equals(Api.USER_SETTING_HOLD_NOTIFY)) {
                        holdNotifySetting = value;
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "caught", e);
        }
        parseHoldNotifyValue(holdNotifySetting);
        Analytics.logEvent("Account: Retrieve Session",
                "home_org", safeGetOrganizationShortName(homeOrgID),
                "pickup_org", safeGetOrganizationShortName(pickupOrgID),
                "search_org", safeGetOrganizationShortName(searchOrgID),
                "hold_notify", safeString(holdNotifySetting));
        Log.d(TAG, "done fleshing user settings");
    }

    private void parseHoldNotifyValue(String value) {
        // NB: value may be either ':' separated or '|' separated, e.g. "phone:email" or "email|sms"
        notifyByEmail = value.contains("email");
        notifyByPhone = value.contains("phone");
        notifyBySMS = value.contains("sms");
    }

    public boolean reauthenticate(Activity activity) {
        return reauthenticate(activity, userName);
    }

    /** invalidate current auth token and get a new one
     *
     * @param activity
     * @return true if auth successful
     */
    public boolean reauthenticate(Activity activity, String user_name) {
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

    public void logout(Activity activity) {
        Log.d(Const.AUTH_TAG, "logout, userName=" + userName + ", authToken=" + authToken);
        AccountUtils.invalidateAuthToken(activity, authToken);
        AccountUtils.clearPassword(activity, userName);
        clearSession();
    }

    // ------------------------Checked Out Items Section
    // -------------------------//

    private CircRecord fleshCircRecord(String id, CircRecord.CircType circType) throws SessionNotFoundException {
        GatewayResponse response = retrieveCircRecord(id);
        if (response.failed) {
            // PINES Crash #23
            Analytics.logException(new ShouldNotHappenException("failed circ retrieve, type:" + circType + " desc:" + response.description));
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

        Object resp = Utils.doRequest(conn(), Api.ACTOR,
                Api.CHECKED_OUT, authToken, new Object[] {
                        authToken, userID });
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
    private GatewayResponse retrieveCircRecord(String id)
            throws SessionNotFoundException {

        Object resp = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.CIRC_RETRIEVE, authToken, new Object[] {
                        authToken, id });
        return GatewayResponse.createFromObject(resp);
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

        OSRFObject resp = null;
        try {
            resp = (OSRFObject) Utils.doRequest(conn(), Api.PCRUD,
                    Api.RETRIEVE_MRA, Api.ANONYMOUS, new Object[] {
                            authToken, id});
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

    /*
     * Method used to renew a circulation record based on target_copy_id Returns
     * many objects, don't think they are needed
     */
    /**
     * Renew circ.
     *
     * @param target_copy the target_copy
     * @throws SessionNotFoundException the session not found exception
     */
    public GatewayResponse renewCirc(Integer target_copy) throws SessionNotFoundException {

        HashMap<String, Integer> param = new HashMap<>();
        param.put("patron", this.userID);
        param.put("copyid", target_copy);
        param.put("opac_renewal", 1);

        Object resp = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.CIRC_RENEW, authToken, new Object[] {
                        authToken, param });

        return GatewayResponse.createFromObject(resp);
    }

    public List<OSRFObject> fetchCopyStatuses() {

        List<OSRFObject> ccs_list = (List<OSRFObject>) Utils.doRequest(conn(), Api.SEARCH,
                Api.COPY_STATUS_ALL, new Object[] {});
        return ccs_list;
    }

    // ------------------------orgs Section
    // --------------------------------------//

    public List<OSRFObject> fetchOrgTypes() {
        Object resp = Utils.doRequest(conn(), Api.ACTOR,
                Api.ORG_TYPES_RETRIEVE, new Object[] {});
        List<OSRFObject> l = (List<OSRFObject>) resp;
        return l;
    }

    public OSRFObject fetchOrgTree() {
        Object resp = Utils.doRequest(conn(), Api.ACTOR,
                Api.ORG_TREE_RETRIEVE, new Object[]{});
        return (OSRFObject) resp;
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

        Object resp = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.HOLDS_RETRIEVE, authToken, new Object[] {
                        authToken, userID });
        if (resp == null) {
            Log.d(TAG, "Result: null");
            return holds;
        }

        List<OSRFObject> listHoldsAhr = (List<OSRFObject>) resp;
        for (OSRFObject ahr_obj: listHoldsAhr) {
            HoldRecord hold = new HoldRecord(ahr_obj);
            fetchHoldTargetDetails(hold);
            fetchHoldQueueStats(hold);
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
            hold.recordInfo.updateFromMRAResponse(fetchRecordAttributes(hold.target));
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
            holdObj.recordInfo.updateFromMRAResponse(fetchRecordAttributes(recordID));
        }
    }

    /**
     * Fetch hold status.
     */
    public void fetchHoldQueueStats(HoldRecord hold)
            throws SessionNotFoundException {

        Integer hold_id = hold.ahr.getInt("id");
        Object resp = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.HOLD_QUEUE_STATS, authToken, new Object[] {
                        authToken, hold_id });
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
        String note = "Cancelled by mobile app";

        Object response = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.HOLD_CANCEL, authToken, new Object[] {
                        authToken, hold_id, null, note });
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

        Object response = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.HOLD_UPDATE, authToken, new Object[] {
                        authToken, ahr });

        return response;
    }

    public Result testAndCreateHold(Integer recordID, Integer pickup_lib,
                                    boolean email_notify, String phone_notify,
                                    String sms_notify, Integer sms_carrier_id,
                                    String expire_time, boolean suspendHold, String thaw_date)
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
        HashMap<String, Object> args = new HashMap<>();
        args.put("patronid", userID);
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

        Object resp = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.HOLD_TEST_AND_CREATE, authToken, new Object[] {
                        authToken, args, ids });

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

    /**
     * Checks if is hold possible.
     *
     * @param pickup_lib the pickup_lib
     * @param recordID the record id
     * @return the object
     * @throws SessionNotFoundException the session not found exception
     */
    public Object isHoldPossible(Integer recordID, Integer pickup_lib)
            throws SessionNotFoundException {

        HashMap<String, Object> args = new HashMap<>();
        args.put("patronid", userID);
        args.put("pickup_lib", pickup_lib);
        args.put("titleid", recordID);

        ArrayList<Integer> ids = new ArrayList<>(1);
        ids.add(recordID);

        Object response = Utils.doRequest(conn(), Api.SERVICE_CIRC,
                Api.HOLD_IS_POSSIBLE, authToken, new Object[] {
                        authToken, args, ids });

        // successs looks like  {local_avail:'',depth:null,success:1}
        // failure looks like {place_unfillable:1,age_protected_copy:null,success:0,last_event:{...}

        return response;
    }

    // ----------------------------Fines
    // Summary------------------------------------//

    /**
     * Gets the fines summary.
     *
     * @return the fines summary
     * @throws SessionNotFoundException the session not found exception
     */
    public OSRFObject getFinesSummary() throws SessionNotFoundException {

        // mous object
        OSRFObject finesSummary = (OSRFObject) Utils.doRequest(conn(), Api.ACTOR,
                Api.FINES_SUMMARY, authToken, new Object[] {
                        authToken, userID });

        return finesSummary;
    }

    /**
     * Gets the transactions.
     *
     * @return the transactions
     * @throws SessionNotFoundException the session not found exception
     */
    public ArrayList<FinesRecord> getTransactions()
            throws SessionNotFoundException {

        Object transactions = Utils.doRequest(conn(), Api.ACTOR,
                Api.TRANSACTIONS_WITH_CHARGES, authToken, new Object[] {
                        authToken, userID });

        ArrayList<FinesRecord> finesRecords = new ArrayList<>();
        List<Map<String, OSRFObject>> list = (List<Map<String, OSRFObject>>) transactions;
        if (list == null)
            return finesRecords;

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

    //------------------------------------------------------

    public String getAuthToken() {
        return authToken;
    }

    public Integer getUserID() {
        return userID;
    }

    /** return number of unread messages in patron message center
     *
     * We don't care about the messages themselves here, because I don't see a way to modify
     * the messages via OSRF, and it's easier to redirect to that section of the OPAC.
     */
    public Integer getUnreadMessageCount() {
        Object resp = Utils.doRequest(conn(), Api.ACTOR,
                Api.MESSAGES_RETRIEVE, new Object[]{
                        authToken, getUserID(), null});
        Integer unread_count = 0;
        if (resp != null) {
            List<OSRFObject> list = (List<OSRFObject>) resp;
            for (OSRFObject obj : list) {
                String read_date = obj.getString("read_date");
                Boolean deleted = Api.parseBoolean(obj.get("deleted"));
                if (read_date == null && !deleted) {
                    ++unread_count;
                }
            }
        }
        return unread_count;
    }

    public OSRFObject getHoursOfOperation(Integer orgID) {
        Object resp = Utils.doRequest(conn(), Api.ACTOR,
                Api.HOURS_OF_OPERATION_RETRIEVE, new Object[] {
                        authToken, orgID});
        return (OSRFObject)resp;
    }
}
