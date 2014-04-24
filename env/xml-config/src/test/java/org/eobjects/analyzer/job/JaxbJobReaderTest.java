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
package org.eobjects.analyzer.job;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import org.eobjects.analyzer.beans.StringAnalyzerResult;
import org.eobjects.analyzer.beans.convert.ConvertToDateTransformer;
import org.eobjects.analyzer.beans.dategap.DateGapAnalyzerResult;
import org.eobjects.analyzer.beans.dategap.DateGapTextRenderer;
import org.eobjects.analyzer.beans.transform.DateMaskMatcherTransformer;
import org.eobjects.analyzer.beans.valuedist.ValueDistributionAnalyzerResult;
import org.eobjects.analyzer.configuration.AnalyzerBeansConfiguration;
import org.eobjects.analyzer.configuration.AnalyzerBeansConfigurationImpl;
import org.eobjects.analyzer.configuration.SourceColumnMapping;
import org.eobjects.analyzer.connection.CsvDatastore;
import org.eobjects.analyzer.connection.Datastore;
import org.eobjects.analyzer.connection.DatastoreCatalog;
import org.eobjects.analyzer.connection.DatastoreCatalogImpl;
import org.eobjects.analyzer.connection.DatastoreConnection;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.MetaModelInputColumn;
import org.eobjects.analyzer.descriptors.ClasspathScanDescriptorProvider;
import org.eobjects.analyzer.descriptors.DescriptorProvider;
import org.eobjects.analyzer.job.builder.AnalysisJobBuilder;
import org.eobjects.analyzer.job.builder.AnalyzerJobBuilder;
import org.eobjects.analyzer.job.builder.TransformerJobBuilder;
import org.eobjects.analyzer.job.runner.AnalysisResultFuture;
import org.eobjects.analyzer.job.runner.AnalysisRunner;
import org.eobjects.analyzer.job.runner.AnalysisRunnerImpl;
import org.eobjects.analyzer.result.AnalyzerResult;
import org.eobjects.analyzer.result.CrosstabResult;
import org.eobjects.analyzer.result.renderer.CrosstabTextRenderer;
import org.eobjects.analyzer.test.TestHelper;
import org.eobjects.analyzer.util.SchemaNavigator;
import org.apache.metamodel.util.ToStringComparator;

public class JaxbJobReaderTest extends TestCase {

    private final DescriptorProvider descriptorProvider = new ClasspathScanDescriptorProvider().scanPackage(
            "org.eobjects.analyzer.beans", true).scanPackage("org.eobjects.analyzer.result", true);
    private final DatastoreCatalog datastoreCatalog = new DatastoreCatalogImpl(
            TestHelper.createSampleDatabaseDatastore("my database"));
    private final AnalyzerBeansConfiguration conf = new AnalyzerBeansConfigurationImpl().replace(datastoreCatalog)
            .replace(descriptorProvider);

    public void testReadComponentNames() throws Exception {
        JobReader<InputStream> reader = new JaxbJobReader(conf);
        AnalysisJob job = reader
                .read(new FileInputStream(new File("src/test/resources/example-job-merged-outcome.xml")));

        assertEquals(1, job.getAnalyzerJobs().size());
        assertEquals("analyzer_1", job.getAnalyzerJobs().iterator().next().getName());

        assertEquals(2, job.getFilterJobs().size());
        assertEquals("single_word_1", job.getFilterJobs().iterator().next().getName());

        assertEquals(1, job.getTransformerJobs().size());
        assertEquals("email_std_1", job.getTransformerJobs().iterator().next().getName());

        assertEquals(2, job.getMergedOutcomeJobs().size());
        assertEquals("merge_1", job.getMergedOutcomeJobs().iterator().next().getName());
    }

    public void testReadMetadataFull() throws Exception {
        JobReader<InputStream> reader = new JaxbJobReader(conf);
        AnalysisJobMetadata metadata = reader.readMetadata(new FileInputStream(new File(
                "src/test/resources/example-job-metadata.xml")));

        assertEquals("Kasper Sørensen", metadata.getAuthor());
        assertEquals("my database", metadata.getDatastoreName());
        assertEquals("Job metadata", metadata.getJobName());
        assertEquals("An example job with complete metadata", metadata.getJobDescription());
        assertEquals("1.1", metadata.getJobVersion());
        assertEquals("[PUBLIC.PERSONS.FIRSTNAME, PUBLIC.PERSONS.LASTNAME]", metadata.getSourceColumnPaths().toString());

        assertNotNull(metadata.getCreatedDate());
        assertNotNull(metadata.getUpdatedDate());
    }

