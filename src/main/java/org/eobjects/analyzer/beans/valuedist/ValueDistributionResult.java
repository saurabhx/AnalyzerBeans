package org.eobjects.analyzer.beans.valuedist;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.result.AnalyzerResult;

public class ValueDistributionResult implements AnalyzerResult {

	private static final long serialVersionUID = 1L;
	private ValueCountList _topValues;
	private ValueCountList _bottomValues;
	private int _nullCount;
	private Collection<String> _uniqueValues;
	private int _uniqueValueCount;
	private String _columnName;

	private ValueDistributionResult(InputColumn<?> column,
			ValueCountList topValues, ValueCountList bottomValues, int nullCount) {
		_columnName = column.getName();
		_topValues = topValues;
		_bottomValues = bottomValues;
		_nullCount = nullCount;
	}

	public ValueDistributionResult(InputColumn<?> column,
			ValueCountList topValues, ValueCountListImpl bottomValues,
			int nullCount, Collection<String> uniqueValues) {
		this(column, topValues, bottomValues, nullCount);
		_uniqueValues = uniqueValues;
	}

	public ValueDistributionResult(InputColumn<?> column,
			ValueCountList topValues, ValueCountListImpl bottomValues,
			int nullCount, int uniqueValueCount) {
		this(column, topValues, bottomValues, nullCount);
		_uniqueValueCount = uniqueValueCount;
	}

	@Override
	public Class<ValueDistributionAnalyzer> getProducerClass() {
		return ValueDistributionAnalyzer.class;
	}

	public ValueCountList getTopValues() {
		return _topValues;
	}

	public ValueCountList getBottomValues() {
		return _bottomValues;
	}

	public int getNullCount() {
		return _nullCount;
	}

	public int getUniqueCount() {
		if (_uniqueValues != null) {
			return _uniqueValues.size();
		}
		return _uniqueValueCount;
	}

	public Collection<String> getUniqueValues() {
		return Collections.unmodifiableCollection(_uniqueValues);
	}

	public String getColumnName() {
		return _columnName;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Value distribution for column: ");
		sb.append(_columnName);
		sb.append('\n');

		if (_topValues != null && _topValues.getActualSize() > 0) {
			sb.append("Top values:");
			List<ValueCount> valueCounts = _topValues.getValueCounts();
			for (ValueCount valueCount : valueCounts) {
				sb.append("\n - ");
				sb.append(valueCount.getValue());
				sb.append(": ");
				sb.append(valueCount.getCount());
			}
		}

		if (_bottomValues != null && _bottomValues.getActualSize() > 0) {
			sb.append("Bottom values:");
			List<ValueCount> valueCounts = _bottomValues.getValueCounts();
			for (ValueCount valueCount : valueCounts) {
				sb.append("\n - ");
				sb.append(valueCount.getValue());
				sb.append(": ");
				sb.append(valueCount.getCount());
			}
		}

		sb.append("\nNull count: ");
		sb.append(_nullCount);

		sb.append("\nUnique values: ");
		if (_uniqueValues == null) {
			sb.append(_uniqueValueCount);
		} else {
			for (String value : _uniqueValues) {
				sb.append("\n - ");
				sb.append(value);
			}
		}
		return sb.toString();
	}
}
