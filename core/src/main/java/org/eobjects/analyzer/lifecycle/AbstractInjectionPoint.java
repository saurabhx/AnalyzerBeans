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
package org.eobjects.analyzer.lifecycle;

import java.lang.reflect.Type;

import org.eobjects.analyzer.configuration.InjectionPoint;
import org.eobjects.analyzer.util.ReflectionUtils;

public abstract class AbstractInjectionPoint<E> implements InjectionPoint<E> {

	protected abstract Type getGenericType();

	@Override
	public boolean isGenericType() {
		return getGenericTypeArgumentCount() > 0;
	}

	public int getGenericTypeArgumentCount() {
		return ReflectionUtils.getTypeParameterCount(getGenericType());
	}

	@Override
	public Class<?> getGenericTypeArgument(int i) throws IndexOutOfBoundsException {
		return ReflectionUtils.getTypeParameter(getGenericType(), i);
	}

}
