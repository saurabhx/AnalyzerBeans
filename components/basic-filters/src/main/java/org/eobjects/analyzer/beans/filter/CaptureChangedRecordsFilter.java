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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Properties;

import org.eobjects.analyzer.beans.api.Categorized;
import org.eobjects.analyzer.beans.api.Close;
import org.eobjects.analyzer.beans.api.Configured;
import org.eobjects.analyzer.beans.api.Description;
import org.eobjects.analyzer.beans.api.Distributed;
import org.eobjects.analyzer.beans.api.FileProperty;
import org.eobjects.analyzer.beans.api.FileProperty.FileAccessMode;
import org.eobjects.analyzer.beans.api.FilterBean;
import org.eobjects.analyzer.beans.api.Initialize;
import org.eobjects.analyzer.beans.api.Optimizeable;
import org.eobjects.analyzer.beans.api.QueryOptimizedFilter;
import org.eobjects.analyzer.beans.categories.DateAndTimeCategory;
import org.eobjects.analyzer.beans.categories.FilterCategory;
import org.eobjects.analyzer.beans.convert.ConvertToDateTransformer;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.InputRow;
import org.eobjects.analyzer.util.StringUtils;
import org.apache.metamodel.query.OperatorType;
import org.apache.metamodel.query.Query;
import org.apache.metamodel.schema.Column;
import org.apache.metamodel.schema.Table;
import org.apache.metamodel.util.Action;
import org.apache.metamodel.util.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter for archieving a "change data capture" mechanism based on a
 * "last modified" field. After each execution, the greatest timestamp is
 * recorded and picked up successively by the next run.
 */
@FilterBean("Capture changed records")
@Description("Include only records that have changed since the last time you ran the job. This filter assumes a field containing the timestamp of the latest change for each record, and stores the greatest encountered value in order to update the filter's future state.")
@Distributed(false)
@Categorized({ FilterCategory.class, DateAndTimeCategory.class })
@Optimizeable(removeableUponOptimization = false)
public class CaptureChangedRecordsFilter implements QueryOptimizedFilter<ValidationCategory> {

    private static final Logger logger = LoggerFactory.getLogger(CaptureChangedRecordsFilter.class);

    @Configured
    @Description("Column containing the last modification timestamp or date.")
    InputColumn<Object> lastModifiedColumn;

    @Configured
    @Description("A file used to persist and load the latest state of this data capture component.")
    @FileProperty(extension = "properties", accessMode = FileAccessMode.SAVE)
    Resource captureStateFile;

    @Configured(required = false)
    @Description("A custom identifier for this captured state. If omitted, the name of the 'Last modified column' will be used.")
    String captureStateIdentifier;

    private Date _lastModifiedThreshold;
    private Date _greatestEncounteredDate;

    @Initialize
    public void initialize() throws IOException {
        final Properties properties = loadProperties();
        final String key = getPropertyKey();
        final String lastModified = properties.getProperty(key);
        final Date date = ConvertToDateTransformer.getInternalInstance().transformValue(lastModified);
        _lastModifiedThreshold = date;
    }

    @Override
    public boolean isOptimizable(final ValidationCategory category) {
        // only the valid category is optimizeable currently
        return category == ValidationCategory.VALID;
    }

    @Override
    public Query optimizeQuery(final Query q, final ValidationCategory category) {
        assert category == ValidationCategory.VALID;

        if (_lastModifiedThreshold != null) {
            final Column column = lastModifiedColumn.getPhysicalColumn();
            if (column.getType().isNumber()) {
                final long timestamp = _lastModifiedThreshold.getTime();
                q.where(column, OperatorType.GREATER_THAN, timestamp);
            } else {
                q.where(column, OperatorType.GREATER_THAN, _lastModifiedThreshold);
            }
        }

        return q;
    }

    @Close(onFailure = false)
    public void close() throws IOException {
        if (_greatestEncounteredDate != null) {
            final Properties properties = loadProperties();
            final String key = getPropertyKey();
            properties.setProperty(key, "" + _greatestEncounteredDate.getTime());

            captureStateFile.write(new Action<OutputStream>() {
                @Override
                public void run(OutputStream out) throws Exception {
                    properties.store(out, null);
                }
            });
        }
    }

    /**
     * Gets the key to use in the capture state file. If there is not a
     * captureStateIdentifier available, we want to avoid using a hardcoded key,
     * since the same file may be used for multiple purposes, even multiple
     * filters of the same type. Of course this is not desired configuration,
     * but may be more convenient for lazy users!
     * 
     * @return
     */
    private String getPropertyKey() {
        if (StringUtils.isNullOrEmpty(captureStateIdentifier)) {
            if (lastModifiedColumn.isPhysicalColumn()) {
                Table table = lastModifiedColumn.getPhysicalColumn().getTable();
                if (table != null && !StringUtils.isNullOrEmpty(table.getName())) {
                    return table.getName() + "." + lastModifiedColumn.getName() + ".GreatestLastModifiedTimestamp";
                }
            }
            return lastModifiedColumn.getName() + ".GreatestLastModifiedTimestamp";
        }
        return captureStateIdentifier.trim() + ".GreatestLastModifiedTimestamp";
    }

    private Properties loadProperties() throws IOException {
        final Properties properties = new Properties();
        if (!captureStateFile.isExists()) {
            logger.info("Capture state file does not exist: {}", captureStateFile);
            return properties;
        }

        captureStateFile.read(new Action<InputStream>() {
            @Override
            public void run(InputStream in) throws Exception {
                properties.load(in);
            }
        });
        return properties;
    }

    @Override
    public ValidationCategory categorize(InputRow inputRow) {
        final Object lastModified = inputRow.getValue(lastModifiedColumn);
        final Date date = ConvertToDateTransformer.getInternalInstance().transformValue(lastModified);

        if (date != null) {
            synchronized (this) {
                if (_greatestEncounteredDate == null || _greatestEncounteredDate.before(date)) {
                    _greatestEncounteredDate = date;
                }
            }
        }

        if (_lastModifiedThreshold == null) {
            return ValidationCategory.VALID;
        }

        if (date == null) {
            logger.info("Date value of {} was null, returning INVALID category: {}", lastModifiedColumn.getName(),
                    inputRow);
            return ValidationCategory.INVALID;
        }

        if (_lastModifiedThreshold.before(date)) {
            return ValidationCategory.VALID;
        }
        return ValidationCategory.INVALID;
    }
}
