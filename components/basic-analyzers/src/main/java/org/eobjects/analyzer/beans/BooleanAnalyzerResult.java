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
package org.eobjects.analyzer.beans;

import org.eobjects.analyzer.result.AnalyzerResult;
import org.eobjects.analyzer.result.Crosstab;

public class BooleanAnalyzerResult implements AnalyzerResult {

	private static final long serialVersionUID = 1L;
	
	private final Crosstab<Number> _columnStatisticsCrosstab;
	private final Crosstab<Number> _valueCombinationCrosstab;

	public BooleanAnalyzerResult(Crosstab<Number> columnStatisticsCrosstab, Crosstab<Number> valueCombinationCrosstab) {
		_columnStatisticsCrosstab = columnStatisticsCrosstab;
		_valueCombinationCrosstab = valueCombinationCrosstab;
	}

	public Crosstab<Number> getColumnStatisticsCrosstab() {
		return _columnStatisticsCrosstab;
	}

	public Crosstab<Number> getValueCombinationCrosstab() {
		return _valueCombinationCrosstab;
	}
}