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
package org.eobjects.analyzer.beans.script;

import org.eobjects.analyzer.beans.filter.ValidationCategory;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.MockInputColumn;
import org.eobjects.analyzer.data.MockInputRow;

import junit.framework.TestCase;

public class JavaScriptFilterTest extends TestCase {

	public void testNotNullFilteringString() throws Exception {
		JavaScriptFilter filter = new JavaScriptFilter();
		filter.setSourceCode("my_col != null;");
		InputColumn<String> myCol = new MockInputColumn<String>("my_col", String.class);
		filter.setColumns(new InputColumn[] { myCol });
		filter.init();

		assertEquals(ValidationCategory.VALID, filter.categorize(new MockInputRow().put(myCol, "hi")));
		assertEquals(ValidationCategory.VALID, filter.categorize(new MockInputRow().put(myCol, "")));
		assertEquals(ValidationCategory.VALID, filter.categorize(new MockInputRow().put(myCol, " ")));
		assertEquals(ValidationCategory.INVALID, filter.categorize(new MockInputRow().put(myCol, null)));
	}

	public void testNotNullFilteringNumber() throws Exception {
		JavaScriptFilter filter = new JavaScriptFilter();
		filter.setSourceCode("my_col != null;");
		InputColumn<Number> myCol = new MockInputColumn<Number>("my_col", Number.class);
		filter.setColumns(new InputColumn[] { myCol });
		filter.init();

		assertEquals(ValidationCategory.VALID, filter.categorize(new MockInputRow().put(myCol, 1)));
		assertEquals(ValidationCategory.VALID, filter.categorize(new MockInputRow().put(myCol, -1)));
		assertEquals(ValidationCategory.VALID, filter.categorize(new MockInputRow().put(myCol, 0)));
		assertEquals(ValidationCategory.INVALID, filter.categorize(new MockInputRow().put(myCol, null)));
	}
}
