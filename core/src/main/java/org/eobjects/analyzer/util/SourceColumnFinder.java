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
package org.eobjects.analyzer.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eobjects.analyzer.data.ExpressionBasedInputColumn;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.job.AnalysisJob;
import org.eobjects.analyzer.job.InputColumnSinkJob;
import org.eobjects.analyzer.job.InputColumnSourceJob;
import org.eobjects.analyzer.job.Outcome;
import org.eobjects.analyzer.job.OutcomeSinkJob;
import org.eobjects.analyzer.job.OutcomeSourceJob;
import org.eobjects.analyzer.job.builder.AnalysisJobBuilder;
import org.eobjects.analyzer.job.builder.SourceColumns;
import org.apache.metamodel.schema.Column;
import org.apache.metamodel.schema.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for traversing dependencies between virtual and physical
 * columns.
 * 
 * @author Kasper Sørensen
 */
public class SourceColumnFinder {

    private static final String LOG_MESSAGE_RECURSIVE_TRAVERSAL = "Ending traversal of object graph because the same originating objects are appearing recursively";

    private static final Logger logger = LoggerFactory.getLogger(SourceColumnFinder.class);

    private Set<InputColumnSinkJob> _inputColumnSinks = new HashSet<InputColumnSinkJob>();
    private Set<InputColumnSourceJob> _inputColumnSources = new HashSet<InputColumnSourceJob>();
    private Set<OutcomeSourceJob> _outcomeSources = new HashSet<OutcomeSourceJob>();
    private Set<OutcomeSinkJob> _outcomeSinks = new HashSet<OutcomeSinkJob>();

    private void addSources(Object... sources) {
        for (Object source : sources) {
            if (source instanceof InputColumnSinkJob) {
                _inputColumnSinks.add((InputColumnSinkJob) source);
            }
            if (source instanceof InputColumnSourceJob) {
                _inputColumnSources.add((InputColumnSourceJob) source);
            }
            if (source instanceof OutcomeSourceJob) {
                _outcomeSources.add((OutcomeSourceJob) source);
            }
            if (source instanceof OutcomeSinkJob) {
                _outcomeSinks.add((OutcomeSinkJob) source);
            }
        }
    }

    private void addSources(Collection<?> sources) {
        addSources(sources.toArray());
    }

    public void addSources(AnalysisJobBuilder job) {
        addSources(new SourceColumns(job.getSourceColumns()));
        addSources(job.getFilterJobBuilders());
        addSources(job.getTransformerJobBuilders());
        addSources(job.getMergedOutcomeJobBuilders());
        addSources(job.getAnalyzerJobBuilders());
    }

    public void addSources(AnalysisJob job) {
        addSources(new SourceColumns(job.getSourceColumns()));
        addSources(job.getFilterJobs());
        addSources(job.getTransformerJobs());
        addSources(job.getMergedOutcomeJobs());
        addSources(job.getAnalyzerJobs());
    }

    public List<InputColumn<?>> findInputColumns(Class<?> dataType) {
        return findInputColumns(null, dataType);
    }

