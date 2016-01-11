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

package org.evergreen_ils.utils.ui;

import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import org.evergreen_ils.R;
import org.evergreen_ils.accountAccess.AccountAccess;
import org.evergreen_ils.globals.AppState;

/**
 * Created by kenstir on 11/21/2015.
 */
public class ActionBarUtils {
    public static void initActionBarForActivity(ActionBarActivity activity, boolean isMainActivity) {
        ActionBar actionBar = activity.getSupportActionBar();
        actionBar.setSubtitle(String.format(activity.getString(R.string.ou_activity_subtitle),
                AppState.getString(AppState.LIBRARY_NAME),
                AccountAccess.userName));
        if (!isMainActivity) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static void initActionBarForActivity(ActionBarActivity activity) {
        initActionBarForActivity(activity, false);
    }
}