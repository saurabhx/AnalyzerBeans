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
package org.eobjects.analyzer.cluster;

import java.util.List;
import java.util.Map;

import org.eobjects.analyzer.beans.CompletenessAnalyzer;
import org.eobjects.analyzer.beans.NumberAnalyzer;
import org.eobjects.analyzer.beans.NumberAnalyzerResult;
import org.eobjects.analyzer.beans.StringAnalyzer;
import org.eobjects.analyzer.beans.StringAnalyzerResult;
import org.eobjects.analyzer.beans.CompletenessAnalyzer.Condition;
import org.eobjects.analyzer.beans.CompletenessAnalyzerResult;
import org.eobjects.analyzer.beans.filter.EqualsFilter;
import org.eobjects.analyzer.beans.filter.MaxRowsFilter;
import org.eobjects.analyzer.beans.filter.ValidationCategory;
import org.eobjects.analyzer.beans.transform.ConcatenatorTransformer;
import org.eobjects.analyzer.beans.valuematch.ValueMatchAnalyzer;
import org.eobjects.analyzer.beans.valuematch.ValueMatchAnalyzerResult;
import org.eobjects.analyzer.beans.writers.InsertIntoTableAnalyzer;
import org.eobjects.analyzer.beans.writers.WriteBufferSizeOption;
import org.eobjects.analyzer.configuration.AnalyzerBeansConfiguration;
import org.eobjects.analyzer.configuration.AnalyzerBeansConfigurationImpl;
import org.eobjects.analyzer.connection.Datastore;
import org.eobjects.analyzer.connection.DatastoreCatalog;
import org.eobjects.analyzer.connection.DatastoreCatalogImpl;
import org.eobjects.analyzer.connection.DatastoreConnection;
import org.eobjects.analyzer.connection.JdbcDatastore;
import org.eobjects.analyzer.connection.UpdateableDatastoreConnection;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.InputRow;
import org.eobjects.analyzer.data.MetaModelInputColumn;
import org.eobjects.analyzer.descriptors.Descriptors;
import org.eobjects.analyzer.descriptors.SimpleDescriptorProvider;
import org.eobjects.analyzer.job.AnalysisJob;
import org.eobjects.analyzer.job.ComponentJob;
import org.eobjects.analyzer.job.builder.AnalysisJobBuilder;
import org.eobjects.analyzer.job.builder.AnalyzerJobBuilder;
import org.eobjects.analyzer.job.builder.FilterJobBuilder;
import org.eobjects.analyzer.job.builder.TransformerJobBuilder;
import org.eobjects.analyzer.job.concurrent.MultiThreadedTaskRunner;
import org.eobjects.analyzer.job.concurrent.SingleThreadedTaskRunner;
import org.eobjects.analyzer.job.concurrent.TaskRunner;
import org.eobjects.analyzer.job.runner.AnalysisResultFuture;
import org.eobjects.analyzer.job.runner.JobStatus;
import org.eobjects.analyzer.result.AnalyzerResult;
import org.eobjects.analyzer.test.TestHelper;
import org.apache.metamodel.UpdateCallback;
import org.apache.metamodel.UpdateScript;
import org.apache.metamodel.data.DataSet;
import org.apache.metamodel.schema.ColumnType;
import org.apache.metamodel.schema.Schema;
import org.junit.Assert;

public class ClusterTestHelper {

