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
package org.evergreen_ils.accountAccess.fines;

import java.util.Date;

import org.evergreen_ils.globals.GlobalConfigs;
import org.evergreen_ils.globals.Log;
import org.opensrf.util.OSRFObject;

public class FinesRecord {

    public static String TAG = FinesRecord.class.getSimpleName();
    public String title;
    public String author;
    public Double balance_owed;
    public Double max_fine;
    private Date checkin_time;

    // types are grocery and circulation
    private int type;

    public static int FINE_GROCERY_TYPE = 1;
    public static int FINE_CIRCULATION = 2;

    public FinesRecord(OSRFObject circ, OSRFObject mvr_record,
            OSRFObject mbts_transaction) {

        if (mbts_transaction.get("xact_type").toString().equals("circulation")) {

            title = mvr_record.getString("title");
            author = mvr_record.getString("author");

            if (circ.get("checkin_time") != null) {
                checkin_time = GlobalConfigs.parseDate(circ.getString("checkin_time"));
            } else {
                checkin_time = null;
            }
        } else {
            // grocery
            title = "Grocery billing";
            author = mbts_transaction.getString("last_billing_note");
        }

        try {
            balance_owed = Double.parseDouble(mbts_transaction.getString("total_owed"));
            max_fine = Double.parseDouble(circ.getString("max_fine"));
        } catch(NumberFormatException e) {
            Log.d(TAG, "error converting double", e);
        }
    }

    // Returns status of fine: e.g. returned or fines accruing
    public String getStatus() {

        if (checkin_time != null)
            return "returned";
        if (balance_owed != null && max_fine != null && balance_owed >= max_fine)
            return "maximum fine";
        return "fines accruing";
    }
}
