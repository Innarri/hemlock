/*
 * Copyright (C) 2015 Kenneth H. Cox
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

package org.evergreen_ils.searchCatalog;

import android.content.Context;
import android.text.TextUtils;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import org.evergreen_ils.accountAccess.AccountAccess;
import org.evergreen_ils.globals.GlobalConfigs;
import org.evergreen_ils.globals.Log;
import org.evergreen_ils.globals.Utils;
import org.evergreen_ils.net.GatewayJsonObjectRequest;
import org.evergreen_ils.net.VolleyWrangler;
import org.opensrf.util.GatewayResponse;
import org.opensrf.util.OSRFObject;

/** Async interface for loading RecordInfo metadata
 *
 * Created by kenstir on 12/27/2015.
 */
public class RecordLoader {
    private static final String TAG = RecordLoader.class.getSimpleName();

    public interface ResponseListener {
        public void onMetadataLoaded();
        public void onSearchFormatLoaded();
    }
    public interface Listener {
        public void onDataAvailable();
    }

    public static void fetch(final RecordInfo record, final Context context, final ResponseListener responseListener) {
        fetchBasicMetadata(record, context, responseListener);
        fetchSearchFormat(record, context, responseListener);
    }

    public static void fetchBasicMetadata(final RecordInfo record, Context context, final ResponseListener responseListener) {
        Log.d(TAG, "fetchBasicMetadata id="+record.doc_id+ " title="+record.title);
        if (!TextUtils.isEmpty(record.title)) {
            responseListener.onMetadataLoaded();
        } else {
            String url = GlobalConfigs.getUrl(Utils.buildGatewayUrl(
                    SearchCatalog.SERVICE, SearchCatalog.METHOD_SLIM_RETRIEVE,
                    new Object[]{record.doc_id}));
            GatewayJsonObjectRequest r = new GatewayJsonObjectRequest(
                    url,
                    new Response.Listener<GatewayResponse>() {
                        @Override
                        public void onResponse(GatewayResponse response) {
                            RecordInfo.updateFromMODSResponse(record, response.payload);
                            responseListener.onMetadataLoaded();
                        }
                    },
                    VolleyWrangler.logErrorListener(TAG));
            VolleyWrangler.getInstance(context).addToRequestQueue(r);
        }
    }

    public static void fetchSearchFormat(final RecordInfo record, Context context, final ResponseListener responseListener) {
        Log.d(TAG, "fetchSearchFormat id="+record.doc_id+ " format="+record.search_format);
        if (!TextUtils.isEmpty(record.search_format)) {
            responseListener.onSearchFormatLoaded();
        } else {
            // todo newer EG supports "ANONYMOUS" PCRUD which should be faster w/o authToken
            String url = GlobalConfigs.getUrl(Utils.buildGatewayUrl(
                    AccountAccess.PCRUD_SERVICE, AccountAccess.PCRUD_METHOD_RETRIEVE_MRA,
                    new Object[]{AccountAccess.getInstance().getAuthToken(), record.doc_id}));
            GatewayJsonObjectRequest r = new GatewayJsonObjectRequest(
                    url,
                    new Response.Listener<GatewayResponse>() {
                        @Override
                        public void onResponse(GatewayResponse response) {
                            record.search_format = AccountAccess.getSearchFormatFromMRAResponse((OSRFObject) response.payload);
                            responseListener.onSearchFormatLoaded();
                        }
                    },
                    VolleyWrangler.logErrorListener(TAG));
            VolleyWrangler.getInstance(context).addToRequestQueue(r);
        }
    }

    public static void fetchCopyCount(final RecordInfo record, final int orgID, Context context, final Listener listener) {
        if (record.copyCountListInfo != null) {
            listener.onDataAvailable();
        } else {
            String url = GlobalConfigs.getUrl(Utils.buildGatewayUrl(
                    SearchCatalog.SERVICE, SearchCatalog.METHOD_GET_COPY_COUNT,
                    new Object[]{orgID, record.doc_id}));
            GatewayJsonObjectRequest r = new GatewayJsonObjectRequest(
                    url,
                    new Response.Listener<GatewayResponse>() {
                        @Override
                        public void onResponse(GatewayResponse response) {
                            SearchCatalog.setCopyCountListInfo(record, response);
                            listener.onDataAvailable();
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.d(TAG, "caught", error);
                            SearchCatalog.setCopyCountListInfo(record, null);
                        }
                    });
            VolleyWrangler.getInstance(context).addToRequestQueue(r);
        }
    }
}