    /**
     * Creates a {@link AnalyzerBeansConfiguration} object (based on a few
     * parameters), typically to use in test methods of this class.
     * 
     * @param testName
     * @param multiThreaded
     * @return
     */
    public static AnalyzerBeansConfiguration createConfiguration(String testName, boolean multiThreaded) {
        final JdbcDatastore csvDatastore = new JdbcDatastore("csv", "jdbc:h2:mem:" + testName, "org.h2.Driver", "SA",
                "", true);
        final UpdateableDatastoreConnection con = csvDatastore.openConnection();
        con.getUpdateableDataContext().executeUpdate(new UpdateScript() {
            @Override
            public void run(UpdateCallback callback) {
                Schema schema = callback.getDataContext().getDefaultSchema();
                if (schema.getTableByName("testtable") != null) {
                    return;
                }
                callback.createTable(schema, "testtable").withColumn("id").ofType(ColumnType.INTEGER).asPrimaryKey()
                        .withColumn("name").ofType(ColumnType.VARCHAR).execute();
            }
        });
        con.close();

        final Datastore databaseDatastore = TestHelper.createSampleDatabaseDatastore("orderdb");

        final DatastoreCatalog datastoreCatalog = new DatastoreCatalogImpl(databaseDatastore, csvDatastore);
        final TaskRunner taskRunner;
        if (multiThreaded) {
            taskRunner = new MultiThreadedTaskRunner(20);
        } else {
            taskRunner = new SingleThreadedTaskRunner();
        }
        final SimpleDescriptorProvider descriptorProvider = new SimpleDescriptorProvider(true);
        descriptorProvider.addFilterBeanDescriptor(Descriptors.ofFilter(MaxRowsFilter.class));
        descriptorProvider.addTransformerBeanDescriptor(Descriptors.ofTransformer(MockTransformerThatWillFail.class));
        descriptorProvider.addTransformerBeanDescriptor(Descriptors.ofTransformer(ConcatenatorTransformer.class));
        descriptorProvider.addAnalyzerBeanDescriptor(Descriptors.ofAnalyzer(InsertIntoTableAnalyzer.class));
        descriptorProvider.addAnalyzerBeanDescriptor(Descriptors.ofAnalyzer(CompletenessAnalyzer.class));
        descriptorProvider.addAnalyzerBeanDescriptor(Descriptors.ofAnalyzer(ValueMatchAnalyzer.class));
        descriptorProvider.addAnalyzerBeanDescriptor(Descriptors.ofAnalyzer(MockAnalyzerWithBadReducer.class));

        final AnalyzerBeansConfiguration configuration = new AnalyzerBeansConfigurationImpl().replace(taskRunner)
                .replace(datastoreCatalog).replace(descriptorProvider);
        return configuration;
    }

    /**
     * Runs a job that verifies that errors (caused by the
     * {@link MockTransformerThatWillFail} dummy component) are picked up
     * correctly from the slave nodes.
     * 
     * @param configuration
     * @param virtualClusterManager
     * @return the list of errors returned, to perform further assertions
     */
    public static List<Throwable> runErrorHandlingJob(AnalyzerBeansConfiguration configuration,
            ClusterManager clusterManager) {
        final AnalysisJobBuilder jobBuilder = new AnalysisJobBuilder(configuration);
        jobBuilder.setDatastore("orderdb");
        jobBuilder.addSourceColumns("CUSTOMERS.CUSTOMERNUMBER");

        final TransformerJobBuilder<MockTransformerThatWillFail> transformer = jobBuilder
                .addTransformer(MockTransformerThatWillFail.class);
        transformer.addInputColumns(jobBuilder.getSourceColumns());

        final AnalyzerJobBuilder<CompletenessAnalyzer> analyzer = jobBuilder.addAnalyzer(CompletenessAnalyzer.class);
        analyzer.addInputColumns(transformer.getOutputColumns());
        analyzer.setConfiguredProperty("Conditions",
                new CompletenessAnalyzer.Condition[] { CompletenessAnalyzer.Condition.NOT_BLANK_OR_NULL });

        // build the job
        final AnalysisJob job = jobBuilder.toAnalysisJob();

        // run the job in a distributed fashion
        final DistributedAnalysisRunner runner = new DistributedAnalysisRunner(configuration, clusterManager);
        final AnalysisResultFuture resultFuture = runner.run(job);

        switch (resultFuture.getStatus()) {
        case NOT_FINISHED:
        case ERRORNOUS:
            break;
        default:
            Assert.fail("Unexpected job status: " + resultFuture.getStatus());
        }

        resultFuture.await();

        if (resultFuture.isSuccessful()) {
            Assert.fail("Job that was supposed to fail was succesful! Results: " + resultFuture.getResultMap());
        }

        Assert.assertEquals(JobStatus.ERRORNOUS, resultFuture.getStatus());

        final List<Throwable> errors = resultFuture.getErrors();

        Assert.assertNotNull(errors);
        Assert.assertFalse(errors.isEmpty());

        jobBuilder.close();

        return errors;
    }