    public void testReadMetadataNone() throws Exception {
        JobReader<InputStream> reader = new JaxbJobReader(new AnalyzerBeansConfigurationImpl());
        AnalysisJobMetadata metadata = reader.readMetadata(new FileInputStream(new File(
                "src/test/resources/example-job-valid.xml")));

        assertNull(metadata.getAuthor());
        assertNull(metadata.getJobName());
        assertNull(metadata.getJobDescription());
        assertNull(metadata.getJobVersion());
        assertEquals("my database", metadata.getDatastoreName());
        assertEquals("[PUBLIC.EMPLOYEES.FIRSTNAME, PUBLIC.EMPLOYEES.LASTNAME, PUBLIC.EMPLOYEES.EMAIL]", metadata
                .getSourceColumnPaths().toString());

        assertNull(metadata.getCreatedDate());
        assertNull(metadata.getUpdatedDate());
    }

    public void testSimpleFilter() throws Exception {
        JaxbJobReader reader = new JaxbJobReader(conf);
        AnalysisJobBuilder jobBuilder = reader.create(new File("src/test/resources/example-job-simple-filter.xml"));
        assertEquals(1, jobBuilder.getFilterJobBuilders().size());
        assertEquals(3, jobBuilder.getAnalyzerJobBuilders().size());

        AnalysisJob analysisJob = jobBuilder.toAnalysisJob();

        AnalysisResultFuture resultFuture = new AnalysisRunnerImpl(conf).run(analysisJob);

        List<AnalyzerResult> results = resultFuture.getResults();
        assertEquals(3, results.size());

        // sort it to make sure test is deterministic
        Collections.sort(results, ToStringComparator.getComparator());

        // the first result is for the unfiltered String analyzer
        CrosstabResult res3 = (CrosstabResult) results.get(0);
        assertEquals(1, res3.getCrosstab().where("Column", "FIRSTNAME").where("Measures", "Min words").get());
        assertEquals(2, res3.getCrosstab().where("Column", "FIRSTNAME").where("Measures", "Max words").get());

        // this result represents the single manager (one unique and no repeated
        // values)
        ValueDistributionAnalyzerResult res1 = (ValueDistributionAnalyzerResult) results.get(1);
        assertEquals("[[<unique>->1]]", res1.getValueCounts().toString());
        assertEquals(1, res1.getUniqueCount().intValue());

        // this result represents all the employees: Two repeated values and 18
        // unique
        ValueDistributionAnalyzerResult res2 = (ValueDistributionAnalyzerResult) results.get(2);
        assertEquals(18, res2.getUniqueCount().intValue());
        assertEquals("[[<unique>->18], [Gerard->2], [Leslie->2]]", res2.getValueCounts().toString());
    }

    public void testNamedInputs() throws Exception {
        JaxbJobReader factory = new JaxbJobReader(conf);
        AnalysisJobBuilder jobBuilder = factory.create(new File("src/test/resources/example-job-named-inputs.xml"));
        assertEquals(true, jobBuilder.isConfigured());

        assertEquals(2, jobBuilder.getTransformerJobBuilders().size());

        List<AnalyzerJobBuilder<?>> analyzerJobBuilders = jobBuilder.getAnalyzerJobBuilders();
        assertEquals(1, analyzerJobBuilders.size());

        AnalyzerJobBuilder<?> analyzerJobBuilder = analyzerJobBuilders.get(0);
        AnalyzerJob analyzerJob = analyzerJobBuilder.toAnalyzerJob();
        BeanConfiguration configuration = analyzerJob.getConfiguration();

        InputColumn<?> col1 = (InputColumn<?>) configuration.getProperty(analyzerJob.getDescriptor()
                .getConfiguredProperty("From column"));
        assertEquals("date 1", col1.getName());

        InputColumn<?> col2 = (InputColumn<?>) configuration.getProperty(analyzerJob.getDescriptor()
                .getConfiguredProperty("To column"));
        assertEquals("date 2", col2.getName());

        AnalysisJob analysisJob = jobBuilder.toAnalysisJob();

        AnalysisResultFuture resultFuture = new AnalysisRunnerImpl(conf).run(analysisJob);
        List<AnalyzerResult> results = resultFuture.getResults();
        assertEquals(1, results.size());
        DateGapAnalyzerResult result = (DateGapAnalyzerResult) results.get(0);
        String[] resultLines = new DateGapTextRenderer().render(result).split("\n");
        assertEquals(58, resultLines.length);
        assertEquals(" - time gap: 2003-01-18 to 2003-01-29", resultLines[0]);
        assertEquals(" - time gap: 2003-02-09 to 2003-02-11", resultLines[1]);
        assertEquals(" - time gap: 2003-05-16 to 2003-05-20", resultLines[2]);
        assertEquals(" - time gap: 2003-07-23 to 2003-07-24", resultLines[3]);
        assertEquals(" - time gap: 2003-08-21 to 2003-08-25", resultLines[4]);
        assertEquals(" - time gap: 2003-09-02 to 2003-09-03", resultLines[5]);
        assertEquals(" - time gap: 2003-11-03 to 2003-11-04", resultLines[6]);
        assertEquals(" - time gap: 2003-12-17 to 2004-01-02", resultLines[7]);
        assertEquals(" - time gap: 2004-05-24 to 2004-05-26", resultLines[8]);
        assertEquals(" - time gap: 2004-09-22 to 2004-09-27", resultLines[9]);
        assertEquals(" - time gap: 2004-12-24 to 2005-01-05", resultLines[10]);
        assertEquals(" - time gap: 2005-05-28 to 2005-05-29", resultLines[11]);
        assertEquals(" - time overlap: 2003-01-09 to 2003-01-18", resultLines[12]);
        assertEquals(" - time overlap: 2003-01-31 to 2003-02-07", resultLines[13]);
        assertEquals(" - time overlap: 2005-05-29 to 2005-06-08", resultLines[57]);
    }

