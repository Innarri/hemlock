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

package org.evergreen_ils.utils.ui

import android.app.Activity
import android.app.AlertDialog

fun Activity.showAlert(ex: Exception) {
    showAlert(ex.localizedMessage)
}

fun Activity.showAlert(errorMessage: String) {
    if (isFinishing) return
    val builder = AlertDialog.Builder(this)
    builder.setTitle("Error")
            .setMessage(errorMessage)
            .setPositiveButton(android.R.string.ok) { dialog, which ->
                //alertDialog = null
                //alertMessage = null
            }
    val alertDialog = builder.create()
    alertDialog.show()
}
