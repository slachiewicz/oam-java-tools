/*
    Open Aviation Map
    Copyright (C) 2012-2013 Ákos Maróy

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.openaviationmap.model;

/**
 * Enumeration for elevation references: mean sea level, surface, etc.
 */
public enum ElevationReference {
    /**
     * Elevation above Mean Sea Level - MSL.
     */
    MSL,

    /**
     * Elevation above the surface - SFC, aka Above Ground Level - AGL.
     */
    SFC,

    /**
     * Elevation according to the standard atmosphere. Used for flight levels.
     */
    STD;

    /**
     * Return an elevation reference value based on a string description.
     *
     * @param str the string representation of the elevation reference.
     * @return the elevation reference corresponding the string representation.
     */
    public static ElevationReference fromString(String str) {
        String s = str.trim().toLowerCase();

        if ("amsl".equals(s) || "msl".equals(s)) {
            return MSL;
        } else if ("agl".equals(s) || "sfc".equals(s)) {
            return SFC;
        } else if ("std".equals(s)) {
            return STD;
        }

        throw new IllegalArgumentException();
    }
}
