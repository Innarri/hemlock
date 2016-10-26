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
package org.evergreen_ils.globals;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;
import org.evergreen_ils.searchCatalog.Organisation;
import org.evergreen_ils.searchCatalog.SearchCatalog;
import org.open_ils.idl.IDLException;
import org.open_ils.idl.IDLParser;
import org.opensrf.net.http.HttpConnection;
import org.opensrf.util.OSRFObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.*;

public class GlobalConfigs {

    public static final String IDL_CLASSES_USED = "acn,acp,ahr,ahtc,aou,au,bmp,cbreb,cbrebi,cbrebin,cbrebn,ccs,circ,ex,mbt,mbts,mous,mra,mraf,mus,mvr,perm_ex";
    //todo: unused mra?
    // extra classes needed for open-ils.actor.user.fleshed.retrieve: ac,au,aua,auact,cuat
    //public static String IDL_FILE_FROM_ASSETS = "fm_IDL.xml";
    private static String mLibraryUrl = "";
    private static HttpConnection conn = null;

    private static String TAG = "GlobalConfigs";
    
    private static Boolean mIsDebuggable = null;

    private static boolean loadedIDL = false;

    private static boolean loadedOrgTree = false;

    // two days notification before checkout expires, this can be modified from
    // preferences
    public static int NOTIFICATION_BEFORE_CHECKOUT_EXPIRATION = 2;

    /** The locale. */
    public String locale = "en-US";

    private static GlobalConfigs mInstance = null;

    /** The organisations. */
    public ArrayList<Organisation> organisations;

    /** The collections request. */
    private String collectionsRequest = "/opac/common/js/" + locale + "/OrgTree.js";

    private GlobalConfigs() {
    }

    public static GlobalConfigs getInstance() {
        if (mInstance == null)
            mInstance = new GlobalConfigs();
        return mInstance;
    }

    public static String getUrl() {
        GlobalConfigs globalConfigs = getInstance();
        return globalConfigs.mLibraryUrl;
    }

    public static String getUrl(String relativeUrl) {
        GlobalConfigs globalConfigs = getInstance();
        return globalConfigs.mLibraryUrl + relativeUrl;
    }

    public static String getIDLUrl() {
        ArrayList<String> params = new ArrayList<String>(32);
        for (String className : TextUtils.split(IDL_CLASSES_USED, ",")) {
            params.add("class=" + className);
        }
        StringBuilder sb = new StringBuilder(512);
        sb.append(mLibraryUrl).append("/reports/fm_IDL.xml?");
        sb.append(TextUtils.join("&", params));
        return sb.toString();
    }

    public boolean initialize(Context context, String library_url) throws IOException, IDLException {
        enableHttpResponseCache(context);
        mIsDebuggable = (0 != (context.getApplicationInfo().flags &= ApplicationInfo.FLAG_DEBUGGABLE));
        if (!TextUtils.equals(library_url, mLibraryUrl) || !loadedIDL) {
            mLibraryUrl = library_url;
            conn = null; // must come before loadXXX()
            loadedIDL = false;
            loadIDL();
            loadCopyStatusesAvailable();
            return true;
        }
        return false;
    }

    public static boolean isDebuggable() {
        if (mIsDebuggable == null)
            return false;
        return mIsDebuggable;
    }

    public static HttpConnection gatewayConnection() {
        if (conn == null && !TextUtils.isEmpty(mLibraryUrl)) {
            try {
                conn = new HttpConnection(mLibraryUrl + "/osrf-gateway-v1");
            } catch (MalformedURLException e) {
                Log.d(TAG, "unable to open connection", e);
            }
        }
        return conn;
    }

    private static void enableHttpResponseCache(Context context) {
        try {
            long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
            File httpCacheDir = new File(context.getCacheDir(), "volley");//try to reuse same cache dir as volley
            Class.forName("android.net.http.HttpResponseCache")
                    .getMethod("install", File.class, long.class)
                    .invoke(null, httpCacheDir, httpCacheSize);
        } catch (Exception httpResponseCacheNotAvailable) {
            Log.d(TAG, "HTTP response cache is unavailable.");
        }
    }

    private void loadIDL() throws IOException, IDLException {
        try {
            Log.d(TAG, "loadIDLFile.start");
            long now_ms = System.currentTimeMillis();
            InputStream in_IDL = Utils.getNetInputStream(getIDLUrl());
            IDLParser parser = new IDLParser(in_IDL);
            parser.setKeepIDLObjects(false);
            now_ms = Log.logElapsedTime(TAG, now_ms, "loadIDL.init");
            parser.parse();
            now_ms = Log.logElapsedTime(TAG, now_ms, "loadIDL.total");
            loadedIDL = true;
        } finally {
            Utils.closeNetInputStream();
        }
    }

    public void addOrganization(OSRFObject obj, int level) {
        Organisation org = new Organisation();
        org.level = level;
        org.id = obj.getInt("id");
        org.name = obj.getString("name");
        org.shortname = obj.getString("shortname");
        org.orgType = obj.getInt("ou_type");

        String opac_visible = obj.getString("opac_visible");
        org.opac_visible = TextUtils.equals(opac_visible, "t");

        org.indentedDisplayPrefix = new String(new char[level]).replace("\0", "  ");
        //Log.d(TAG, "kcxxx: id="+org.id+" level="+org.level+" name="+org.name+" vis="+(org.opac_visible ? "1" : "0"));
        if (org.opac_visible)
            organisations.add(org);

        List<OSRFObject> children = null;
        try {
            children = (List<OSRFObject>) obj.get("children");
            for (OSRFObject child : children) {
                addOrganization(child, level + 1);
            }
        } catch (Exception e) {
            Log.d(TAG, "addOrganization caught exception decoding children of "+org.name, e);
        }
    }

    public void loadOrganizations(OSRFObject orgTree) {
        organisations = new ArrayList<Organisation>();
        addOrganization(orgTree, 0);

        // If the org tree is too big, then an indented list is unwieldy.
        // Convert it into a flat list sorted by org.name.
        if (organisations.size() > 25) {
            Collections.sort(organisations, new Comparator<Organisation>() {
                @Override
                public int compare(Organisation a, Organisation b) {
                    // top-level OU appears first
                    if (a.level == 0) return -1;
                    if (b.level == 0) return 1;
                    return a.name.compareTo(b.name);
                }
            });
            for (Organisation o : organisations) {
                o.indentedDisplayPrefix = "";
            }
        }
    }

    // todo candidate for on-demand loading
    public void loadCopyStatusesAvailable() {
        SearchCatalog search = SearchCatalog.getInstance();
        search.getCopyStatuses();
    }

    public Organisation getOrganization(int id) {
        for (int i = 0; i < organisations.size(); i++) {
            if (organisations.get(i).id == id)
                return organisations.get(i);
        }

        return null;
    }

    public String getOrganizationName(int id) {
        Organisation org = getOrganization(id);
        if (org == null) {
            return null;
        } else {
            return org.name;
        }
    }
}
