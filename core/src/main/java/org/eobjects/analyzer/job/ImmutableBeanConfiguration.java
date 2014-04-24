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
package org.eobjects.analyzer.job;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eobjects.analyzer.descriptors.ConfiguredPropertyDescriptor;
import org.eobjects.analyzer.descriptors.PropertyDescriptor;
import org.apache.metamodel.util.EqualsBuilder;

/**
 * Default (immutable) implementation of {@link BeanConfiguration}.
 */
public final class ImmutableBeanConfiguration implements BeanConfiguration {

    private static final long serialVersionUID = 1L;

    private final Map<PropertyDescriptor, Object> _properties;

    public ImmutableBeanConfiguration(Map<ConfiguredPropertyDescriptor, Object> properties) {
        if (properties == null) {
            _properties = new HashMap<PropertyDescriptor, Object>();
        } else {
            _properties = new HashMap<PropertyDescriptor, Object>(properties);
        }

        // validate contents
        for (Map.Entry<PropertyDescriptor, Object> entry : _properties.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Collection) {
                throw new IllegalArgumentException(
                        "Collection values are not allowed in BeanConfigurations. Violating entry: " + entry.getKey()
                                + " -> " + entry.getValue());
            }
        }
    }

    @Override
    public Object getProperty(ConfiguredPropertyDescriptor propertyDescriptor) {
        return _properties.get(propertyDescriptor);
    }

    @Override
    public String toString() {
        return "ImmutableBeanConfiguration[" + _properties + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        return prime + _properties.size() + _properties.keySet().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final ImmutableBeanConfiguration other = (ImmutableBeanConfiguration) obj;

        // since map comparison does not use deep equals for arrays, we need to
        // do this ourselves!

        final Map<PropertyDescriptor, Object> otherProperties = other._properties;
        final Set<PropertyDescriptor> configredProperties = _properties.keySet();
        if (!configredProperties.equals(otherProperties.keySet())) {
            return false;
        }

        for (final PropertyDescriptor propertyDescriptor : configredProperties) {
            final Object value1 = _properties.get(propertyDescriptor);
            final Object value2 = otherProperties.get(propertyDescriptor);
            final boolean equals = EqualsBuilder.equals(value1, value2);
            if (!equals) {
                return false;
            }
        }

        return true;
    }
}
