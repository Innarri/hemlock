/*
 * Copyright (C) 2012 Evergreen Open-ILS
 * @author Daniel-Octavian Rizea
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * or the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be usefull,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 * 
 */

package org.evergreen_ils.accountAccess.bookbags;

import org.opensrf.util.OSRFObject;

import java.io.Serializable;
import java.util.ArrayList;

public class BookBag implements Serializable {

    public int id;

    public String name = null;

    public String description = null;

    public Boolean shared = null;

    public ArrayList<BookBagItem> items = null;

    public BookBag(OSRFObject object) {
        this.id = object.getInt("id");
        this.name = object.getString("name");
        this.description = object.getString("description");
        this.items = new ArrayList<>();
        this.shared = object.getBoolean("pub");
    }

}
