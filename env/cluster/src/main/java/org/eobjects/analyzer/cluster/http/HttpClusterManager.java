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
package org.eobjects.analyzer.cluster.http;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.eobjects.analyzer.cluster.ClusterManager;
import org.eobjects.analyzer.cluster.DistributedJobContext;
import org.eobjects.analyzer.cluster.FixedDivisionsCountJobDivisionManager;
import org.eobjects.analyzer.cluster.JobDivisionManager;
import org.eobjects.analyzer.cluster.LazyRefAnalysisResultFuture;
import org.eobjects.analyzer.job.AnalysisJob;
import org.eobjects.analyzer.job.JaxbJobWriter;
import org.eobjects.analyzer.job.runner.AnalysisResultFuture;
import org.eobjects.analyzer.result.AnalysisResult;
import org.eobjects.analyzer.util.ChangeAwareObjectInputStream;
import org.apache.metamodel.util.Action;
import org.apache.metamodel.util.FileHelper;
import org.apache.metamodel.util.LazyRef;

/**
 * A cluster manager that uses HTTP servlet transport to communicate between
 * nodes.
 */
public class HttpClusterManager implements ClusterManager {

    private final HttpClient _httpClient;
    private final List<String> _slaveEndpoints;

    /**
     * Creates a new HTTP cluster manager
     * 
     * @param slaveEndpoints
     *            the endpoint URLs of the slaves
     */
    public HttpClusterManager(List<String> slaveEndpoints) {
        this(new DefaultHttpClient(new PoolingClientConnectionManager()), slaveEndpoints);
    }

    /**
     * Create a new HTTP cluster manager
     * 
     * @param httpClient
     *            http client to use for invoking slave endpoints. Must be
     *            capable of executing multiple requests at the same time (see
     *            {@link PoolingClientConnectionManager}).
     * @param slaveEndpoints
     *            the endpoint URLs of the slaves
     */
    public HttpClusterManager(HttpClient httpClient, List<String> slaveEndpoints) {
        _httpClient = httpClient;
        _slaveEndpoints = slaveEndpoints;
    }

    @Override
    public JobDivisionManager getJobDivisionManager() {
        return new FixedDivisionsCountJobDivisionManager(_slaveEndpoints.size());
    }

    @Override
    public AnalysisResultFuture dispatchJob(AnalysisJob job, DistributedJobContext context) throws Exception {
        // determine endpoint url
        final int index = context.getJobDivisionIndex();
        final String slaveEndpoint = _slaveEndpoints.get(index);

        // write the job as XML
        final JaxbJobWriter jobWriter = new JaxbJobWriter(context.getMasterConfiguration());
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jobWriter.write(job, baos);
        final byte[] bytes = baos.toByteArray();

        // send the request in another thread
        final List<Throwable> errors = new LinkedList<Throwable>();
        final LazyRef<AnalysisResult> resultRef = sendRequest(slaveEndpoint, bytes, errors);
        resultRef.requestLoad(new Action<Throwable>() {
            @Override
            public void run(Throwable error) throws Exception {
                errors.add(error);
            }
        });

        return new LazyRefAnalysisResultFuture(resultRef, errors);
    }

    private LazyRef<AnalysisResult> sendRequest(final String slaveEndpoint, final byte[] bytes,
            final List<Throwable> errors) {
        return new LazyRef<AnalysisResult>() {
            @Override
            protected AnalysisResult fetch() throws Throwable {
                // send the HTTP request
                final HttpPost request = new HttpPost(slaveEndpoint);
                request.setEntity(new ByteArrayEntity(bytes));
                final HttpResponse response = _httpClient.execute(request);

                // handle the response
                final StatusLine statusLine = response.getStatusLine();
                if (statusLine.getStatusCode() != 200) {
                    throw new IllegalStateException("Slave server responded with an error: "
                            + statusLine.getReasonPhrase() + " (" + statusLine.getStatusCode() + ")");
                }

                final InputStream inputStream = response.getEntity().getContent();
                try {
                    AnalysisResult result = readResult(inputStream, errors);
                    return result;
                } finally {
                    FileHelper.safeClose(inputStream);
                }
            }
        };
    }

    protected AnalysisResult readResult(InputStream inputStream, List<Throwable> errors) throws Exception {
        final ChangeAwareObjectInputStream changeAwareObjectInputStream = new ChangeAwareObjectInputStream(inputStream);
        final Object object = changeAwareObjectInputStream.readObject();
        changeAwareObjectInputStream.close();
        if (object instanceof AnalysisResult) {
            // response carries a result
            return (AnalysisResult) object;
        } else if (object instanceof List) {
            // response carries a list of errors
            @SuppressWarnings("unchecked")
            List<Throwable> slaveErrors = (List<Throwable>) object;
            errors.addAll(slaveErrors);
            return null;
        } else {
            throw new IllegalStateException("Unexpected response payload: " + object);
        }
    }
}
