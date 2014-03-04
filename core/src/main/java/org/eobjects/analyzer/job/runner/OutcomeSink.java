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

import org.eobjects.analyzer.job.Outcome;

/**
 * Interface for RowProcessingConsumers to add outcomes to and for others to
 * detect the {@link Outcome} state of a record.
 */
public interface OutcomeSink extends Cloneable {

    /**
     * Adds a {@link Outcome} to the set of active outcomes
     * 
     * @param filterOutcome
     */
    public void add(Outcome filterOutcome);

    /**
     * Gets the currently active outcomes.
     * 
     * @return
     */
    public Outcome[] getOutcomes();

    /**
     * Determines if a particular outcome is active in the current state.
     * 
     * @param outcome
     * @return
     */
    public boolean contains(Outcome outcome);

    /**
     * Clones the instance.
     * 
     * @return
     */
    public OutcomeSink clone();
}
