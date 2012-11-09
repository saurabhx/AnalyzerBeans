/**
 * eobjects.org AnalyzerBeans
 * Copyright (C) 2010 eobjects.org
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.eobjects.analyzer.beans.stringpattern;

import org.eobjects.analyzer.util.LabelUtils;

/**
 * Token which represents a null
 */
public class NullToken implements Token {
    
    public static final Token INSTANCE = new NullToken();

    @Override
    public TokenType getType() {
        return TokenType.PREDEFINED;
    }

    @Override
    public String getString() {
        return LabelUtils.NULL_LABEL;
    }

    @Override
    public char charAt(int index) {
        throw new UnsupportedOperationException("Null does not have any chars");
    }

    @Override
    public int length() {
        return 0;
    }

}