    public static void runBasicAnalyzersJob(AnalyzerBeansConfiguration configuration, ClusterManager clusterManager)
            throws Throwable {
        // build a job that concats names and inserts the concatenated names
        // into a file
        final AnalysisJobBuilder jobBuilder = new AnalysisJobBuilder(configuration);
        jobBuilder.setDatastore("orderdb");
        jobBuilder.addSourceColumns("CUSTOMERS.CUSTOMERNUMBER");
        jobBuilder.addSourceColumns("CUSTOMERS.COUNTRY");

        final AnalyzerJobBuilder<StringAnalyzer> stringAnalyzer = jobBuilder.addAnalyzer(StringAnalyzer.class);
        stringAnalyzer.addInputColumns(jobBuilder.getAvailableInputColumns(String.class));

        final AnalyzerJobBuilder<NumberAnalyzer> numberAnalyzer = jobBuilder.addAnalyzer(NumberAnalyzer.class);
        numberAnalyzer.addInputColumns(jobBuilder.getAvailableInputColumns(Number.class));

        final AnalysisJob job = jobBuilder.toAnalysisJob();

        // run the job in a distributed fashion
        final DistributedAnalysisRunner runner = new DistributedAnalysisRunner(configuration, clusterManager);
        final AnalysisResultFuture resultFuture = runner.run(job);

        Assert.assertTrue(resultFuture.getStatus() == JobStatus.NOT_FINISHED
                || resultFuture.getStatus() == JobStatus.SUCCESSFUL);

        jobBuilder.close();
        resultFuture.await();

        if (resultFuture.isErrornous()) {
            List<Throwable> errors = resultFuture.getErrors();
            throw errors.get(0);
        }

        Assert.assertEquals(JobStatus.SUCCESSFUL, resultFuture.getStatus());

        final List<AnalyzerResult> results = resultFuture.getResults();

        Assert.assertEquals(2, results.size());

        for (AnalyzerResult analyzerResult : results) {
            Assert.assertNotNull(analyzerResult);
            if (analyzerResult instanceof StringAnalyzerResult) {
                final StringAnalyzerResult stringAnalyzerResult = (StringAnalyzerResult) analyzerResult;

                final InputColumn<String>[] columns = stringAnalyzerResult.getColumns();
                Assert.assertEquals(1, columns.length);

                final InputColumn<String> column = columns[0];
                Assert.assertEquals("COUNTRY", column.getName());

                // test reduction: various ways of aggregating crosstab metrics
                // - min, max, avg, sum
                Assert.assertEquals(122, stringAnalyzerResult.getRowCount(column));
                Assert.assertEquals(1, stringAnalyzerResult.getMinWords(column));
                Assert.assertEquals(2, stringAnalyzerResult.getMaxWords(column));
                Assert.assertEquals(5.71, stringAnalyzerResult.getAvgChars(column), 0.1d);
                Assert.assertEquals(697, stringAnalyzerResult.getTotalCharCount(column));

            } else if (analyzerResult instanceof NumberAnalyzerResult) {
                final NumberAnalyzerResult numberAnalyzerResult = (NumberAnalyzerResult) analyzerResult;

                final InputColumn<? extends Number>[] columns = numberAnalyzerResult.getColumns();
                Assert.assertEquals(1, columns.length);

                final InputColumn<? extends Number> column = columns[0];
                Assert.assertEquals("CUSTOMERNUMBER", column.getName());

                Assert.assertEquals(122, numberAnalyzerResult.getRowCount(column));
                Assert.assertEquals(36161.0, numberAnalyzerResult.getSum(column).doubleValue(), 0.1);
                Assert.assertEquals(296.4, numberAnalyzerResult.getMean(column).doubleValue(), 0.1);
                Assert.assertEquals(496, numberAnalyzerResult.getHighestValue(column).doubleValue(), 0.1);
                Assert.assertEquals(103.0, numberAnalyzerResult.getLowestValue(column).doubleValue(), 0.1);
                Assert.assertEquals(117.0, numberAnalyzerResult.getStandardDeviation(column).doubleValue(), 0.8);
                Assert.assertEquals(null, numberAnalyzerResult.getMedian(column));
            } else {
                Assert.fail("Unexpected analyzer result found: " + analyzerResult);
            }
        }
    }

