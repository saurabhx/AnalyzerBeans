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
package org.eobjects.analyzer.test.full.scenarios;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import junit.framework.TestCase;

import org.eobjects.analyzer.beans.transform.TokenizerTransformer;
import org.eobjects.analyzer.beans.valuedist.ValueDistributionAnalyzer;
import org.eobjects.analyzer.beans.valuedist.ValueDistributionAnalyzerResult;
import org.eobjects.analyzer.configuration.AnalyzerBeansConfiguration;
import org.eobjects.analyzer.configuration.AnalyzerBeansConfigurationImpl;
import org.eobjects.analyzer.connection.Datastore;
import org.eobjects.analyzer.connection.DatastoreConnection;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.MutableInputColumn;
import org.eobjects.analyzer.job.AnalysisJob;
import org.eobjects.analyzer.job.builder.AnalysisJobBuilder;
import org.eobjects.analyzer.job.builder.AnalyzerJobBuilder;
import org.eobjects.analyzer.job.builder.TransformerJobBuilder;
import org.eobjects.analyzer.job.concurrent.MultiThreadedTaskRunner;
import org.eobjects.analyzer.job.concurrent.TaskRunner;
import org.eobjects.analyzer.job.runner.AnalysisResultFuture;
import org.eobjects.analyzer.job.runner.AnalysisRunner;
import org.eobjects.analyzer.job.runner.AnalysisRunnerImpl;
import org.eobjects.analyzer.result.AnalyzerResult;
import org.eobjects.analyzer.test.TestHelper;
import org.apache.metamodel.DataContext;
import org.apache.metamodel.schema.Column;
import org.apache.metamodel.schema.Table;

public class TokenizerAndValueDistributionTest extends TestCase {

    public void testScenario() throws Throwable {
        TaskRunner taskRunner = new MultiThreadedTaskRunner(5);

        AnalyzerBeansConfiguration configuration = new AnalyzerBeansConfigurationImpl().replace(taskRunner);

        AnalysisRunner runner = new AnalysisRunnerImpl(configuration);

        Datastore datastore = TestHelper.createSampleDatabaseDatastore("ds");
        DatastoreConnection con = datastore.openConnection();
        DataContext dc = con.getDataContext();

        AnalysisJobBuilder analysisJobBuilder = new AnalysisJobBuilder(configuration);
        analysisJobBuilder.setDatastoreConnection(con);

        Table table = dc.getDefaultSchema().getTableByName("EMPLOYEES");
        assertNotNull(table);

        Column jobTitleColumn = table.getColumnByName("JOBTITLE");
        assertNotNull(jobTitleColumn);

        analysisJobBuilder.addSourceColumns(jobTitleColumn);

        TransformerJobBuilder<TokenizerTransformer> transformerJobBuilder = analysisJobBuilder
                .addTransformer(TokenizerTransformer.class);
        transformerJobBuilder.addInputColumn(analysisJobBuilder.getSourceColumns().get(0));
        transformerJobBuilder.setConfiguredProperty("Number of tokens", 4);
        List<MutableInputColumn<?>> transformerOutput = transformerJobBuilder.getOutputColumns();
        assertEquals(4, transformerOutput.size());

        transformerOutput.get(0).setName("first word");
        transformerOutput.get(1).setName("second word");
        transformerOutput.get(2).setName("third words");
        transformerOutput.get(3).setName("fourth words");

        for (InputColumn<?> inputColumn : transformerOutput) {
            AnalyzerJobBuilder<ValueDistributionAnalyzer> valueDistribuitionJobBuilder = analysisJobBuilder
                    .addAnalyzer(ValueDistributionAnalyzer.class);
            valueDistribuitionJobBuilder.addInputColumn(inputColumn);
            valueDistribuitionJobBuilder.setConfiguredProperty("Record unique values", true);
            valueDistribuitionJobBuilder.setConfiguredProperty("Top n most frequent values", null);
            valueDistribuitionJobBuilder.setConfiguredProperty("Bottom n most frequent values", null);
        }

        AnalysisJob analysisJob = analysisJobBuilder.toAnalysisJob();
        analysisJobBuilder.close();

        AnalysisResultFuture resultFuture = runner.run(analysisJob);

        assertFalse(resultFuture.isDone());

        List<AnalyzerResult> results = resultFuture.getResults();

        assertTrue(resultFuture.isDone());

        if (!resultFuture.isSuccessful()) {
            List<Throwable> errors = resultFuture.getErrors();
            throw errors.get(0);
        }

        // expect 1 result for each token
        assertEquals(4, results.size());

        for (AnalyzerResult analyzerResult : results) {
            ValueDistributionAnalyzerResult result = (ValueDistributionAnalyzerResult) analyzerResult;
            Collection<String> uniqueValues = new TreeSet<String>(result.getUniqueValues());
            if ("first word".equals(result.getName())) {
                assertEquals("[[Sales->19], [VP->2], [<unique>->2]]", result.getValueCounts().toString());
                assertEquals(0, result.getNullCount());
                assertEquals(2, result.getUniqueCount().intValue());
            } else if ("second word".equals(result.getName())) {
                assertEquals("[[Rep->17], [Manager->3], [<unique>->2], [<null>->1]]", result.getValueCounts().toString());
                assertEquals(1, result.getNullCount());
                assertEquals(2, result.getUniqueCount().intValue());
            } else if ("third words".equals(result.getName())) {
                assertEquals("[[<null>->20], [<unique>->3]]", result.getValueCounts().toString());
                assertEquals(20, result.getNullCount());
                assertEquals(3, result.getUniqueCount().intValue());
                assertEquals("[(EMEA), (JAPAN,, (NA)]", uniqueValues.toString());
            } else if ("fourth words".equals(result.getName())) {
                assertEquals("[[<null>->22], [<unique>->1]]", result.getValueCounts().toString());
                assertEquals(22, result.getNullCount());
                assertEquals(1, result.getUniqueCount().intValue());
                assertEquals("[APAC)]", uniqueValues.toString());
            } else {
                fail("Unexpected columnName: " + result.getName());
            }
        }

        con.close();
        taskRunner.shutdown();
    }
}
