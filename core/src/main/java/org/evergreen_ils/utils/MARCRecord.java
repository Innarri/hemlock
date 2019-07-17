//
//  Copyright (C) 2019 Kenneth H. Cox
//
//  This program is free software; you can redistribute it and/or
//  modify it under the terms of the GNU General Public License
//  as published by the Free Software Foundation; either version 2
//  of the License, or (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package org.evergreen_ils.utils;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class MARCRecord {
    public static class MARCSubfield {
        public String code;
        public String text = null;
        public MARCSubfield(String code) {
            this.code = code;
        }
    }

    public static class MARCDatafield {
        public String tag;
        public String ind1;
        public String ind2;
        public List<MARCSubfield> subfields = new ArrayList<>();
        public MARCDatafield(String tag, String ind1, String ind2) {
            this.tag = tag;
            this.ind1 = ind1;
            this.ind2 = ind2;
        }
    }

    public List<MARCDatafield> datafields = new ArrayList<>();

    public MARCRecord() {
    }

    public List<Link> getLinks() {
        ArrayList<Link> links = new ArrayList<>();
        for (MARCDatafield df: datafields) {
            if (TextUtils.equals(df.tag, "856")
                    && TextUtils.equals(df.ind1, "4")
                    && (TextUtils.equals(df.ind2, "0") || TextUtils.equals(df.ind2, "1"))) {
                String href = null;
                String text = null;
                for (MARCSubfield sf: df.subfields) {
                    if (TextUtils.equals(sf.code, "u") && href == null) href = sf.text;
                    if ((TextUtils.equals(sf.code, "3") || TextUtils.equals(sf.code, "y")) && text == null) text = sf.text;
                }
                if (href != null && text != null) {
                    links.add(new Link(href, text));
                }
            }
        }
        return links;
    }
}