/*
 * Copyright (C) 2016 Kenneth H. Cox
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package org.evergreen_ils;

import android.text.TextUtils;

import org.evergreen_ils.system.Analytics;
import org.opensrf.ShouldNotHappenException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

/** OSRF API constants
 *
 * See also https://webby.evergreencatalog.com/opac/extras/docgen.xsl
 * for online service/method documentation.
 *
 * Created by kenstir on 9/26/2016.
 */
public class Api {

    public static final String ANONYMOUS = "ANONYMOUS";

    /// actor

    public static final String ACTOR = "open-ils.actor";
    public static final String CHECKED_OUT = "open-ils.actor.user.checked_out";
    public static final String USER_FLESHED_RETRIEVE = "open-ils.actor.user.fleshed.retrieve"; // au,aua,ac,auact,cuat
    public static final String ORG_TREE_RETRIEVE = "open-ils.actor.org_tree.retrieve";
    public static final String ORG_TYPES_RETRIEVE = "open-ils.actor.org_types.retrieve";
    public static final String ORG_UNIT_RETRIEVE = "open-ils.actor.org_unit.retrieve";
    public static final String ORG_UNIT_SETTING_RETRIEVE = "open-ils.actor.org_unit_setting.values.ranged.retrieve";
    public static final String ORG_UNIT_SETTING_BATCH = "open-ils.actor.ou_setting.ancestor_default.batch";
    public static final String ORG_UNIT_SETTING = "open-ils.actor.ou_setting.ancestor_default";
    public static final String FINES_SUMMARY = "open-ils.actor.user.fines.summary";
    public static final String TRANSACTIONS_WITH_CHARGES = "open-ils.actor.user.transactions.have_charge.fleshed";
    public static final String CONTAINERS_BY_CLASS = "open-ils.actor.container.retrieve_by_class.authoritative"; // [cbreb]
    public static final String CONTAINER_FLESH = "open-ils.actor.container.flesh"; // [cbrebi]
    public static final String CONTAINER_ITEM_DELETE = "open-ils.actor.container.item.delete";
    public static final String CONTAINER_CREATE = "open-ils.actor.container.create";
    public static final String CONTAINER_ITEM_CREATE = "open-ils.actor.container.item.create";
    public static final String CONTAINER_FULL_DELETE = "open-ils.actor.container.full_delete";
    public static final String MESSAGES_RETRIEVE = "open-ils.actor.message.retrieve"; // [aum]

    public static final String SETTING_CREDIT_PAYMENTS_ALLOW = "credit.payments.allow";
    public static final String SETTING_ORG_UNIT_NOT_PICKUP_LIB = "opac.holds.org_unit_not_pickup_lib";
    public static final String SETTING_SMS_ENABLE = "sms.enable";
    public static final String USER_SETTING_HOLD_NOTIFY = "opac.hold_notify"; // e.g. "email|sms" or "phone:email"
    public static final String USER_SETTING_DEFAULT_PHONE = "opac.default_phone";
    public static final String USER_SETTING_DEFAULT_PICKUP_LOCATION = "opac.default_pickup_location";
    public static final String USER_SETTING_DEFAULT_SEARCH_LOCATION = "opac.default_search_location";
    public static final String USER_SETTING_DEFAULT_SMS_CARRIER = "opac.default_sms_carrier";
    public static final String USER_SETTING_DEFAULT_SMS_NOTIFY = "opac.default_sms_notify";
    public static final String VALUE = "value";

    /// auth

    public static final String AUTH = "open-ils.auth";
    public static final String AUTH_INIT = "open-ils.auth.authenticate.init";
    public static final String AUTH_COMPLETE = "open-ils.auth.authenticate.complete";
    public static final String AUTH_SESSION_RETRIEVE = "open-ils.auth.session.retrieve"; // au

    /// circ

    public static final String SERVICE_CIRC = "open-ils.circ";
    public static final String CIRC_RETRIEVE = "open-ils.circ.retrieve"; // circ
    public static final String CIRC_RENEW = "open-ils.circ.renew";
    public static final String HOLDS_RETRIEVE = "open-ils.circ.holds.retrieve"; // [ahr]
    public static final String HOLD_QUEUE_STATS = "open-ils.circ.hold.queue_stats.retrieve";
    public static final String HOLD_UPDATE = "open-ils.circ.hold.update";
    public static final String HOLD_CANCEL = "open-ils.circ.hold.cancel";
    public static final String HOLD_IS_POSSIBLE = "open-ils.circ.title_hold.is_possible";
    public static final String HOLD_TEST_AND_CREATE = "open-ils.circ.holds.test_and_create.batch";
    public static final String MONEY_BILLING_RETRIEVE = "open-ils.circ.money.billing.retrieve.all";

    /// fielder

    public static final String FIELDER = "open-ils.fielder";
    public static final String FIELDER_BMP_ATOMIC = "open-ils.fielder.bmp.atomic";

