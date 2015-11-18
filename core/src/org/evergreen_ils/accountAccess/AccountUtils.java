package org.evergreen_ils.accountAccess;

import android.accounts.*;
import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import org.evergreen_ils.R;
import org.evergreen_ils.auth.Const;
import org.w3c.dom.Text;

import java.io.IOException;

/**
 * Created by kenstir on 11/17/2015.
 */
public class AccountUtils {

    public static String getLibraryUrl(Activity activity, String account_name, String account_type) {
        final AccountManager am = AccountManager.get(activity);
        Account account = new Account(account_name, account_type);
        String library_url = am.getUserData(account, Const.KEY_LIBRARY_URL);

        // compatibility with specific apps like cwmars_app.  If no library_url exists as userdata on the account,
        // get it from the resources.
        if (TextUtils.isEmpty(library_url)) {
            library_url = activity.getString(R.string.ou_library_url);
            if (!TextUtils.isEmpty(library_url)) {
                am.setUserData(account, Const.KEY_LIBRARY_URL, library_url);
            }
        }

        return library_url;
    }

    public static void invalidateAuthToken(Activity activity, String auth_token) {
        Log.i(Const.AUTH_TAG, "invalidateAuthToken "+auth_token);
        final AccountManager am = AccountManager.get(activity);
        final String accountType = activity.getString(R.string.ou_account_type);
        am.invalidateAuthToken(accountType, auth_token);
    }

    public static String getAuthTokenForAccount(Activity activity, String account_name) throws AuthenticatorException, OperationCanceledException, IOException {
        Log.i(Const.AUTH_TAG, "getAuthTokenForAccount "+account_name);
        if (runningOnUIThread() || TextUtils.isEmpty(account_name)) {
            Log.i(Const.AUTH_TAG, "getAuthTokenForAccount returns null");
            return null;
        }
        final AccountManager am = AccountManager.get(activity);
        final String accountType = activity.getString(R.string.ou_account_type);
        final Account account = new Account(account_name, accountType);
        Bundle b = am.getAuthToken(account, Const.AUTHTOKEN_TYPE, null, activity, null, null).getResult();
        final String auth_token = b.getString(AccountManager.KEY_AUTHTOKEN);
        Log.i(Const.AUTH_TAG, "getAuthTokenForAccount " + account_name + " returns " + auth_token);
        return auth_token;
    }

    public static Bundle getAuthToken(Activity activity) throws AuthenticatorException, OperationCanceledException, IOException {
        Log.i(Const.AUTH_TAG, "getAuthToken");
        if (runningOnUIThread())
            return new Bundle();
        final AccountManager am = AccountManager.get(activity);
        final String accountType = activity.getString(R.string.ou_account_type);
        AccountManagerFuture<Bundle> future = am.getAuthTokenByFeatures(accountType, Const.AUTHTOKEN_TYPE, null, activity, null, null, null, null);
        Bundle bnd = future.getResult();
        Log.i(Const.AUTH_TAG, "getAuthToken returns "+bnd);
        return bnd;
    }

    public static boolean runningOnUIThread() {
        return (Looper.myLooper() == Looper.getMainLooper());
    }
}
