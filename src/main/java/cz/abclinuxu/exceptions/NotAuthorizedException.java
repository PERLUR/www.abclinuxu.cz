/*
 *  Copyright (C) 2005 Leos Literak
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public
 *  License as published by the Free Software Foundation; either
 *  version 2 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; see the file COPYING.  If not, write to
 *  the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 *  Boston, MA 02111-1307, USA.
 */
package cz.abclinuxu.exceptions;

import cz.abclinuxu.AbcException;

/**
 * This exception is thrown, when user tries to perform
 * action, for which his rights are not sufficient.
 */
public class NotAuthorizedException extends AbcException {

    /**
     * This exception is thrown, when user tries to perform
     * action, where his rights are not sufficient.
     */
    public NotAuthorizedException(String desc) {
        super(desc);
    }

    /**
     * This exception is thrown, when user tries to perform
     * action, where his rights are not sufficient.
     */
    public NotAuthorizedException(String desc, Exception e) {
        super(desc, e);
    }
}