    public static void runCompletenessAndValueMatcherAnalyzerJob(AnalyzerBeansConfiguration configuration,
            ClusterManager clusterManager) throws Throwable {
        // build a job that concats names and inserts the concatenated names
        // into a file
        final AnalysisJobBuilder jobBuilder = new AnalysisJobBuilder(configuration);
        jobBuilder.setDatastore("orderdb");
        jobBuilder.addSourceColumns("CUSTOMERS.CUSTOMERNUMBER", "CUSTOMERS.CONTACTFIRSTNAME",
                "CUSTOMERS.CONTACTLASTNAME", "CUSTOMERS.COUNTRY", "CUSTOMERS.ADDRESSLINE2");

        List<MetaModelInputColumn> cols = jobBuilder.getSourceColumns();
        AnalyzerJobBuilder<CompletenessAnalyzer> completeness = jobBuilder.addAnalyzer(CompletenessAnalyzer.class);
        completeness.addInputColumns(cols);
        Condition[] conditions = new CompletenessAnalyzer.Condition[cols.size()];
        for (int i = 0; i < conditions.length; i++) {
            conditions[i] = Condition.NOT_BLANK_OR_NULL;
        }
        completeness.setConfiguredProperty("Conditions", conditions);

        AnalyzerJobBuilder<ValueMatchAnalyzer> valueMatch = jobBuilder.addAnalyzer(ValueMatchAnalyzer.class);
        valueMatch.addInputColumn(jobBuilder.getSourceColumnByName("COUNTRY"));
        valueMatch.setConfiguredProperty("Expected values", new String[] { "United States", "USA", "Denmark",
                "Danmark", "Netherlands" });

        final AnalysisJob job = jobBuilder.toAnalysisJob();

        // run the job in a distributed fashion
        final DistributedAnalysisRunner runner = new DistributedAnalysisRunner(configuration, clusterManager);
        final AnalysisResultFuture resultFuture = runner.run(job);

        Assert.assertEquals(JobStatus.NOT_FINISHED, resultFuture.getStatus());

        jobBuilder.close();
        resultFuture.await();

        if (resultFuture.isErrornous()) {
            List<Throwable> errors = resultFuture.getErrors();
            throw errors.get(0);
        }

        Assert.assertEquals(JobStatus.SUCCESSFUL, resultFuture.getStatus());

        final List<AnalyzerResult> results = resultFuture.getResults();
        Assert.assertEquals(2, results.size());

        for (AnalyzerResult analyzerResult : results) {
            Assert.assertNotNull(analyzerResult);
            if (analyzerResult instanceof CompletenessAnalyzerResult) {
                // Check completeness analyzer result

                CompletenessAnalyzerResult completenessAnalyzerResult = (CompletenessAnalyzerResult) analyzerResult;

                Assert.assertEquals(109, completenessAnalyzerResult.getInvalidRowCount());

                InputRow[] rows = completenessAnalyzerResult.getRows();
                Assert.assertNotNull(rows);

                Assert.assertTrue("No annotated rows available in CompletenessAnalyzer's result", rows.length > 0);

            } else if (analyzerResult instanceof ValueMatchAnalyzerResult) {

                ValueMatchAnalyzerResult valueMatchAnalyzerResult = (ValueMatchAnalyzerResult) analyzerResult;
                Assert.assertEquals(0, valueMatchAnalyzerResult.getNullCount());

                Assert.assertEquals(83, valueMatchAnalyzerResult.getUnexpectedValueCount().intValue());
                InputRow[] rows = valueMatchAnalyzerResult.getAnnotatedRowsForUnexpectedValues().getRows();
                Assert.assertTrue(rows.length > 0);
                Assert.assertTrue(rows.length <= 83);

                Assert.assertEquals(2, valueMatchAnalyzerResult.getCount("Denmark").intValue());
                rows = valueMatchAnalyzerResult.getAnnotatedRowsForValue("Denmark").getRows();
                Assert.assertEquals(2, rows.length);
                for (InputRow row : rows) {
                    String rowString = row.toString();
                    boolean assert1 = rowString
                            .equals("MetaModelInputRow[Row[values=[145, Jytte, Petersen, Denmark, null]]]");
                    boolean assert2 = rowString
                            .equals("MetaModelInputRow[Row[values=[227, Palle, Ibsen, Denmark, null]]]");

                    Assert.assertTrue("Unexpected 'Denmark' row: " + rowString, assert1 || assert2);
                }
            } else {
                Assert.fail("Unexpected analyzer result found: " + analyzerResult);
            }
        }

    }