    public void testInvalidRead() throws Exception {
        JaxbJobReader factory = new JaxbJobReader(new AnalyzerBeansConfigurationImpl());
        try {
            factory.create(new File("src/test/resources/example-job-invalid.xml"));
            fail("Exception expected");
        } catch (IllegalArgumentException e) {
            assertEquals("javax.xml.bind.UnmarshalException: unexpected element "
                    + "(uri:\"http://eobjects.org/analyzerbeans/job/1.0\", local:\"datacontext\"). "
                    + "Expected elements are <{http://eobjects.org/analyzerbeans/job/1.0}variables>,"
                    + "<{http://eobjects.org/analyzerbeans/job/1.0}columns>,"
                    + "<{http://eobjects.org/analyzerbeans/job/1.0}data-context>", e.getMessage());
        }
    }

    public void testMissingDatastore() throws Exception {
        JaxbJobReader factory = new JaxbJobReader(new AnalyzerBeansConfigurationImpl());
        try {
            factory.create(new File("src/test/resources/example-job-valid.xml"));
            fail("Exception expected");
        } catch (NoSuchDatastoreException e) {
            assertEquals("No such datastore: my database", e.getMessage());
        }
    }

    public void testMissingTransformerDescriptor() throws Exception {
        JaxbJobReader factory = new JaxbJobReader(conf);
        try {
            factory.create(new File("src/test/resources/example-job-missing-descriptor.xml"));
            fail("Exception expected");
        } catch (NoSuchComponentException e) {
            assertEquals("No such Transformer descriptor: tokenizerDescriptor", e.getMessage());
        }
    }

    public void testValidJob() throws Exception {
        JaxbJobReader factory = new JaxbJobReader(conf);
        AnalysisJobBuilder builder = factory.create(new File("src/test/resources/example-job-valid.xml"));
        assertTrue(builder.isConfigured());

        List<MetaModelInputColumn> sourceColumns = builder.getSourceColumns();
        assertEquals(3, sourceColumns.size());
        assertEquals("MetaModelInputColumn[PUBLIC.EMPLOYEES.FIRSTNAME]", sourceColumns.get(0).toString());
        assertEquals("MetaModelInputColumn[PUBLIC.EMPLOYEES.LASTNAME]", sourceColumns.get(1).toString());
        assertEquals("MetaModelInputColumn[PUBLIC.EMPLOYEES.EMAIL]", sourceColumns.get(2).toString());

        assertEquals(1, builder.getTransformerJobBuilders().size());
        assertEquals(
                "[TransformedInputColumn[id=trans-0001-0002,name=username], TransformedInputColumn[id=trans-0001-0003,name=domain]]",
                builder.getTransformerJobBuilders().get(0).getOutputColumns().toString());
        assertEquals("[TransformedInputColumn[id=trans-0001-0002,name=username], "
                + "TransformedInputColumn[id=trans-0001-0003,name=domain], "
                + "MetaModelInputColumn[PUBLIC.EMPLOYEES.FIRSTNAME], "
                + "MetaModelInputColumn[PUBLIC.EMPLOYEES.LASTNAME]]",
                Arrays.toString(builder.getAnalyzerJobBuilders().get(0).toAnalyzerJob().getInput()));

        List<AnalyzerResult> results = new AnalysisRunnerImpl(conf).run(builder.toAnalysisJob()).getResults();
        assertEquals(1, results.size());
        CrosstabResult crosstabResult = (CrosstabResult) results.get(0);

        String[] resultLines = crosstabResult.toString(-1).split("\n");
        assertEquals(85, resultLines.length);
        assertEquals("Crosstab:", resultLines[0]);
        assertEquals("FIRSTNAME,Avg chars: 5.391304347826087", resultLines[1]);
        assertEquals("FIRSTNAME,Avg white spaces: 0.043478260869565216", resultLines[2]);
        assertEquals("FIRSTNAME,Blank count: 0", resultLines[3]);
        assertEquals("FIRSTNAME,Diacritic chars: 0", resultLines[4]);
        assertEquals("FIRSTNAME,Digit chars: 0", resultLines[5]);
    }

