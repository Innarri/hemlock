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

import org.evergreen_ils.Api
import org.evergreen_ils.data.Account
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.opensrf.util.OSRFObject

class AccountTest {

    var sessionObj = OSRFObject(mapOf<String, Any?>(
            "id" to 42,
            "home_ou" to 69,
            "day_phone" to "508-555-1212"
    ))
    var cardObj = OSRFObject(mapOf<String, Any?>("barcode" to "1234"))

    @Before
    fun setUp() {
    }

    @Test
    fun test_basic() {
        val account = Account("hemlock")
        assertEquals("hemlock", account.username)
        assertNull(account.authToken)
    }

    @Test
    fun test_loadSession() {
        val account = Account("hemlock", "636f7666656665")
        assertEquals("636f7666656665", account.authTokenOrThrow())

        account.loadSession(sessionObj)
        assertEquals(42, account.id)
        assertEquals("508-555-1212", account.phoneNumber)
    }

    fun makeSetting(name: String, value: Any?): OSRFObject {
        return OSRFObject(mapOf<String, Any?>("name" to name, "value" to value))
    }

    @Test
    fun test_loadFleshedUserSettings() {
        val account = Account("hemlock", "636f7666656665")
        account.loadSession(sessionObj)

        val settingsObj = arrayListOf(
                makeSetting(Api.USER_SETTING_DEFAULT_PHONE, "617-555-1212"),
                makeSetting(Api.USER_SETTING_HOLD_NOTIFY,"email|sms")
        )
        val fleshedUserSettingsObj = OSRFObject(mapOf<String, Any?>(
                "card" to cardObj,
                "settings" to settingsObj
        ))
        account.loadFleshedUserSettings(fleshedUserSettingsObj)

        assertEquals(42, account.id)
        assertEquals("617-555-1212", account.phoneNumber)
        assertEquals(69, account.pickupOrg)
        assertEquals(69, account.searchOrg)
        assertNull(account.smsCarrier)
        assertNull(account.smsNumber)
        assertTrue(account.notifyByEmail)
        assertFalse(account.notifyByPhone)
        assertTrue(account.notifyBySMS)
    }
}
