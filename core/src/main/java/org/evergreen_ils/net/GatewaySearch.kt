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

import org.evergreen_ils.Api
import org.evergreen_ils.data.Result
import org.opensrf.util.OSRFObject

object GatewaySearch: SearchService {
    override suspend fun fetchAssetCopy(copyId: Int): Result<OSRFObject> {
        return try {
            val ret = Gateway.fetchObject(Api.SEARCH, Api.ASSET_COPY_RETRIEVE, arrayOf(copyId), true)
            Result.Success(ret)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun fetchAssetCallNumber(callNumber: Int): Result<OSRFObject> {
        return try {
            val ret = Gateway.fetchObject(Api.SEARCH, Api.ASSET_CALL_NUMBER_RETRIEVE, arrayOf(callNumber), true)
            Result.Success(ret)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun fetchCopyStatuses(): Result<List<OSRFObject>> {
        return try {
            val ret = Gateway.fetchObjectArray(Api.SEARCH, Api.COPY_STATUS_ALL, arrayOf(), true)
            Result.Success(ret)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun fetchRecordMODS(id: Int): Result<OSRFObject> {
        return try {
            val ret = Gateway.fetchObject(Api.SEARCH, Api.MODS_SLIM_RETRIEVE, arrayOf(id), true)
            Result.Success(ret)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun fetchMetarecordMODS(id: Int): Result<OSRFObject> {
        return try {
            val ret = Gateway.fetchObject(Api.SEARCH, Api.METARECORD_MODS_SLIM_RETRIEVE, arrayOf(id), true)
            Result.Success(ret)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