    public void testUsingSourceColumnMapping() throws Throwable {
        Datastore datastore = TestHelper.createSampleDatabaseDatastore("another datastore name");
        JobReader<InputStream> reader = new JaxbJobReader(conf);

        AnalysisJobMetadata metadata = reader.readMetadata(new FileInputStream(new File(
                "src/test/resources/example-job-valid.xml")));
        SourceColumnMapping sourceColumnMapping = new SourceColumnMapping(metadata.getSourceColumnPaths());
        assertFalse(sourceColumnMapping.isSatisfied());
        assertEquals("[PUBLIC.EMPLOYEES.EMAIL, PUBLIC.EMPLOYEES.FIRSTNAME, PUBLIC.EMPLOYEES.LASTNAME]",
                sourceColumnMapping.getPaths().toString());

        sourceColumnMapping.setDatastore(datastore);
        DatastoreConnection con = datastore.openConnection();
        SchemaNavigator sn = con.getSchemaNavigator();
        sourceColumnMapping.setColumn("PUBLIC.EMPLOYEES.EMAIL", sn.convertToColumn("PUBLIC.CUSTOMERS.PHONE"));
        sourceColumnMapping.setColumn("PUBLIC.EMPLOYEES.FIRSTNAME",
                sn.convertToColumn("PUBLIC.CUSTOMERS.CONTACTFIRSTNAME"));
        sourceColumnMapping.setColumn("PUBLIC.EMPLOYEES.LASTNAME",
                sn.convertToColumn("PUBLIC.CUSTOMERS.CONTACTLASTNAME"));

        assertEquals("[]", sourceColumnMapping.getUnmappedPaths().toString());
        assertTrue(sourceColumnMapping.isSatisfied());

        AnalysisJob job = reader.read(new FileInputStream(new File("src/test/resources/example-job-valid.xml")),
                sourceColumnMapping);

        assertEquals("another datastore name", job.getDatastore().getName());
        assertEquals("[MetaModelInputColumn[PUBLIC.CUSTOMERS.CONTACTFIRSTNAME], "
                + "MetaModelInputColumn[PUBLIC.CUSTOMERS.CONTACTLASTNAME], "
                + "MetaModelInputColumn[PUBLIC.CUSTOMERS.PHONE]]", job.getSourceColumns().toString());

        AnalysisRunner runner = new AnalysisRunnerImpl(conf);
        AnalysisResultFuture resultFuture = runner.run(job);
        if (!resultFuture.isSuccessful()) {
            throw resultFuture.getErrors().get(0);
        }

        AnalyzerResult res = resultFuture.getResults().get(0);
        assertTrue(res instanceof StringAnalyzerResult);

        String[] resultLines = new CrosstabTextRenderer().render((CrosstabResult) res).split("\n");
        assertEquals(
                "                                              username           domain CONTACTFIRSTNAME  CONTACTLASTNAME ",
                resultLines[0]);
        assertEquals(
                "Row count                                          122              122              122              122 ",
                resultLines[1]);
        assertEquals(
                "Null count                                         122              122                0                0 ",
                resultLines[2]);
    }

    public void testReadVariables() throws Exception {
        CsvDatastore datastore = new CsvDatastore("date-datastore", "src/test/resources/example-dates.csv");
        AnalyzerBeansConfiguration configuration = new AnalyzerBeansConfigurationImpl().replace(
                new DatastoreCatalogImpl(datastore)).replace(descriptorProvider);
        JaxbJobReader reader = new JaxbJobReader(configuration);
        File file = new File("src/test/resources/example-job-variables.xml");
        assertTrue(file.exists());
        AnalysisJobBuilder ajb = reader.create(file);

        List<TransformerJobBuilder<?>> tjbs = ajb.getTransformerJobBuilders();

        DateMaskMatcherTransformer dateMaskMatcherTransformer = (DateMaskMatcherTransformer) tjbs.get(0)
                .getConfigurableBean();
        assertEquals("[yyyy-MM-dd]", Arrays.toString(dateMaskMatcherTransformer.getDateMasks()));

        ConvertToDateTransformer convertToDateTransformer = (ConvertToDateTransformer) tjbs.get(1)
                .getConfigurableBean();
        assertEquals("[yyyy-MM-dd]", Arrays.toString(convertToDateTransformer.getDateMasks()));
        assertEquals("2000-01-01",
                new SimpleDateFormat("yyyy-MM-dd").format(convertToDateTransformer.getNullReplacement()));
    }
}
