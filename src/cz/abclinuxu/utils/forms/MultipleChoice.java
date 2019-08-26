/*
 *  Copyright (C) 2008 Karel Piwko
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

package cz.abclinuxu.utils.forms;

import java.util.AbstractCollection;
import java.util.Collection;

/**
 * Abstract template for multiple form values. Implements AbstractColletion
 * to allow Freemarker to iterate throught its values.
 * @param <V> Values stored, must implement Selectable interface
 * @see Selectable
 * @author kapy
 */
public abstract class MultipleChoice<V extends Selectable> extends AbstractCollection {
    

    /** Size of map including selected items only */
    protected int selected = 0;
    /** Consired emptyness the same as all items were selected */
    protected boolean noneIsAll;  
    
   
    /**
     * Returns collection filled by selected items
     * @return Collection of selected items
     */
    public abstract Collection selectedSet(); 
    
    /**
     * Returns collection af all values stored in collection,
     * including not selected ones
     * @return All values available
     */
    public abstract Collection<V> values();
    
    /**
     * Returns flag of collection emptyness
     * @return <code>true</code> if no items inserted is selected, 
     * <code>false</code> otherwise
     */
    public boolean isNothingSelected() {
        return selected==0;
    }
    
    /**
     * Returns flag which means that every item stored is selected
     * @return <code>true</code> if all items vere selected, 
     * <code>false</code> otherwise
     */
    public abstract boolean isEverythingSelected();
    
}
