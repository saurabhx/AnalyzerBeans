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
package org.eobjects.analyzer.beans.transform;

import org.eobjects.analyzer.beans.api.Categorized;
import org.eobjects.analyzer.beans.api.Configured;
import org.eobjects.analyzer.beans.api.Description;
import org.eobjects.analyzer.beans.api.OutputColumns;
import org.eobjects.analyzer.beans.api.Transformer;
import org.eobjects.analyzer.beans.api.TransformerBean;
import org.eobjects.analyzer.beans.categories.MatchingAndStandardizationCategory;
import org.eobjects.analyzer.beans.convert.ConvertToStringTransformer;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.InputRow;
import org.eobjects.analyzer.reference.StringPattern;

@TransformerBean("String pattern matcher")
@Description("Matches string values against a set of string patterns, producing a corresponding set of output columns specifying whether or not the values matched those string patterns")
@Categorized({ MatchingAndStandardizationCategory.class })
public class StringPatternMatcherTransformer implements Transformer<Object> {

	@Configured
	StringPattern[] _stringPatterns;

	@Configured
	InputColumn<?> _column;

	@Configured
	MatchOutputType _outputType = MatchOutputType.TRUE_FALSE;

	public StringPatternMatcherTransformer(InputColumn<?> column, StringPattern[] stringPatterns) {
		this();
		_column = column;
		_stringPatterns = stringPatterns;
	}

	public StringPatternMatcherTransformer() {
	}

	@Override
	public OutputColumns getOutputColumns() {
		String columnName = _column.getName();
		String[] names = new String[_stringPatterns.length];
		for (int i = 0; i < names.length; i++) {
			names[i] = columnName + " '" + _stringPatterns[i].getName() + "'";
		}
		Class<?>[] types = new Class[_stringPatterns.length];
		for (int i = 0; i < types.length; i++) {
			types[i] = _outputType.getOutputClass();
		}
		return new OutputColumns(names, types);
	}

	@Override
	public Object[] transform(InputRow inputRow) {
		Object value = inputRow.getValue(_column);
		Object[] result = doMatching(value);
		return result;
	}

	public Object[] doMatching(Object value) {
		Object[] result = new Object[_stringPatterns.length];
		String stringValue = ConvertToStringTransformer.transformValue(value);

		for (int i = 0; i < result.length; i++) {
			boolean matches = _stringPatterns[i].matches(stringValue);
			if (_outputType == MatchOutputType.TRUE_FALSE) {
				result[i] = matches;
			} else if (_outputType == MatchOutputType.INPUT_OR_NULL) {
				if (matches) {
					result[i] = stringValue;
				} else {
					result[i] = null;
				}
			}
		}
		return result;
	}

	public void setStringPatterns(StringPattern[] stringPatterns) {
		_stringPatterns = stringPatterns;
	}

	public void setColumn(InputColumn<?> column) {
		_column = column;
	}
}