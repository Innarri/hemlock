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

package net.kenstir.apps.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import org.opensrf.util.OSRFObject

class OSRFObjectTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
        }
    }

    @Test
    fun test_basic() {
        val map = mutableMapOf<String, Any?>(
                "id" to 42,
                "home_ou" to 69,
                "day_phone" to "508-555-1212"
        )
        val obj = OSRFObject(map)
        assertEquals(42, obj.getInt("id"))
        assertEquals("508-555-1212", obj.getString("day_phone"))
        assertNull(obj.getString("unset"))
    }

    @Test
    fun test_apiToPrimative() {
        val map = mapOf<String, Any?>(
                "juvenile" to "f",
                "active" to "t"
        )
        val obj = OSRFObject(map)
        assertEquals(true, obj.getBoolean("active"))
        assertEquals(false, obj.getBoolean("juvenile"))
    }
}