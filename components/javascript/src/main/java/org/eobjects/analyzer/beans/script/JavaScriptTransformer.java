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
package org.eobjects.analyzer.beans.script;

import org.eobjects.analyzer.beans.api.Alias;
import org.eobjects.analyzer.beans.api.Categorized;
import org.eobjects.analyzer.beans.api.Configured;
import org.eobjects.analyzer.beans.api.Description;
import org.eobjects.analyzer.beans.api.Initialize;
import org.eobjects.analyzer.beans.api.OutputColumns;
import org.eobjects.analyzer.beans.api.StringProperty;
import org.eobjects.analyzer.beans.api.Transformer;
import org.eobjects.analyzer.beans.api.TransformerBean;
import org.eobjects.analyzer.beans.categories.ScriptingCategory;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.InputRow;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A transformer that uses userwritten JavaScript to generate a value
 */
@TransformerBean("JavaScript transformer (simple)")
@Alias("JavaScript transformer")
@Description("Supply your own piece of JavaScript to do a custom transformation")
@Categorized({ ScriptingCategory.class })
public class JavaScriptTransformer implements Transformer<Object> {

	private static final Logger logger = LoggerFactory
			.getLogger(JavaScriptTransformer.class);

	public static enum ReturnType {
		STRING, NUMBER, BOOLEAN;
	}

	@Configured
	InputColumn<?>[] columns;

	@Configured
	ReturnType returnType = ReturnType.STRING;

	@Configured
	@Description("Available variables:\nvalues[0..]: Array of values\nvalues[\"my_col\"]: Map of values\nmy_col: Each column value has it's own variable\nout: Print to console using out.println('hello')\nlog: Print to log using log.info(...), log.warn(...), log.error(...)")
	@StringProperty(multiline = true, mimeType = { "text/javascript",
			"application/x-javascript" })
	String sourceCode = "function eval() {\n\treturn \"hello \" + values[0];\n}\n\neval();";

	private ContextFactory _contextFactory;
	private Script _script;

	// this scope is shared between all threads
	private ScriptableObject _sharedScope;

	@Override
	public OutputColumns getOutputColumns() {
		OutputColumns outputColumns = new OutputColumns("JavaScript output");
		if (returnType == ReturnType.NUMBER) {
			outputColumns.setColumnType(0, Number.class);
		} else if (returnType == ReturnType.BOOLEAN) {
			outputColumns.setColumnType(0, Boolean.class);
		} else {
			outputColumns.setColumnType(0, String.class);
		}
		return outputColumns;
	}

	@Initialize
	public void init() {
		_contextFactory = new ContextFactory();
		Context context = _contextFactory.enterContext();

		try {
			_script = context.compileString(sourceCode, this.getClass()
					.getSimpleName(), 1, null);
			_sharedScope = context.initStandardObjects();

			JavaScriptUtils.addToScope(_sharedScope, logger, "logger", "log");
			JavaScriptUtils.addToScope(_sharedScope, System.out, "out");
		} finally {
			Context.exit();
		}
	}

	@Override
	public Object[] transform(InputRow inputRow) {
		Context context = _contextFactory.enterContext();

		try {

			// this scope is local to the execution of a single row
			Scriptable scope = context.newObject(_sharedScope);
			scope.setPrototype(_sharedScope);
			scope.setParentScope(null);

			JavaScriptUtils.addToScope(scope, inputRow, columns, "values");

			Object result = _script.exec(context, scope);
			// String stringResult = Context.toString(result);

			if (result == null) {
				result = null;
			} else if (returnType == ReturnType.NUMBER) {
				result = Context.toNumber(result);
			} else if (returnType == ReturnType.BOOLEAN) {
				result = Context.toBoolean(result);
			} else {
				result = Context.toString(result);
			}
			return new Object[] { result };
		} finally {
			Context.exit();
		}
	}

	public void setSourceCode(String sourceCode) {
		this.sourceCode = sourceCode;
	}

	public void setColumns(InputColumn<?>[] columns) {
		this.columns = columns;
	}
}