    /**
     * Runs a simple job that is fully distributable and should be able to
     * execute in all contexts. The job does one transformation (concatenates
     * two fields) and inserts this field, together with a source field, into
     * another table.
     * 
     * @param configuration
     * @param clusterManager
     * @throws Throwable
     */
    public static void runConcatAndInsertJob(AnalyzerBeansConfiguration configuration, ClusterManager clusterManager)
            throws Throwable {
        // build a job that concats names and inserts the concatenated names
        // into a file
        final AnalysisJobBuilder jobBuilder = new AnalysisJobBuilder(configuration);
        jobBuilder.setDatastore("orderdb");
        jobBuilder.addSourceColumns("CUSTOMERS.CUSTOMERNUMBER", "CUSTOMERS.CONTACTFIRSTNAME",
                "CUSTOMERS.CONTACTLASTNAME");

        // concatenate firstname + lastname
        final TransformerJobBuilder<ConcatenatorTransformer> concatenator = jobBuilder
                .addTransformer(ConcatenatorTransformer.class);
        concatenator.addInputColumn(jobBuilder.getSourceColumnByName("CONTACTFIRSTNAME"));
        concatenator.addInputColumn(jobBuilder.getSourceColumnByName("CONTACTLASTNAME"));
        concatenator.setConfiguredProperty("Separator", " ");

        // insert into CSV file
        final Datastore csvDatastore = configuration.getDatastoreCatalog().getDatastore("csv");
        final Datastore dbDatastore = configuration.getDatastoreCatalog().getDatastore("orderdb");
        final DatastoreConnection csvCon = csvDatastore.openConnection();
        final DatastoreConnection dbCon = dbDatastore.openConnection();
        try {
            Schema schema = csvCon.getDataContext().getDefaultSchema();
            final String schemaName = schema.getName();
            final String tableName = schema.getTable(0).getName();

            final AnalyzerJobBuilder<InsertIntoTableAnalyzer> insert = jobBuilder
                    .addAnalyzer(InsertIntoTableAnalyzer.class);
            insert.setConfiguredProperty("Datastore", csvDatastore);
            insert.addInputColumn(jobBuilder.getSourceColumnByName("CUSTOMERNUMBER"));
            insert.addInputColumn(concatenator.getOutputColumns().get(0));
            insert.setConfiguredProperty("Schema name", schemaName);
            insert.setConfiguredProperty("Table name", tableName);
            insert.setConfiguredProperty("Column names", new String[] { "id", "name" });
            insert.setConfiguredProperty("Buffer size", WriteBufferSizeOption.TINY);

            // build the job
            final AnalysisJob job = jobBuilder.toAnalysisJob();

            // run the job in a distributed fashion
            final DistributedAnalysisRunner runner = new DistributedAnalysisRunner(configuration, clusterManager);
            final AnalysisResultFuture resultFuture = runner.run(job);

            Assert.assertEquals(JobStatus.NOT_FINISHED, resultFuture.getStatus());

            resultFuture.await();

            if (resultFuture.isErrornous()) {
                List<Throwable> errors = resultFuture.getErrors();
                throw errors.get(0);
            }

            Assert.assertEquals(JobStatus.SUCCESSFUL, resultFuture.getStatus());

            // check that the file created has the same amount of records as the
            // CUSTOMER table of orderdb.
            DataSet ds1 = dbCon.getDataContext().query().from("CUSTOMERS").selectCount().execute();
            DataSet ds2 = csvCon.getDataContext().query().from(tableName).selectCount().execute();
            try {
                Assert.assertTrue(ds1.next());
                Assert.assertTrue(ds2.next());
                Assert.assertEquals(ds1.getRow().toString(), ds2.getRow().toString());
            } finally {
                ds1.close();
                ds2.close();
            }

            // await multiple times to ensure that second time isn't distorting
            // the result
            resultFuture.await();
            resultFuture.await();

            // check that the analysis result elements are there...
            final Map<ComponentJob, AnalyzerResult> resultMap = resultFuture.getResultMap();
            Assert.assertEquals(1, resultMap.size());
            Assert.assertEquals("{ImmutableAnalyzerJob[name=null,analyzer=Insert into table]=122 inserts executed}",
                    resultMap.toString());

        } finally {
            dbCon.close();
            csvCon.close();
            jobBuilder.close();
        }
    }

