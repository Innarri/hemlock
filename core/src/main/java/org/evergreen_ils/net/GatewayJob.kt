/*
 * Copyright (c) 2019 Kenneth H. Cox
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

package org.evergreen_ils.net

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.evergreen_ils.data.EgOrg
import org.evergreen_ils.data.Organization
import org.evergreen_ils.system.Log

object GatewayJob {

    // EXPERIMENTAL
    // usage: fetchAllOrgSettingsAsync().join()
    //suspend fun fetchAllOrgSettingsAsync(org: Organization?) = GlobalScope.launch {
    suspend fun fetchAllOrgSettingsAsync(org: Organization?) = GlobalScope.async {
        val orgs = if (org != null) listOf(org) else EgOrg.orgs
        for (org in orgs) {
            if (!org.settingsLoaded) {
                async { org.loadSettings(Gateway.actor.fetchOrgSettings(org.id));
                    Log.d(TAG, "[kcxxx] org ${org.id} loaded")
                }
            }
        }
    }
}
