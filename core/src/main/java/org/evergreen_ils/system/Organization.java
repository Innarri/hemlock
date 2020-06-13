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
package org.evergreen_ils.system;

import org.evergreen_ils.utils.TextUtils;
import org.opensrf.util.OSRFObject;

public class Organization /*implements Serializable*/ {
    private static final String TAG = Organization.class.getSimpleName();

    public static final Integer consortiumOrgId = 1;

    public Integer level = null;
    public Integer id = null;
    public Integer parent_ou = null;
    public String name = null;
    public String shortname = null;
    public String email = null;
    public String phone = null;
    public OrgType orgType = null;
    public Integer mailingAddressID = null;
    public OSRFObject addressObj = null;
    public String indentedDisplayPrefix = "";

    public Boolean opac_visible = null;

    public Boolean settings_loaded = false;
    public Boolean setting_is_pickup_location = null; // null=not loaded
    public Boolean setting_allow_credit_payments = null; // null=not loaded
    public String setting_info_url = null;

    public Organization() {
    }

    public String getSpinnerLabel() {
        return indentedDisplayPrefix + name;
    }

    public String getAddress(String separator) {
        StringBuilder sb = new StringBuilder();
        if (addressObj == null) return null;
        sb.append(addressObj.getString("street1"));
        String s = addressObj.getString("street2");
        if (!TextUtils.isEmpty(s)) { sb.append(separator).append(s); }
        sb.append(separator).append(addressObj.getString("city"));
        sb.append(", ").append(addressObj.getString("state"));
        //sb.append(separator).append(addressObj.getString("country"));
        sb.append(" ").append(addressObj.getString("post_code"));
        return sb.toString();
    }

    public boolean isPickupLocation() {
        if (setting_is_pickup_location != null) {
            return setting_is_pickup_location;
        } else {
            return defaultIsPickupLocation();
        }
    }

    private boolean defaultIsPickupLocation() {
        if (orgType == null)
            return true;//should not happen
        return orgType.can_have_vols;
    }

    public boolean isConsortium() {
        return parent_ou == null;
    }
}
