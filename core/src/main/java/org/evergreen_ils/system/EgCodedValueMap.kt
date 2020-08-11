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
package org.evergreen_ils.system

import org.evergreen_ils.Api
import org.evergreen_ils.android.Analytics
import org.evergreen_ils.android.Log
import org.evergreen_ils.utils.TextUtils
import org.opensrf.ShouldNotHappenException
import org.opensrf.util.OSRFObject
import java.util.*

object EgCodedValueMap {
    private val TAG = EgCodedValueMap::class.java.simpleName

    const val SEARCH_FORMAT = "search_format"
    const val ICON_FORMAT = "icon_format"
    const val ALL_SEARCH_FORMATS = "All Formats"

    internal data class CodedValue(val code: String, val value: String, val opac_visible: Boolean)

    private var searchFormats = ArrayList<CodedValue>()
    private var iconFormats = ArrayList<CodedValue>()

    @JvmStatic
    fun loadCodedValueMaps(objects: List<OSRFObject>) {
        searchFormats = ArrayList()
        iconFormats = ArrayList()
        for (obj in objects) {
            val ctype = obj.getString("ctype", "")
            val code = obj.getString("code") ?: continue
            val opac_visible = Api.parseBoolean(obj["opac_visible"])
            val search_label = obj.getString("search_label") ?: ""
            val value = obj.getString("value") ?: ""
            val cv = CodedValue(code, if (search_label.isNotBlank()) search_label else value, opac_visible)
            Log.d(TAG, "ccvm ctype:" + ctype + " code:" + code + " label:" + cv.value)
            if (ctype == SEARCH_FORMAT) {
                searchFormats.add(cv)
            } else if (ctype == ICON_FORMAT) {
                iconFormats.add(cv)
            }
        }
    }

    fun getValueFromCode(ctype: String, code: String): String? {
        val values: ArrayList<CodedValue>
        values = if (ctype == SEARCH_FORMAT) {
            searchFormats
        } else if (ctype == ICON_FORMAT) {
            iconFormats
        } else {
            return null
        }
        for (cv in values) {
            if (TextUtils.equals(code, cv.code)) {
                return cv.value
            }
        }
        Analytics.logException(ShouldNotHappenException("Unknown ccvm code: $code"))
        return null
    }

    fun getCodeFromValue(ctype: String, value: String): String? {
        val values: ArrayList<CodedValue>
        values = if (ctype == SEARCH_FORMAT) {
            searchFormats
        } else if (ctype == ICON_FORMAT) {
            iconFormats
        } else {
            return null
        }
        for (cv in values) {
            if (TextUtils.equals(value, cv.value)) {
                return cv.code
            }
        }
        Analytics.logException(ShouldNotHappenException("Unknown ccvm value: $value"))
        return null
    }

    @JvmStatic
    fun iconFormatLabel(code: String): String? {
        return getValueFromCode(ICON_FORMAT, code)
    }

    @JvmStatic
    fun searchFormatLabel(code: String): String? {
        return getValueFromCode(SEARCH_FORMAT, code)
    }

    @JvmStatic
    fun searchFormatCode(label: String?): String? {
        return if (label.isNullOrBlank() || label == ALL_SEARCH_FORMATS) "" else getCodeFromValue(SEARCH_FORMAT, label)
    }

    /// list of labels e.g. "All Formats", "All Books", ...
    @JvmStatic
    val searchFormatSpinnerLabels: List<String>
        get() {
            val labels = ArrayList<String>()
            for (cv in searchFormats) {
                if (cv.opac_visible) {
                    labels.add(cv.value)
                }
            }
            labels.sort()
            labels.add(0, ALL_SEARCH_FORMATS)
            return labels
        }
}
