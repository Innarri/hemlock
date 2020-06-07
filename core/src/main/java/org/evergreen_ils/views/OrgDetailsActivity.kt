/*
 * Copyright (c) 2020 Kenneth H. Cox
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

package org.evergreen_ils.views

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.core.util.Pair
import org.evergreen_ils.Api
import org.evergreen_ils.R
import org.evergreen_ils.accountAccess.AccountAccess
import org.evergreen_ils.android.Log
import org.evergreen_ils.system.EvergreenServer
import org.evergreen_ils.system.Organization
import org.evergreen_ils.utils.ui.BaseActivity
import org.evergreen_ils.utils.ui.OrgArrayAdapter
import org.evergreen_ils.utils.ui.ProgressDialogSupport
import org.opensrf.util.OSRFObject
import java.util.*

private const val TAG = "OrgDetailsActivity"

class OrgDetailsActivity : BaseActivity() {

    private var orgSpinner: Spinner? = null
    private var day0Hours: TextView? = null
    private var day1Hours: TextView? = null
    private var day2Hours: TextView? = null
    private var day3Hours: TextView? = null
    private var day4Hours: TextView? = null
    private var day5Hours: TextView? = null
    private var day6Hours: TextView? = null
    private var email: TextView? = null
    private var phone: TextView? = null
    private lateinit var orgDetailsRunnable: Runnable
    private var progress: ProgressDialogSupport? = null

    private var orgID: Int? = null
    private var org: Organization? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isRestarting) return

        setContentView(R.layout.activity_org_details)

        orgID = intent.getIntExtra("orgID", 1)

        orgSpinner = findViewById(R.id.org_details_spinner)
        day0Hours = findViewById(R.id.org_details_day0hours)
        day1Hours = findViewById(R.id.org_details_day1hours)
        day2Hours = findViewById(R.id.org_details_day2hours)
        day3Hours = findViewById(R.id.org_details_day3hours)
        day4Hours = findViewById(R.id.org_details_day4hours)
        day5Hours = findViewById(R.id.org_details_day5hours)
        day6Hours = findViewById(R.id.org_details_day6hours)
        email = findViewById(R.id.org_details_email)
        phone = findViewById(R.id.org_details_phone)

        progress = ProgressDialogSupport()

        initOrgSpinner()
        initOrgDetailsRunnable()
        initButtons()
    }

    override fun onDestroy() {
        progress?.dismiss()
        super.onDestroy()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, object{}.javaClass.enclosingMethod?.name)

        fetchData()
    }

    private fun initOrgSpinner() {
        val defaultOrgId = AccountAccess.getInstance().homeOrgID
        val pair: Pair<ArrayList<String?>, Int> = EvergreenServer.getInstance().getOrganizationSpinnerLabelsAndSelectedIndex(defaultOrgId)
        val selectedOrgPos = pair.second
        val adapter: ArrayAdapter<String> = OrgArrayAdapter(this, R.layout.org_item_layout, pair.first, false)
        orgSpinner?.adapter = adapter
        orgSpinner?.setSelection(selectedOrgPos)
        orgSpinner?.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                org = EvergreenServer.getInstance().visibleOrganizations[position]
                orgID = org?.id
                fetchData()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun initOrgDetailsRunnable() {
        orgDetailsRunnable = Runnable {
            runOnUiThread { progress?.show(this, getString(R.string.msg_loading_details)) }
            val obj = AccountAccess.getInstance().getHoursOfOperation(orgID);
            runOnUiThread { onOrgsLoaded(); onHoursLoaded(obj); progress?.dismiss() }
        }
    }

    private fun initButtons() {
        email?.setOnClickListener {
            Log.d(TAG, "here")
        }
        phone?.setOnClickListener {
            dialPhone(org?.phone)
        }
    }

    private fun hoursOfOperation(obj: OSRFObject?, day: Int): String? {
        val openTimeApi = obj?.getString("dow_${day}_open")
        val closeTimeApi = obj?.getString("dow_${day}_close")
        val openTime = Api.parseHours(openTimeApi)
        val closeTime = Api.parseHours(closeTimeApi)
        if (openTime == null || closeTime == null) {
            return null
        }
        if (openTimeApi == closeTimeApi) {
            return "closed"
        }
        val openTimeLocal = Api.formatHoursForOutput(openTime)
        val closeTimeLocal = Api.formatHoursForOutput(closeTime)
        return "$openTimeLocal - $closeTimeLocal"
    }

    private fun onHoursLoaded(obj: OSRFObject?) {
        day0Hours?.text = hoursOfOperation(obj, 0)
        day1Hours?.text = hoursOfOperation(obj, 1)
        day2Hours?.text = hoursOfOperation(obj, 2)
        day3Hours?.text = hoursOfOperation(obj, 3)
        day4Hours?.text = hoursOfOperation(obj, 4)
        day5Hours?.text = hoursOfOperation(obj, 5)
        day6Hours?.text = hoursOfOperation(obj, 6)
    }

    private fun onOrgsLoaded() {
        email?.text = org?.email
        phone?.text = org?.phone
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun fetchData() {
        Thread(orgDetailsRunnable).start()
    }
}