    /// pcrud

    public static final String PCRUD_SERVICE = "open-ils.pcrud";
    public static final String RETRIEVE_BRE = "open-ils.pcrud.retrieve.bre";
    public static final String RETRIEVE_MRA = "open-ils.pcrud.retrieve.mra";
    public static final String SEARCH_MRA = "open-ils.pcrud.search.mra.atomic";
    public static final String SEARCH_MRAF = "open-ils.pcrud.search.mraf.atomic";
    public static final String SEARCH_SMS_CARRIERS = "open-ils.pcrud.search.csc.atomic"; // [csc]

    /// search

    public static final String SEARCH = "open-ils.search";
    public static final String MULTICLASS_QUERY = "open-ils.search.biblio.multiclass.query";
    public static final String MODS_SLIM_RETRIEVE = "open-ils.search.biblio.record.mods_slim.retrieve";
    public static final String MODS_SLIM_BATCH = "open-ils.search.biblio.record.mods_slim.batch.retrieve.atomic";
    public static final String COPY_LOCATION_COUNTS = "open-ils.search.biblio.copy_location_counts.summary.retrieve";
    public static final String COPY_STATUS_ALL = "open-ils.search.config.copy_status.retrieve.all"; // [ccs]
    public static final String COPY_COUNT = "open-ils.search.biblio.record.copy_count";
    public static final String ASSET_CALL_NUMBER_RETRIEVE = "open-ils.search.asset.call_number.retrieve";
    public static final String METARECORD_MODS_SLIM_RETRIEVE = "open-ils.search.biblio.metarecord.mods_slim.retrieve";
    public static final String ASSET_COPY_RETRIEVE = "open-ils.search.asset.copy.retrieve";
    public static final String MODS_FROM_COPY = "open-ils.search.biblio.mods_from_copy";

    /// serial

    public static final String SERVICE_SERIAL = "open-ils.serial";
    public static final String METHOD_FETCH_ISSUANCE = "open-ils.serial.issuance.pub_fleshed.batch.retrieve";

    /// misc
    public static final String ILS_VERSION = "opensrf.open-ils.system.ils_version";

    /// general

    public static final String DATE_PATTERN = "yyyy-MM-dd'T'hh:mm:ssZ";
    private static final String TAG = Api.class.getSimpleName();

    // get date string to pass to API methods
    public static String formatDate(Date date) {
        final SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN);
        return sdf.format(date);
    }

    // parse date string returned from API methods
    public static Date parseDate(String dateString) {

        if (dateString == null)
            return null;

        Date date = null;
        final SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN);

        try {
            date = sdf.parse(dateString);
        } catch (ParseException e) {
            Analytics.logException(e);
            date = new Date();
        }

        return date;
    }

    public static Integer formatBoolean(Boolean obj) {
        return obj ? 1 : 0;
    }

    // parse bool string returned from API methods
    public static Boolean parseBoolean(Object obj) {
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else if (obj instanceof String) {
            String s = (String) obj;
            return (s != null && s.equals("t"));
        } else {
            return false;
        }
    }

    /**
     * Return o as an Integer
     *
     * Sometimes search returns a count as a json number ("count":0), sometimes a string ("count":"1103").
     * Seems to be the same for result "ids" list (See Issue #1).  Handle either form and return as an int.
     */
    public static Integer parseInteger(Object o, Integer dflt) {
        if (o instanceof Integer) {
            return (Integer)o;
        } else if (o instanceof String) {
            // I have seen settings with value=null, e.g. opac.default_search_location
            if ("null".equals(o) || TextUtils.isEmpty((String) o))
                return null;
            try {
                return Integer.parseInt((String) o);
            } catch (NumberFormatException e) {
                Analytics.logException(e);
                return null;
            }
        } else {
            Analytics.logException(new ShouldNotHappenException("unexpected type: "+o));
            return dflt;
        }
    }
    public static Integer parseInteger(Object o) {
        return parseInteger(o, null);
    }

    // Some queries return at times a list of String ids and at times a list of Integer ids,
    // see Issue #1 and PINES Crashlytics #28.
    public static List<String> parseIdsList(Object o) {
        ArrayList<String> ret = new ArrayList<>();
        if (o instanceof List) {
            for (Object elem: (List<?>) o) {
                Integer i = parseInteger(elem);
                if (i != null) {
                    ret.add(i.toString());
                }
            }
        }
        return ret;
    }

    /**
     * Return o as a double
     */
    public static Double parseDouble(Object o) {
        if (o instanceof Double) {
            return (Double) o;
        } else if (o instanceof Float) {
            return (Double) o;
        } else if (o instanceof String) {
            try {
                return Double.parseDouble((String) o);
            } catch (NumberFormatException e) {
                Analytics.logException(e);
                return null;
            }
        } else {
            Analytics.logException(new ShouldNotHappenException("unexpected type: "+o));
            return null;
        }
    }
}