    /**
     * @deprecated use {@link #findInputColumns(Class)} instead.
     */
    @Deprecated
    public List<InputColumn<?>> findInputColumns(org.eobjects.analyzer.data.DataTypeFamily dataTypeFamily,
            Class<?> dataType) {
        if (dataTypeFamily == null) {
            dataTypeFamily = org.eobjects.analyzer.data.DataTypeFamily.UNDEFINED;
        }

        List<InputColumn<?>> result = new ArrayList<InputColumn<?>>();
        for (InputColumnSourceJob source : _inputColumnSources) {
            InputColumn<?>[] outputColumns = source.getOutput();
            for (InputColumn<?> col : outputColumns) {
                final org.eobjects.analyzer.data.DataTypeFamily columnFamily = col.getDataTypeFamily();
                if (columnFamily == dataTypeFamily
                        || dataTypeFamily == org.eobjects.analyzer.data.DataTypeFamily.UNDEFINED) {
                    final Class<?> columnDataType = col.getDataType();
                    if (dataType == null || columnDataType == null) {
                        result.add(col);
                    } else {
                        if (ReflectionUtils.is(columnDataType, dataType)) {
                            result.add(col);
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Finds all source jobs/components for a particular job/component. This
     * method uses {@link Object} as types because input and output can be quite
     * polymorphic. Typically {@link InputColumnSinkJob},
     * {@link InputColumnSourceJob}, {@link OutcomeSinkJob} and
     * {@link OutcomeSourceJob} implementations are used.
     * 
     * @param job
     *            typically some {@link InputColumnSinkJob}
     * @return a list of jobs/components that are a source of this job.
     */
    public Set<Object> findAllSourceJobs(Object job) {
        final Set<Object> result = new HashSet<Object>();
        findAllSourceJobs(job, result);
        return result;
    }

    private void findAllSourceJobs(Object job, Set<Object> result) {
        if (job instanceof InputColumnSinkJob) {
            final InputColumn<?>[] inputColumns = ((InputColumnSinkJob) job).getInput();
            for (final InputColumn<?> inputColumn : inputColumns) {
                final InputColumnSourceJob source = findInputColumnSource(inputColumn);
                if (source != null) {
                    result.add(source);
                    findAllSourceJobs(source, result);
                }
            }
        }

        if (job instanceof OutcomeSinkJob) {
            final Outcome[] requirements = ((OutcomeSinkJob) job).getRequirements();
            for (final Outcome outcome : requirements) {
                OutcomeSourceJob source = findOutcomeSource(outcome);
                if (source != null) {
                    result.add(source);
                    findAllSourceJobs(source, result);
                }
            }
        }
    }

    public InputColumnSourceJob findInputColumnSource(InputColumn<?> inputColumn) {
        if (inputColumn instanceof ExpressionBasedInputColumn) {
            return null;
        }
        for (InputColumnSourceJob source : _inputColumnSources) {
            InputColumn<?>[] output = source.getOutput();
            for (InputColumn<?> column : output) {
                if (inputColumn.equals(column)) {
                    return source;
                }
            }
        }
        return null;
    }

    public OutcomeSourceJob findOutcomeSource(Outcome requirement) {
        for (OutcomeSourceJob source : _outcomeSources) {
            Outcome[] outcomes = source.getOutcomes();
            for (Outcome outcome : outcomes) {
                if (requirement.equals(outcome)) {
                    return source;
                }
            }
        }
        return null;
    }

    public Set<Column> findOriginatingColumns(Outcome requirement) {
        OutcomeSourceJob source = findOutcomeSource(requirement);

        HashSet<Column> result = new HashSet<Column>();
        findOriginatingColumnsOfSource(source, result);
        return result;
    }

    public Table findOriginatingTable(Outcome requirement) {
        return findOriginatingTable(requirement, new HashSet<Object>());
    }

    private Table findOriginatingTable(Outcome requirement, Set<Object> resolvedSet) {
        OutcomeSourceJob source = findOutcomeSource(requirement);
        if (!resolvedSet.add(source)) {
            logger.info(LOG_MESSAGE_RECURSIVE_TRAVERSAL);
            return null;
        }
        return findOriginatingTableOfSource(source, resolvedSet);
    }

    public Table findOriginatingTable(InputColumn<?> inputColumn) {
        return findOriginatingTable(inputColumn, new HashSet<Object>());
    }

    private Table findOriginatingTable(InputColumn<?> inputColumn, Set<Object> resolvedSet) {
        if (!resolvedSet.add(inputColumn)) {
            logger.info(LOG_MESSAGE_RECURSIVE_TRAVERSAL);
            return null;
        }

        if (inputColumn == null) {
            logger.warn("InputColumn was null, no originating table found");
            return null;
        }
        if (inputColumn.isPhysicalColumn()) {
            return inputColumn.getPhysicalColumn().getTable();
        }

        final InputColumnSourceJob inputColumnSource = findInputColumnSource(inputColumn);
        if (!resolvedSet.add(inputColumnSource)) {
            logger.info(LOG_MESSAGE_RECURSIVE_TRAVERSAL);
            return null;
        }

        return findOriginatingTableOfSource(inputColumnSource, resolvedSet);
    }

    private Table findOriginatingTableOfSource(Object source, Set<Object> resolvedSet) {
        final Set<Table> result = new TreeSet<Table>();
        if (source instanceof InputColumnSinkJob) {
            InputColumn<?>[] input = ((InputColumnSinkJob) source).getInput();
            if (input != null) {
                for (InputColumn<?> col : input) {
                    if (col == null) {
                        logger.warn("InputColumn sink had a null-column element!");
                    } else {
                        Table table = findOriginatingTable(col, resolvedSet);
                        if (table != null) {
                            result.add(table);
                        }
                    }
                }
            }
        }
        if (source instanceof OutcomeSinkJob) {
            Outcome[] requirements = ((OutcomeSinkJob) source).getRequirements();
            if (requirements != null) {
                for (Outcome outcome : requirements) {
                    Table table = findOriginatingTable(outcome, resolvedSet);
                    if (table != null) {
                        result.add(table);
                    }
                }
            }
        }

        if (result.isEmpty()) {
            return null;
        }
        if (result.size() == 1) {
            return result.iterator().next();
        }
        StringBuilder sb = new StringBuilder();
        for (Table table : result) {
            if (sb.length() != 0) {
                sb.append(", ");
            }
            sb.append(table.getName());
        }
        throw new IllegalStateException("Multiple originating tables (" + sb + ") found for source: " + source);
    }

    private void findOriginatingColumnsOfInputColumn(InputColumn<?> inputColumn, Set<Column> result) {
        if (inputColumn == null) {
            return;
        }
        if (inputColumn.isPhysicalColumn()) {
            result.add(inputColumn.getPhysicalColumn());
        } else {
            InputColumnSourceJob source = findInputColumnSource(inputColumn);
            findOriginatingColumnsOfSource(source, result);
        }
    }

    private void findOriginatingColumnsOfOutcome(Outcome requirement, Set<Column> result) {
        OutcomeSourceJob source = findOutcomeSource(requirement);
        findOriginatingColumnsOfSource(source, result);
    }

    private void findOriginatingColumnsOfSource(Object source, Set<Column> result) {
        if (source == null) {
            return;
        }
        if (source instanceof InputColumnSinkJob) {
            InputColumn<?>[] input = ((InputColumnSinkJob) source).getInput();
            if (input != null) {
                for (InputColumn<?> inputColumn : input) {
                    findOriginatingColumnsOfInputColumn(inputColumn, result);
                }
            }
        }
        if (source instanceof OutcomeSinkJob) {
            Outcome[] requirements = ((OutcomeSinkJob) source).getRequirements();
            for (Outcome outcome : requirements) {
                findOriginatingColumnsOfOutcome(outcome, result);
            }
        }
    }

    public Set<Column> findOriginatingColumns(InputColumn<?> inputColumn) {
        Set<Column> result = new HashSet<Column>();

        // TODO: Detect cyclic dependencies between transformers (A depends on
        // B, B depends on A)

        findOriginatingColumnsOfInputColumn(inputColumn, result);
        return result;
    }
}