    public static void runNoExpectedRecordsJob(AnalyzerBeansConfiguration configuration) throws Throwable {
        final AnalysisJob job;
        {
            final AnalysisJobBuilder jobBuilder = new AnalysisJobBuilder(configuration);
            try {
                // build a job that concats names and inserts the concatenated
                // names
                // into a file
                jobBuilder.setDatastore("orderdb");
                jobBuilder.addSourceColumns("CUSTOMERS.CUSTOMERNUMBER", "CUSTOMERS.CONTACTFIRSTNAME",
                        "CUSTOMERS.CONTACTLASTNAME");

                final FilterJobBuilder<EqualsFilter, ValidationCategory> equalsFilter = jobBuilder
                        .addFilter(EqualsFilter.class);
                equalsFilter.addInputColumn(jobBuilder.getSourceColumnByName("CUSTOMERNUMBER"));
                equalsFilter.getConfigurableBean().setValues(new String[] { "-1000000" });

                final AnalyzerJobBuilder<StringAnalyzer> stringAnalyzer = jobBuilder.addAnalyzer(StringAnalyzer.class);
                stringAnalyzer.addInputColumns(jobBuilder.getAvailableInputColumns(String.class));
                stringAnalyzer.setRequirement(equalsFilter, ValidationCategory.VALID);

                job = jobBuilder.toAnalysisJob();
            } finally {
                jobBuilder.close();
            }
        }

        final DistributedAnalysisRunner analysisRunner = new DistributedAnalysisRunner(configuration,
                new ClusterManager() {
                    @Override
                    public JobDivisionManager getJobDivisionManager() {
                        throw new IllegalStateException(
                                "Since this job should yield 0 expected records, this method should not be invoked");
                    }

                    @Override
                    public AnalysisResultFuture dispatchJob(AnalysisJob job, DistributedJobContext context)
                            throws Exception {
                        throw new IllegalStateException(
                                "Since this job should yield 0 expected records, this method should not be invoked");
                    }
                });

        final AnalysisResultFuture resultFuture = analysisRunner.run(job);
        resultFuture.await();
        if (resultFuture.isErrornous()) {
            throw resultFuture.getErrors().get(0);
        }

        final List<AnalyzerResult> results = resultFuture.getResults();
        Assert.assertEquals(1, results.size());

        final AnalyzerResult analyzerResult = results.get(0);
        Assert.assertTrue(analyzerResult instanceof StringAnalyzerResult);
    }
}
