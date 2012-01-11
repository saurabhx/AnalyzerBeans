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
package org.eobjects.analyzer.job.tasks;

import org.eobjects.analyzer.descriptors.ComponentDescriptor;
import org.eobjects.analyzer.job.concurrent.TaskListener;
import org.eobjects.analyzer.lifecycle.LifeCycleHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloseBeanTaskListener implements TaskListener {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final LifeCycleHelper _lifeCycleHelper;
	private final ComponentDescriptor<?> _descriptor;
	private final Object _component;

	public CloseBeanTaskListener(LifeCycleHelper lifeCycleHelper, ComponentDescriptor<?> descriptor, Object component) {
		_lifeCycleHelper = lifeCycleHelper;
		_descriptor = descriptor;
		_component = component;
	}

	private void cleanup() {
		logger.debug("execute()");

		// close can occur AFTER completion
		_lifeCycleHelper.close(_descriptor, _component);
	}

	@Override
	public void onBegin(Task task) {
	}

	@Override
	public void onComplete(Task task) {
		cleanup();
	}

	@Override
	public void onError(Task task, Throwable throwable) {
		cleanup();
	}
}
