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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import org.eobjects.analyzer.beans.api.Configured;
import org.eobjects.analyzer.beans.api.Filter;
import org.eobjects.analyzer.beans.api.FilterBean;
import org.eobjects.analyzer.configuration.AnalyzerBeansConfiguration;
import org.eobjects.analyzer.configuration.AnalyzerBeansConfigurationImpl;
import org.eobjects.analyzer.connection.DatastoreCatalog;
import org.eobjects.analyzer.connection.DatastoreCatalogImpl;
import org.eobjects.analyzer.connection.PojoDatastore;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.InputRow;
import org.eobjects.analyzer.data.MutableInputColumn;
import org.eobjects.analyzer.job.AnalysisJob;
import org.eobjects.analyzer.job.Outcome;
import org.eobjects.analyzer.job.builder.AnalysisJobBuilder;
import org.eobjects.analyzer.job.builder.AnalyzerJobBuilder;
import org.eobjects.analyzer.job.builder.FilterJobBuilder;
import org.eobjects.analyzer.job.builder.TransformerJobBuilder;
import org.eobjects.analyzer.job.runner.AnalysisResultFuture;
import org.eobjects.analyzer.job.runner.AnalysisRunnerImpl;
import org.eobjects.analyzer.result.ListResult;
import org.eobjects.analyzer.test.MockAnalyzer;
import org.eobjects.analyzer.test.MockTransformer;
import org.apache.metamodel.pojo.ArrayTableDataProvider;
import org.apache.metamodel.util.SimpleTableDef;

public class FilterRequirementMergingTest extends TestCase {

    private AnalyzerBeansConfiguration configuration;
    private PojoDatastore datastore;
    private AnalysisJobBuilder jobBuilder;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        SimpleTableDef tableDef = new SimpleTableDef("table", new String[] { "col1" });
        List<Object[]> rowData = new ArrayList<Object[]>();
        rowData.add(new Object[] { "foo" });
        rowData.add(new Object[] { "bar" });
        rowData.add(new Object[] { "baz" });
        rowData.add(new Object[] { "hello" });
        rowData.add(new Object[] { "world" });
        datastore = new PojoDatastore("ds", "sch", new ArrayTableDataProvider(tableDef, rowData));

        DatastoreCatalog datastoreCatalog = new DatastoreCatalogImpl(datastore);
        configuration = new AnalyzerBeansConfigurationImpl().replace(datastoreCatalog);

