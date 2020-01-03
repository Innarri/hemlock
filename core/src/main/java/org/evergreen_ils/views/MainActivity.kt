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

package org.evergreen_ils.views

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.MenuItemCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.evergreen_ils.R
import org.evergreen_ils.android.AccountUtils
import org.evergreen_ils.accountAccess.bookbags.BookBagActivity
import org.evergreen_ils.accountAccess.checkout.CheckoutsActivity
import org.evergreen_ils.accountAccess.fines.FinesActivity
import org.evergreen_ils.accountAccess.holds.HoldsActivity
import org.evergreen_ils.android.App
import org.evergreen_ils.data.EgSms
import org.evergreen_ils.data.Result
import org.evergreen_ils.net.Gateway
import org.evergreen_ils.searchCatalog.SearchActivity
import org.evergreen_ils.system.Analytics
import org.evergreen_ils.system.Log
import org.evergreen_ils.utils.ui.BaseActivity
import org.opensrf.util.OSRFObject

class MainActivity : BaseActivity() {

    private var mUnreadMessageCount: Int? = null //unknown
    private var mUnreadMessageText: TextView? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isRestarting) return

        Log.d(TAG, object{}.javaClass.enclosingMethod?.name)

        setContentView(R.layout.activity_main)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, object{}.javaClass.enclosingMethod?.name)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        Log.d(TAG, object{}.javaClass.enclosingMethod?.name)
        loadGlobalData()
        loadUnreadMessageCount()
    }

    // Start the async load of data whose lifetime extends past that of MainActivity.
    // We don't want to cancel this data when starting a new Activity.
    private fun loadGlobalData() {
        GlobalScope.launch {
            async { EgSms.loadCarriers(Gateway.pcrud.fetchSMSCarriers()) }
        }
    }

    // Load data that is local to this Activity.
    private fun loadUnreadMessageCount() {
        async {
            if (resources.getBoolean(R.bool.ou_enable_messages)) {
                onMessagesResult(Gateway.actor.fetchUserMessages(App.getAccount()))
            }
        }
    }

    private fun onMessagesResult(result: Result<List<OSRFObject>>) {
        when (result) {
            is Result.Success ->  updateMessagesBadge(result.data)
            is Result.Error -> Unit
        }
    }

    private fun updateMessagesBadge(messages: List<OSRFObject>) {
        mUnreadMessageCount = countUnread(messages)
        updateUnreadMessagesText()
    }

    private fun countUnread(messages: List<OSRFObject>): Int {
        var count = 0
        messages.forEach {
            val readDate = it.getString("read_date")
            val deleted = it.getBoolean("deleted")
            if (readDate == null && !deleted) {
                ++count
            }
        }
        return count
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult req=$requestCode result=$resultCode")
        if (requestCode == App.REQUEST_LAUNCH_OPAC_LOGIN_REDIRECT) {
            loadUnreadMessageCount()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)

        // remove items we don't need
        if (TextUtils.isEmpty(feedbackUrl))
            menu.removeItem(R.id.action_feedback)

        // set up the messages action view, it didn't work when set in xml
        if (!resources.getBoolean(R.bool.ou_enable_messages)) {
            menu.removeItem(R.id.action_messages)
        } else {
            createMessagesActionView(menu)
        }

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val item = menu.findItem(R.id.action_switch_account)
        if (item != null)
            item.isEnabled = AccountUtils.haveMoreThanOneAccount(this)
        updateUnreadMessagesText()
        return true
    }

    private fun createMessagesActionView(menu: Menu) {
        val item = menu.findItem(R.id.action_messages)
        MenuItemCompat.setActionView(item, R.layout.badge_layout)
        val layout = MenuItemCompat.getActionView(item) as RelativeLayout
        mUnreadMessageText = layout.findViewById<View>(R.id.badge_text) as TextView
        val button = layout.findViewById<View>(R.id.badge_icon_button) as ImageButton
        button.setOnClickListener { onOptionsItemSelected(item) }
    }

    private fun updateUnreadMessagesText() {
        if (mUnreadMessageText == null)
            return
        if (isFinishing)
            return
        val count = mUnreadMessageCount
        if (count != null) {
            mUnreadMessageText?.visibility = if (count > 0) View.VISIBLE else View.GONE;
            mUnreadMessageText?.text = String.format("%d", count)
        } else {
            mUnreadMessageText?.visibility = View.GONE
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (menuItemHandler?.onItemSelected(this, id, "main_option_menu") == true)
            return true
        return if (handleMenuAction(id)) true else super.onOptionsItemSelected(item)
    }

    fun onButtonClick(v: View) {
        val id = v.id
        if (id == R.id.account_btn_check_out) {
            Analytics.logEvent("Checkouts: Open", "via", "main_button")
            startActivity(Intent(this, CheckoutsActivity::class.java))
        } else if (id == R.id.account_btn_holds) {
            Analytics.logEvent("Holds: Open", "via", "main_button")
            startActivity(Intent(this, HoldsActivity::class.java))
        } else if (id == R.id.account_btn_fines) {
            Analytics.logEvent("Fines: Open", "via", "main_button")
            startActivity(Intent(this, FinesActivity::class.java))
        } else if (id == R.id.main_my_lists_button) {
            Analytics.logEvent("Lists: Open", "via", "main_button")
            startActivity(Intent(this, BookBagActivity::class.java))
        } else if (id == R.id.main_btn_search) {
            Analytics.logEvent("Search: Open", "via", "main_button")
            startActivity(Intent(this, SearchActivity::class.java))
        } else if (id == R.id.main_barcode_button) {
            Analytics.logEvent("Barcode: Open", "via", "main_button")
            startActivity(Intent(this, BarcodeActivity::class.java))
        } else if (menuItemHandler != null) {
            menuItemHandler?.onItemSelected(this, id, "main_button")
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.getSimpleName()
    }
}
