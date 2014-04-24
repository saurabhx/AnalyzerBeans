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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.TestCase;

import org.eobjects.analyzer.beans.api.AnalyzerBean;
import org.eobjects.analyzer.beans.api.Close;
import org.eobjects.analyzer.beans.api.Configured;
import org.eobjects.analyzer.beans.api.TransformerBean;
import org.eobjects.analyzer.configuration.AnalyzerBeansConfiguration;
import org.eobjects.analyzer.configuration.AnalyzerBeansConfigurationImpl;
import org.eobjects.analyzer.connection.CsvDatastore;
import org.eobjects.analyzer.connection.Datastore;
import org.eobjects.analyzer.data.InputRow;
import org.eobjects.analyzer.data.MutableInputColumn;
import org.eobjects.analyzer.job.AnalysisJob;
import org.eobjects.analyzer.job.builder.AnalysisJobBuilder;
import org.eobjects.analyzer.job.builder.AnalyzerJobBuilder;
import org.eobjects.analyzer.job.builder.TransformerJobBuilder;
import org.eobjects.analyzer.result.ListResult;
import org.eobjects.analyzer.test.MockAnalyzer;
import org.eobjects.analyzer.test.MockTransformer;

public class AnalysisRunnerImplTest extends TestCase {

    private static final AtomicBoolean MY_BOOL1 = new AtomicBoolean(false);
    private static final AtomicBoolean MY_BOOL2 = new AtomicBoolean(false);
    private static final AtomicBoolean MY_BOOL3 = new AtomicBoolean(false);
    private AnalyzerBeansConfiguration configuration = new AnalyzerBeansConfigurationImpl();
    private AnalysisRunner runner = new AnalysisRunnerImpl(configuration);
    private Datastore datastore = new CsvDatastore("ds", "src/test/resources/employees.csv");

    public void testCloseMethodOnFailure() throws Exception {

        final AnalysisJobBuilder jobBuilder = new AnalysisJobBuilder(configuration);
        jobBuilder.setDatastore(datastore);
        jobBuilder.addSourceColumns("name");

        final TransformerJobBuilder<TestTransformer1> transformer1 = jobBuilder.addTransformer(TestTransformer1.class);
        transformer1.addInputColumn(jobBuilder.getSourceColumnByName("name"));
        final List<MutableInputColumn<?>> outputColumns1 = transformer1.getOutputColumns();

        final TransformerJobBuilder<TestTransformer2> transformer2 = jobBuilder.addTransformer(TestTransformer2.class);
        transformer2.addInputColumn(jobBuilder.getSourceColumnByName("name"));
        final List<MutableInputColumn<?>> outputColumns2 = transformer2.getOutputColumns();

        final TransformerJobBuilder<TestTransformer3> transformer3 = jobBuilder.addTransformer(TestTransformer3.class);
        transformer3.addInputColumn(jobBuilder.getSourceColumnByName("name"));
        final List<MutableInputColumn<?>> outputColumns3 = transformer3.getOutputColumns();

        final AnalyzerJobBuilder<TestAnalyzer> analyzer = jobBuilder.addAnalyzer(TestAnalyzer.class);
        analyzer.addInputColumns(outputColumns1);
        analyzer.addInputColumns(outputColumns2);
        analyzer.addInputColumns(outputColumns3);

        AnalysisJob analysisJob;

        // run a succesful job to show the effect on MY_BOOL
        MY_BOOL1.set(false);
        MY_BOOL2.set(false);
        MY_BOOL3.set(false);
        analysisJob = jobBuilder.toAnalysisJob();
        AnalysisResultFuture resultFuture = runner.run(analysisJob);
        resultFuture.await();
        assertTrue(resultFuture.isSuccessful());
        assertTrue(MY_BOOL1.get());
        assertFalse(MY_BOOL2.get());
        assertTrue(MY_BOOL3.get());

        // modify the job to make it crash
        analyzer.setConfiguredProperty("Produce an error", true);
        analysisJob = jobBuilder.toAnalysisJob();

        // run again but this time produce an error
        MY_BOOL1.set(false);
        MY_BOOL2.set(false);
        MY_BOOL3.set(false);
        resultFuture = runner.run(analysisJob);
        resultFuture.await();
        assertFalse(resultFuture.isSuccessful());
        assertEquals("produceAnError=true", resultFuture.getErrors().get(0).getMessage());
        assertFalse(MY_BOOL1.get());
        assertTrue(MY_BOOL2.get());
        assertTrue(MY_BOOL3.get());

        // Error on get result
        analyzer.setConfiguredProperty("Produce an error", false);
        analyzer.setConfiguredProperty("Produce an error on get result", true);
        analysisJob = jobBuilder.toAnalysisJob();

        // run again but this time produce an error
        MY_BOOL1.set(false);
        MY_BOOL2.set(false);
        MY_BOOL3.set(false);
        resultFuture = runner.run(analysisJob);
        resultFuture.await();
        assertFalse(resultFuture.isSuccessful());
        assertEquals("produceAnErrorOnGetResult=true", resultFuture.getErrors().get(0).getMessage());
        assertFalse(MY_BOOL1.get());
        assertTrue(MY_BOOL2.get());
        assertTrue(MY_BOOL3.get());

        jobBuilder.close();
    }

    @AnalyzerBean("Test analyzer")
    public static class TestAnalyzer extends MockAnalyzer {

        @Configured
        boolean produceAnError = false;

        @Configured
        boolean produceAnErrorOnGetResult = false;

        @Override
        public void run(InputRow row, int distinctCount) {
            if (produceAnError) {
                throw new IllegalStateException("produceAnError=true");
            }
            super.run(row, distinctCount);
        }

        @Override
        public ListResult<InputRow> getResult() {
            if (produceAnErrorOnGetResult) {
                throw new IllegalStateException("produceAnErrorOnGetResult=true");
            }
            return super.getResult();
        }

    }

    @TransformerBean("Test transformer1")
    public static class TestTransformer1 extends MockTransformer {
        @Close(onFailure = false)
        public void closeIfSuccessful() {
            MY_BOOL1.set(true);
        }
    }

    @TransformerBean("Test transformer2")
    public static class TestTransformer2 extends MockTransformer {
        @Close(onSuccess = false)
        public void closeIfFailure() {
            MY_BOOL2.set(true);
        }
    }

    @TransformerBean("Test transformer3")
    public static class TestTransformer3 extends MockTransformer {
        @Close
        public void closeAlways() {
            MY_BOOL3.set(true);
        }
    }
}
