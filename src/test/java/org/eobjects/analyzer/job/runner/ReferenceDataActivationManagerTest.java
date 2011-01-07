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
package org.eobjects.analyzer.job.runner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import junit.framework.TestCase;

import org.eobjects.analyzer.beans.BooleanAnalyzer;
import org.eobjects.analyzer.beans.standardize.EmailStandardizerTransformer;
import org.eobjects.analyzer.beans.transform.DictionaryMatcherTransformer;
import org.eobjects.analyzer.configuration.AnalyzerBeansConfigurationImpl;
import org.eobjects.analyzer.connection.DatastoreCatalog;
import org.eobjects.analyzer.connection.DatastoreCatalogImpl;
import org.eobjects.analyzer.connection.JdbcDatastore;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.MutableInputColumn;
import org.eobjects.analyzer.descriptors.DescriptorProvider;
import org.eobjects.analyzer.descriptors.LazyDescriptorProvider;
import org.eobjects.analyzer.job.AnalysisJob;
import org.eobjects.analyzer.job.builder.AnalysisJobBuilder;
import org.eobjects.analyzer.job.builder.TransformerJobBuilder;
import org.eobjects.analyzer.job.concurrent.SingleThreadedTaskRunner;
import org.eobjects.analyzer.job.concurrent.TaskRunner;
import org.eobjects.analyzer.reference.Dictionary;
import org.eobjects.analyzer.reference.ReferenceDataCatalog;
import org.eobjects.analyzer.reference.ReferenceDataCatalogImpl;
import org.eobjects.analyzer.reference.StringPattern;
import org.eobjects.analyzer.reference.SynonymCatalog;
import org.eobjects.analyzer.storage.StorageProvider;
import org.eobjects.analyzer.test.TestHelper;

public class ReferenceDataActivationManagerTest extends TestCase {

	public void testInvocationThroughAnalysisRunner() throws Throwable {
		MockMonitoredDictionary dict1 = new MockMonitoredDictionary();
		MockMonitoredDictionary dict2 = new MockMonitoredDictionary();
		MockMonitoredDictionary dict3 = new MockMonitoredDictionary();
		assertEquals(0, dict1.getInitCount());
		assertEquals(0, dict1.getCloseCount());
		assertEquals(0, dict2.getInitCount());
		assertEquals(0, dict2.getCloseCount());
		assertEquals(0, dict3.getInitCount());
		assertEquals(0, dict3.getCloseCount());

		Collection<Dictionary> dictionaries = new ArrayList<Dictionary>();
		dictionaries.add(dict1);
		dictionaries.add(dict2);
		dictionaries.add(dict3);
		ReferenceDataCatalog referenceDataCatalog = new ReferenceDataCatalogImpl(dictionaries,
				new ArrayList<SynonymCatalog>(), new ArrayList<StringPattern>());

		DescriptorProvider descriptorProvider = new LazyDescriptorProvider();
		TaskRunner taskRunner = new SingleThreadedTaskRunner();
		JdbcDatastore datastore = TestHelper.createSampleDatabaseDatastore("db");
		DatastoreCatalog datastoreCatalog = new DatastoreCatalogImpl(datastore);
		StorageProvider storageProvider = TestHelper.createStorageProvider();

		AnalyzerBeansConfigurationImpl configuration = new AnalyzerBeansConfigurationImpl(datastoreCatalog,
				referenceDataCatalog, descriptorProvider, taskRunner, storageProvider);

		AnalysisRunner runner = new AnalysisRunnerImpl(configuration);

		// build a job
		AnalysisJobBuilder ajb = new AnalysisJobBuilder(configuration);
		ajb.setDatastore(datastore);
		ajb.addSourceColumn(datastore.getDataContextProvider().getSchemaNavigator()
				.convertToColumn("PUBLIC.EMPLOYEES.EMAIL"));

		InputColumn<?> emailColumn = ajb.getSourceColumnByName("email");
		assertNotNull(emailColumn);

		MutableInputColumn<?> usernameColumn = ajb.addTransformer(EmailStandardizerTransformer.class)
				.addInputColumn(emailColumn).getOutputColumnByName("Username");
		assertNotNull(usernameColumn);

		TransformerJobBuilder<DictionaryMatcherTransformer> tjb = ajb.addTransformer(DictionaryMatcherTransformer.class);
		DictionaryMatcherTransformer transformer = tjb.getConfigurableBean();
		transformer.setDictionaries(new Dictionary[] { dict1, dict2, dict3 });
		tjb.addInputColumn(usernameColumn);
		List<MutableInputColumn<?>> outputColumns = tjb.getOutputColumns();

		ajb.addRowProcessingAnalyzer(BooleanAnalyzer.class).addInputColumns(outputColumns);
		AnalysisJob job = ajb.toAnalysisJob();

		AnalysisResultFuture result = runner.run(job);

		if (!result.isSuccessful()) {
			throw result.getErrors().get(0);
		}

		assertEquals(1, dict1.getInitCount());
		assertEquals(1, dict1.getCloseCount());
		assertEquals(1, dict2.getInitCount());
		assertEquals(1, dict2.getCloseCount());
		assertEquals(1, dict3.getInitCount());
		assertEquals(1, dict3.getCloseCount());
		
		result = runner.run(job);

		if (!result.isSuccessful()) {
			throw result.getErrors().get(0);
		}

		assertEquals(2, dict1.getInitCount());
		assertEquals(2, dict1.getCloseCount());
		assertEquals(2, dict2.getInitCount());
		assertEquals(2, dict2.getCloseCount());
		assertEquals(2, dict3.getInitCount());
		assertEquals(2, dict3.getCloseCount());
	}
}