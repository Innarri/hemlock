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
import org.evergreen_ils.Api
import org.evergreen_ils.data.EgOrg
import org.evergreen_ils.system.Log
import org.evergreen_ils.data.Organization
import org.opensrf.util.OSRFObject

typealias JSONDictionary = Map<String, Any?>

object GatewayActor: ActorService {
    override suspend fun fetchServerVersion(): String {
        return Gateway.fetchNoCache(Api.ACTOR, Api.ILS_VERSION, arrayOf()) { response ->
            response.asString()
        }
    }

    override suspend fun fetchOrgTypes(): List<OSRFObject> {
        return Gateway.fetchObjectArray(Api.ACTOR, Api.ORG_TYPES_RETRIEVE, arrayOf(), true)
    }

    override suspend fun fetchOrgTree(): OSRFObject {
        return Gateway.fetchObject(Api.ACTOR, Api.ORG_TREE_RETRIEVE, arrayOf(), true)
    }

    override suspend fun fetchOrgSettings(orgID: Int): JSONDictionary {
        val settings = arrayListOf(Api.SETTING_ORG_UNIT_NOT_PICKUP_LIB,
                Api.SETTING_CREDIT_PAYMENTS_ALLOW)
        val args = arrayOf<Any?>(orgID, settings, Api.ANONYMOUS)
        return Gateway.fetch(Api.ACTOR, Api.ORG_UNIT_SETTING_BATCH, args, true) {
            it.asMap()
        }
    }

    override suspend fun fetchFleshedUser(authToken: String, userID: Int): OSRFObject {
        val settings = listOf("card", "settings")
        val args = arrayOf<Any?>(authToken, userID, settings)
        return Gateway.fetchObject(Api.ACTOR, Api.USER_FLESHED_RETRIEVE, args, true)
    }

    override suspend fun fetchMessages(authToken: String, userID: Int): List<OSRFObject> {
        val args = arrayOf(authToken, userID, null)
        return Gateway.fetchObjectArray(Api.ACTOR, Api.MESSAGES_RETRIEVE, args, false)
    }
}
