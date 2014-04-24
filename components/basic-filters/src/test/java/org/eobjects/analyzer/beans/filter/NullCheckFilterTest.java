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
package org.eobjects.analyzer.beans.filter;

import junit.framework.TestCase;

import org.eobjects.analyzer.connection.Datastore;
import org.eobjects.analyzer.connection.DatastoreConnection;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.MetaModelInputColumn;
import org.eobjects.analyzer.data.MockInputColumn;
import org.eobjects.analyzer.data.MockInputRow;
import org.eobjects.analyzer.descriptors.Descriptors;
import org.eobjects.analyzer.descriptors.FilterBeanDescriptor;
import org.eobjects.analyzer.descriptors.SimpleDescriptorProvider;
import org.eobjects.analyzer.test.TestHelper;
import org.eobjects.analyzer.util.SchemaNavigator;
import org.apache.metamodel.query.Query;

public class NullCheckFilterTest extends TestCase {

	public void testAliases() throws Exception {
		FilterBeanDescriptor<?, ?> desc1 = Descriptors.ofFilter(NullCheckFilter.class);

		SimpleDescriptorProvider descriptorProvider = new SimpleDescriptorProvider();
		descriptorProvider.addFilterBeanDescriptor(desc1);

		FilterBeanDescriptor<?, ?> desc2 = descriptorProvider.getFilterBeanDescriptorByDisplayName("Not null");
		FilterBeanDescriptor<?, ?> desc3 = descriptorProvider.getFilterBeanDescriptorByDisplayName("Null check");

		assertSame(desc1, desc2);
		assertSame(desc1, desc3);

		Enum<?> notNullOutcome1 = desc1.getOutcomeCategoryByName("VALID");
		Enum<?> notNullOutcome2 = desc1.getOutcomeCategoryByName("NOT_NULL");
		assertSame(notNullOutcome1, notNullOutcome2);

		Enum<?> nullOutcome1 = desc1.getOutcomeCategoryByName("INVALID");
		Enum<?> nullOutcome2 = desc1.getOutcomeCategoryByName("NULL");
		assertSame(nullOutcome1, nullOutcome2);
	}

	public void testCategorize() throws Exception {
		InputColumn<Integer> col1 = new MockInputColumn<Integer>("col1", Integer.class);
		InputColumn<Boolean> col2 = new MockInputColumn<Boolean>("col2", Boolean.class);
		InputColumn<String> col3 = new MockInputColumn<String>("col3", String.class);
		InputColumn<?>[] columns = new InputColumn[] { col1, col2, col3 };

		NullCheckFilter filter = new NullCheckFilter(columns, true);
		assertEquals(NullCheckFilter.NullCheckCategory.NOT_NULL,
				filter.categorize(new MockInputRow().put(col1, 1).put(col2, true).put(col3, "foo")));

		assertEquals(NullCheckFilter.NullCheckCategory.NULL,
				filter.categorize(new MockInputRow().put(col1, 1).put(col2, null).put(col3, "foo")));

		assertEquals(NullCheckFilter.NullCheckCategory.NULL,
				filter.categorize(new MockInputRow().put(col1, 1).put(col2, true).put(col3, "")));

		assertEquals(NullCheckFilter.NullCheckCategory.NULL,
				filter.categorize(new MockInputRow().put(col1, 1).put(col2, true).put(col3, null)));

		assertEquals(NullCheckFilter.NullCheckCategory.NULL,
				filter.categorize(new MockInputRow().put(col1, null).put(col2, null).put(col3, null)));
	}

	public void testDescriptor() throws Exception {
		FilterBeanDescriptor<NullCheckFilter, NullCheckFilter.NullCheckCategory> desc = Descriptors
				.ofFilter(NullCheckFilter.class);
		Class<NullCheckFilter.NullCheckCategory> categoryEnum = desc.getOutcomeCategoryEnum();
		assertEquals(NullCheckFilter.NullCheckCategory.class, categoryEnum);
	}

	public void testOptimizeQuery() throws Exception {
		Datastore datastore = TestHelper.createSampleDatabaseDatastore("mydb");
		DatastoreConnection con = datastore.openConnection();
		SchemaNavigator nav = con.getSchemaNavigator();

		MetaModelInputColumn col1 = new MetaModelInputColumn(nav.convertToColumn("EMPLOYEES.EMAIL"));
		MetaModelInputColumn col2 = new MetaModelInputColumn(nav.convertToColumn("EMPLOYEES.EMPLOYEENUMBER"));
		InputColumn<?>[] columns = new InputColumn[] { col1, col2 };

		NullCheckFilter filter = new NullCheckFilter(columns, true);

		Query baseQuery = con.getDataContext().query().from("EMPLOYEES").select("EMAIL").and("EMPLOYEENUMBER").toQuery();
		Query optimizedQuery = filter.optimizeQuery(baseQuery.clone(), NullCheckFilter.NullCheckCategory.NOT_NULL);

		assertEquals("SELECT \"EMPLOYEES\".\"EMAIL\", \"EMPLOYEES\".\"EMPLOYEENUMBER\" FROM "
				+ "PUBLIC.\"EMPLOYEES\" WHERE \"EMPLOYEES\".\"EMAIL\" IS NOT NULL AND \"EMPLOYEES\".\"EMAIL\" <> '' AND "
				+ "\"EMPLOYEES\".\"EMPLOYEENUMBER\" IS NOT NULL", optimizedQuery.toSql());

		optimizedQuery = filter.optimizeQuery(baseQuery.clone(), NullCheckFilter.NullCheckCategory.NULL);

		assertEquals("SELECT \"EMPLOYEES\".\"EMAIL\", \"EMPLOYEES\".\"EMPLOYEENUMBER\" FROM "
				+ "PUBLIC.\"EMPLOYEES\" WHERE (\"EMPLOYEES\".\"EMAIL\" IS NULL OR \"EMPLOYEES\".\"EMAIL\" = '' OR "
				+ "\"EMPLOYEES\".\"EMPLOYEENUMBER\" IS NULL)", optimizedQuery.toSql());

		con.close();
	}
}
