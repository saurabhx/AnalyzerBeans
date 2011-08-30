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
package org.eobjects.analyzer.beans.convert;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.eobjects.analyzer.beans.api.Categorized;
import org.eobjects.analyzer.beans.api.Configured;
import org.eobjects.analyzer.beans.api.Description;
import org.eobjects.analyzer.beans.api.Initialize;
import org.eobjects.analyzer.beans.api.OutputColumns;
import org.eobjects.analyzer.beans.api.Transformer;
import org.eobjects.analyzer.beans.api.TransformerBean;
import org.eobjects.analyzer.beans.categories.ConversionCategory;
import org.eobjects.analyzer.beans.categories.DateAndTimeCategory;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.InputRow;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Attempts to convert anything to a Date value
 * 
 * @author Kasper Sørensen
 */
@TransformerBean("Convert to date")
@Description("Converts anything to a date (or null).")
@Categorized({ ConversionCategory.class, DateAndTimeCategory.class })
public class ConvertToDateTransformer implements Transformer<Date> {

	private static final String[] prototypePatterns = { "yyyy-MM-dd", "dd-MM-yyyy", "MM-dd-yyyy" };

	private static final DateTimeFormatter NUMBER_BASED_DATE_FORMAT_LONG = DateTimeFormat.forPattern("yyyyMMdd");
	private static final DateTimeFormatter NUMBER_BASED_DATE_FORMAT_SHORT = DateTimeFormat.forPattern("yyMMdd");

	private static ConvertToDateTransformer internalInstance;

	@Inject
	@Configured(order = 1)
	InputColumn<?> input;

	@Inject
	@Configured(required = false, order = 2)
	Date nullReplacement;

	@Inject
	@Configured(required = false, order = 3)
	String[] dateMasks;

	private DateTimeFormatter[] _dateTimeFormatters;

	public static ConvertToDateTransformer getInternalInstance() {
		if (internalInstance == null) {
			internalInstance = new ConvertToDateTransformer();
			internalInstance.init();
		}
		return internalInstance;
	}

	public ConvertToDateTransformer() {
		dateMasks = getDefaultDateMasks();
	}

	@Initialize
	public void init() {
		if (dateMasks == null) {
			dateMasks = getDefaultDateMasks();
		}
		_dateTimeFormatters = new DateTimeFormatter[dateMasks.length];
		for (int i = 0; i < dateMasks.length; i++) {
			String dateMask = dateMasks[i];
			_dateTimeFormatters[i] = DateTimeFormat.forPattern(dateMask);
		}
	}

	@Override
	public OutputColumns getOutputColumns() {
		return new OutputColumns(input.getName() + " (as date)");
	}

	@Override
	public Date[] transform(InputRow inputRow) {
		Object value = inputRow.getValue(input);
		Date d = transformValue(value);
		if (d == null) {
			d = nullReplacement;
		}
		return new Date[] { d };
	}

	public Date transformValue(Object value) {
		Date d = null;
		if (value != null) {
			if (value instanceof Date) {
				d = (Date) value;
			} else if (value instanceof Calendar) {
				d = ((Calendar) value).getTime();
			} else if (value instanceof String) {
				d = convertFromString((String) value);
			} else if (value instanceof Number) {
				d = convertFromNumber((Number) value);
			}
		}
		return d;
	}

	protected Date convertFromString(String value) {
		try {
			long longValue = Long.parseLong(value);
			return convertFromNumber(longValue);
		} catch (NumberFormatException e) {
			// do nothing, proceed to dateFormat parsing
		}

		for (DateTimeFormatter formatter : _dateTimeFormatters) {
			try {
				return formatter.parseDateTime(value).toDate();
			} catch (Exception e) {
				// proceed to next formatter
			}
		}

		return null;
	}

	protected Date convertFromNumber(Number value) {
		Number numberValue = (Number) value;
		long longValue = numberValue.longValue();

		String stringValue = Long.toString(longValue);
		// test if the number is actually a format of the type yyyyMMdd
		if (stringValue.length() == 8 && (stringValue.startsWith("1") || stringValue.startsWith("2"))) {
			try {
				return NUMBER_BASED_DATE_FORMAT_LONG.parseDateTime(stringValue).toDate();
			} catch (Exception e) {
				// do nothing, proceed to next method of conversion
			}
		}

		// test if the number is actually a format of the type yyMMdd
		if (stringValue.length() == 6) {
			try {
				return NUMBER_BASED_DATE_FORMAT_SHORT.parseDateTime(stringValue).toDate();
			} catch (Exception e) {
				// do nothing, proceed to next method of conversion
			}
		}

		if (longValue > 5000000) {
			// this number is most probably amount of milliseconds since
			// 1970
			return new Date(longValue);
		} else {
			// this number is most probably the amount of days since
			// 1970
			return new Date(longValue * 1000 * 60 * 60 * 24);
		}
	}

	private String[] getDefaultDateMasks() {
		final List<String> defaultDateMasks = new ArrayList<String>();

		defaultDateMasks.add("yyyy-MM-dd HH:mm:ss.S");
		defaultDateMasks.add("yyyy-MM-dd HH:mm:ss");
		defaultDateMasks.add("yyyyMMddHHmmssZ");
		defaultDateMasks.add("yyMMddHHmmssZ");

		for (String string : prototypePatterns) {
			defaultDateMasks.add(string);
			string = string.replaceAll("\\-", "\\.");
			defaultDateMasks.add(string);
			string = string.replaceAll("\\.", "\\/");
			defaultDateMasks.add(string);
		}

		return defaultDateMasks.toArray(new String[defaultDateMasks.size()]);
	}
	
	public String[] getDateMasks() {
		return dateMasks;
	}
	
	public Date getNullReplacement() {
		return nullReplacement;
	}
}