        jobBuilder = new AnalysisJobBuilder(configuration);
        jobBuilder.setDatastore(datastore);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        jobBuilder.close();
    }

    /**
     * Tests that if two transformations that have different (opposing)
     * requirements both feed into the same component (e.g. an Analyzer), then
     * both requirement states will be accepted by that component.
     */
    public void testMergeFilterRequirementsWhenAnalyzerConsumesInputColumnsWithMultipleRequirements() throws Throwable {
        jobBuilder.addSourceColumns("col1");
        InputColumn<?> sourceColumn = jobBuilder.getSourceColumnByName("col1");
        FilterJobBuilder<EvenOddFilter, EvenOddFilter.Category> filter = jobBuilder.addFilter(EvenOddFilter.class)
                .addInputColumn(sourceColumn);

        Outcome req1 = filter.getOutcome(EvenOddFilter.Category.EVEN);
        Outcome req2 = filter.getOutcome(EvenOddFilter.Category.ODD);

        TransformerJobBuilder<MockTransformer> transformer1 = jobBuilder.addTransformer(MockTransformer.class)
                .addInputColumn(sourceColumn);
        transformer1.setRequirement(req1);
        MutableInputColumn<?> outputColumn1 = transformer1.getOutputColumns().get(0);
        outputColumn1.setName("outputColumn1");

        TransformerJobBuilder<MockTransformer> transformer2 = jobBuilder.addTransformer(MockTransformer.class)
                .addInputColumn(sourceColumn);
        transformer2.setRequirement(req2);
        MutableInputColumn<?> outputColumn2 = transformer2.getOutputColumns().get(0);
        outputColumn2.setName("outputColumn2");

        AnalyzerJobBuilder<MockAnalyzer> analyzer = jobBuilder.addAnalyzer(MockAnalyzer.class);
        // add outputcolumn 1+2 - they have opposite requirements
        analyzer.addInputColumns(sourceColumn, outputColumn1, outputColumn2);

        AnalysisJob job = jobBuilder.toAnalysisJob();

        AnalysisResultFuture resultFuture = new AnalysisRunnerImpl(configuration).run(job);

        resultFuture.await();

        if (resultFuture.isErrornous()) {
            throw resultFuture.getErrors().get(0);
        }

        @SuppressWarnings("unchecked")
        ListResult<InputRow> listResult = (ListResult<InputRow>) resultFuture.getResults().get(0);
        List<InputRow> list = listResult.getValues();

        assertFalse("List is empty - this indicates that no records passed through the 'multiple requirements' rule",
                list.isEmpty());
        assertEquals("[foo, null, mocked: foo]", list.get(0).getValues(sourceColumn, outputColumn1, outputColumn2)
                .toString());
        assertEquals("[bar, mocked: bar, null]", list.get(1).getValues(sourceColumn, outputColumn1, outputColumn2)
                .toString());
        assertEquals("[baz, null, mocked: baz]", list.get(2).getValues(sourceColumn, outputColumn1, outputColumn2)
                .toString());
        assertEquals("[hello, mocked: hello, null]", list.get(3).getValues(sourceColumn, outputColumn1, outputColumn2)
                .toString());
        assertEquals("[world, null, mocked: world]", list.get(4).getValues(sourceColumn, outputColumn1, outputColumn2)
                .toString());
        assertEquals(5, list.size());
    }

    /**
     * Tests that if a single transformations that has a requirements feed into
     * the another component (e.g. an Analyzer), then that component will
     * respect the transformation's requirement.
     */
    public void testDontMergeFilterRequirementWhenAnalyzerConsumesInputColumnsWithSingleRequirement() throws Throwable {
        jobBuilder.addSourceColumns("col1");
        InputColumn<?> sourceColumn = jobBuilder.getSourceColumnByName("col1");
        FilterJobBuilder<EvenOddFilter, EvenOddFilter.Category> filter = jobBuilder.addFilter(EvenOddFilter.class)
                .addInputColumn(sourceColumn);

        Outcome req1 = filter.getOutcome(EvenOddFilter.Category.EVEN);
        Outcome req2 = filter.getOutcome(EvenOddFilter.Category.ODD);

        TransformerJobBuilder<MockTransformer> transformer1 = jobBuilder.addTransformer(MockTransformer.class)
                .addInputColumn(sourceColumn);
        transformer1.setRequirement(req1);
        MutableInputColumn<?> outputColumn1 = transformer1.getOutputColumns().get(0);
        outputColumn1.setName("outputColumn1");

        TransformerJobBuilder<MockTransformer> transformer2 = jobBuilder.addTransformer(MockTransformer.class)
                .addInputColumn(sourceColumn);
        transformer2.setRequirement(req2);
        MutableInputColumn<?> outputColumn2 = transformer2.getOutputColumns().get(0);
        outputColumn2.setName("outputColumn2");

        AnalyzerJobBuilder<MockAnalyzer> analyzer = jobBuilder.addAnalyzer(MockAnalyzer.class);

        // add only outputcolumn 1 - it has a single requirement
        analyzer.addInputColumns(sourceColumn, outputColumn1);

        AnalysisJob job = jobBuilder.toAnalysisJob();

        AnalysisResultFuture resultFuture = new AnalysisRunnerImpl(configuration).run(job);

        resultFuture.await();

        if (resultFuture.isErrornous()) {
            throw resultFuture.getErrors().get(0);
        }

        @SuppressWarnings("unchecked")
        ListResult<InputRow> listResult = (ListResult<InputRow>) resultFuture.getResults().get(0);
        List<InputRow> list = listResult.getValues();

        assertFalse("List is empty - this indicates that no records passed through the 'multiple requirements' rule",
                list.isEmpty());
        assertEquals("[bar, mocked: bar, null]", list.get(0).getValues(sourceColumn, outputColumn1, outputColumn2)
                .toString());
        assertEquals("[hello, mocked: hello, null]", list.get(1).getValues(sourceColumn, outputColumn1, outputColumn2)
                .toString());
        assertEquals(2, list.size());
    }

    @FilterBean("Even/odd record filter")
    public static class EvenOddFilter implements Filter<EvenOddFilter.Category> {

        public static enum Category {
            EVEN, ODD
        }

        private final AtomicInteger counter = new AtomicInteger();

        @Configured
        InputColumn<String> column;

        @Override
        public Category categorize(InputRow inputRow) {
            int v = counter.incrementAndGet();
            if (v % 2 == 0) {
                return Category.EVEN;
            }
            return Category.ODD;
        }

    }
}
