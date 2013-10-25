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
package org.evergreen.android.globals;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.evergreen.android.accountAccess.SessionNotFoundException;
import org.opensrf.Method;
import org.opensrf.net.http.GatewayRequest;
import org.opensrf.net.http.HttpConnection;
import org.opensrf.net.http.HttpRequest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.ImageView;

public class Utils {

    /**
     * Gets the net page content.
     * 
     * @param url
     *            the url of the page to be retrieved
     * @return the net page content
     */
    public static String getNetPageContent(String url) {

        String result = "";

        HttpResponse response = null;

        try {
            HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet(url);
            response = client.execute(request);
        } catch (Exception e) {
            System.out.println("Exception to GET page " + url);
        }
        StringBuilder str = null;

        try {
            InputStream in = response.getEntity().getContent();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in));
            str = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                str.append(line);
            }
            in.close();
        } catch (Exception e) {
            System.err
                    .println("Error in retrieving response " + e.getMessage());
        }

        result = str.toString();

        return result;
    }

    public static InputStream getNetInputStream(String url) {

        InputStream in = null;

        HttpResponse response = null;

        try {
            HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet(url);
            response = client.execute(request);
        } catch (Exception e) {
            System.out.println("Exception to GET page " + url);
        }

        try {
            in = response.getEntity().getContent();

            return in;
        } catch (Exception e) {
            System.err
                    .println("Error in retrieving response " + e.getMessage());
        }

        return in;
    }

    /**
     * Checks to see if network access is enabled
     * 
     * @throws NoNetworkAccessException
     *             if neither data connection or wifi are enabled
     *             NoAccessToServer if the library remote server can't be
     *             reached
     * 
     */
    public static boolean checkNetworkStatus(ConnectivityManager cm)
            throws NoNetworkAccessException, NoAccessToServer {

        boolean networkAccessEnabled = false;
        boolean httpAddressAccessReachable = false;

        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni != null) {
            if (ni.getType() == ConnectivityManager.TYPE_WIFI)
                if (ni.isConnected()) {
                    networkAccessEnabled = true;
                }
            if (ni.getType() == ConnectivityManager.TYPE_MOBILE)
                if (ni.isConnected()) {
                    networkAccessEnabled = true;
                }
        }

        System.out.println("Network access " + networkAccessEnabled);

        if (!networkAccessEnabled)
            throw new NoNetworkAccessException();
        
        if (networkAccessEnabled) {
            httpAddressAccessReachable = checkIfNetAddressIsReachable(GlobalConfigs.httpAddress);
            if (httpAddressAccessReachable == false)
                throw new NoAccessToServer();
        }
        return networkAccessEnabled;

    }

    public static boolean checkIfNetAddressIsReachable(String url)
            throws NoAccessToServer {

    	// kcxxx: old method of fetching the URL to see if the net is "reachable" was not good.
    	// For now just check if the url is empty.
    	return (url.length() > 0);
    }

    public static void bookCoverImage(ImageView picture, String imageID,
            int size) {

        String urlS = (GlobalConfigs.httpAddress
                + "/opac/extras/ac/jacket/small/" + imageID);

        Bitmap bmp = null; // create a new Bitmap variable called bmp, and
                           // initialize it to null

        try {

            URL url = new URL(urlS); // create a URL object from the urlS string
                                     // above
            URLConnection conn = url.openConnection(); // save conn as a
                                                       // URLConnection

            conn.connect(); // connect to the URLConnection conn
            InputStream is = conn.getInputStream(); // get the image from the
                                                    // URLConnection conn using
                                                    // InputStream is
            BufferedInputStream bis = new BufferedInputStream(is, 8*1024); // create a
                                                                   // BufferedInputStream
                                                                   // called bis
                                                                   // from is
            bmp = BitmapFactory.decodeStream(bis); // use bis to convert the
                                                   // stream to a bitmap image,
                                                   // and save it to bmp
            int bmpHeight = bmp.getHeight(); // stores the original height of
                                             // the image
            if (bmpHeight != 1) {
                double scale = size / (double) bmpHeight; // sets the scaling
                                                          // number to match the
                                                          // desired size
                double bmpWidthh = (double) bmp.getWidth() * scale; // scales
                                                                    // and
                                                                    // stores
                                                                    // the
                                                                    // original
                                                                    // width
                                                                    // into the
                                                                    // desired
                                                                    // one
                int bmpWidth = (int) bmpWidthh; // gets the width of the picture
                                                // and saves it
                bmp = Bitmap.createScaledBitmap(bmp, bmpWidth, size, true); // creates
                                                                            // and
                                                                            // stores
                                                                            // a
                                                                            // new
                                                                            // bmp
                                                                            // with
                                                                            // desired
                                                                            // dimensions
            }

        } catch (MalformedURLException e) { // catch errors
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        picture.setImageBitmap(bmp); // send the Bitmap image bmp to pic, and
                                     // call the method to set the image.

    }

    public static Object doRequest(HttpConnection conn, String service,
            String methodName, String authToken, ConnectivityManager cm,
            Object[] params) throws SessionNotFoundException,
            NoNetworkAccessException, NoAccessToServer {

        // check for network connectivity
        checkNetworkStatus(cm);

        // check to see if EG http server is reachable
        checkIfNetAddressIsReachable(GlobalConfigs.httpAddress);

        // TODO check params and throw errors
        Method method = new Method(methodName);

        System.out.println("Method :" + methodName + " param:");
        for (int i = 0; i < params.length; i++) {
            method.addParam(params[i]);
            System.out.print("Param " + i + ":" + params[i]);
        }
        // need space
        System.out.println();

        // sync request
        HttpRequest req = new GatewayRequest(conn, service, method).send();
        Object resp;

        while ((resp = req.recv()) != null) {
            System.out.println("Sync Response: " + resp);
            Object response = (Object) resp;

            String textcode = null;
            try {
                textcode = ((Map<String, String>) response).get("textcode");
            } catch (Exception e) {
                System.err.println("Exception in retreive" + e.getMessage());
            }

            if (textcode != null) {
                if (textcode.equals("NO_SESSION")) {
                    // System.out.println("REQUIRE NEW SESSION");
                    throw new SessionNotFoundException();
                }

            }

            return response;

        }
        return null;

    }

    // does not require authToken
    public static Object doRequest(HttpConnection conn, String service,
            String methodName, ConnectivityManager cm, Object[] params)
            throws NoNetworkAccessException, NoAccessToServer {

        // check for network connectivity
        checkNetworkStatus(cm);

        // check to see if EG http server is reachable
        checkIfNetAddressIsReachable(GlobalConfigs.httpAddress);

        Method method = new Method(methodName);

        System.out.println("Method :" + methodName + ":");
        for (int i = 0; i < params.length; i++) {
            method.addParam(params[i]);
            System.out.println("Param " + i + ": " + params[i]);
        }

        // sync request
        HttpRequest req = new GatewayRequest(conn, service, method).send();
        Object resp;

        while ((resp = req.recv()) != null) {
            System.out.println("Sync Response: " + resp);
            Object response = (Object) resp;

            return response;

        }
        return null;

    }

    // does not throw exception
    // is fast than with checks for multiple method invocations like in search
    public static Object doRequestSimple(HttpConnection conn, String service,
            String methodName, Object[] params) {
        Method method = new Method(methodName);
        System.out.println("Method :" + methodName);
        for (int i = 0; i < params.length; i++) {
            method.addParam(params[i]);
            System.out.println("Param " + i + ":" + params[i]);
        }

        // sync request
        HttpRequest req = new GatewayRequest(conn, service, method).send();
        Object resp;

        while ((resp = req.recv()) != null) {
            System.out.println("Sync Response: " + resp);
            Object response = (Object) resp;

            return response;

        }
        return null;
    }

    public static ShowServerNotAvailableRunnable showServerNotAvailableDialog(
            Context context) {

        return new ShowServerNotAvailableRunnable(context);
    }

    public static ShowNetworkNotAvailableRunnable showNetworkNotAvailableDialog(
            Context context) {

        return new ShowNetworkNotAvailableRunnable(context);
    }

}
