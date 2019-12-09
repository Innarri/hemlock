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

package org.evergreen_ils.test

import org.evergreen_ils.data.CopyLocationCounts
import org.evergreen_ils.data.EgCopyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.opensrf.util.GatewayResult
import org.opensrf.util.OSRFObject

class CopyLocationCountsTest {

    fun make_ccs(id: Int, name: String, opac_visible: String): OSRFObject {
        return OSRFObject(mapOf<String, Any?>("id" to id, "name" to name, "opac_visible" to opac_visible))
    }

    @Before
    fun setUp() {
        val ccsList = listOf<OSRFObject>(
                make_ccs(1, "Checked out", "t"),
                make_ccs(0, "Available", "t")
        )
        EgCopyStatus.loadCopyStatuses(ccsList)
    }

    @Test
    fun test_basic() {
        val cscJson = """
            {"payload":[[["7","","DVD HARRY","","AV",{"1":1}]]],"status":200}
            """
        val gwResult = GatewayResult.create(cscJson)
        assertNotNull(gwResult)
        val payloadList = gwResult.asArray()
        assertEquals(1, payloadList.size)
        val clcList = CopyLocationCounts.makeArray(payloadList)
        val clc = clcList.first()
        assertEquals(7, clc.orgId)
        assertEquals("", clc.callNumberPrefix)
        assertEquals("DVD HARRY", clc.callNumberLabel)
        assertEquals("", clc.callNumberSuffix)
        assertEquals("AV", clc.copyLocation)
        assertEquals(1, clc.countsByStatus.size)
        assertEquals("1 Checked out", clc.countsByStatusLabel)
        assertEquals("DVD HARRY", clc.callNumber)
    }
}
