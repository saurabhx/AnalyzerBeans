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

import java.lang.annotation.Annotation;

import org.eobjects.analyzer.configuration.InjectionPoint;
import org.eobjects.analyzer.descriptors.PropertyDescriptor;

/**
 * {@link InjectionPoint} implementation for {@link PropertyDescriptor}s
 * 
 * @author Kasper Sørensen
 */
public final class PropertyInjectionPoint implements InjectionPoint<Object> {

	private final PropertyDescriptor _descriptor;
	private final Object _instance;

	public PropertyInjectionPoint(PropertyDescriptor descriptor, Object instance) {
		_descriptor = descriptor;
		_instance = instance;
	}

	@Override
	public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
		return _descriptor.getAnnotation(annotationClass);
	}

	@Override
	public Object getInstance() {
		return _instance;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<Object> getBaseType() {
		return (Class<Object>) _descriptor.getType();
	}

	@Override
	public boolean isGenericType() {
		return _descriptor.getTypeArgumentCount() > 0;
	}

	@Override
	public int getGenericTypeArgumentCount() {
		return _descriptor.getTypeArgumentCount();
	}

	@Override
	public Class<?> getGenericTypeArgument(int i) throws IndexOutOfBoundsException {
		return _descriptor.getTypeArgument(i);
	}

	@Override
	public String toString() {
	    return "PropertyInjectionPoint[" + _descriptor + "]";
	}
}
