/*
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at http://www.eclipse.org/legal/epl-v10.html
 */
package io.liveoak.spi.state;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.liveoak.spi.LiveOak;
import io.liveoak.spi.exceptions.InvalidPropertyTypeException;
import io.liveoak.spi.exceptions.PropertyException;
import io.liveoak.spi.exceptions.RequiredPropertyException;

/**
 * Opaque state of a resource.
 *
 * <p>State objects are used to instill new state into a server-side resource.</p>
 *
 * @author Bob McWhirter
 */
public interface ResourceState {

    /**
     * Retrieve the ID of the resource.
     *
     * @return The ID of the resource.
     */
    String id();

    /**
     * Set the ID of the resource.
     *
     * @param id The ID of the resource.
     */
    void id(String id);

    void uri(URI uri);

    default URI uri() {
        ResourceState self = getPropertyAsResourceState(LiveOak.SELF);
        if (self == null) {
            return null;
        }
        Object href = self.getProperty(LiveOak.HREF);
        if (href == null) {
            return null;
        }
        if (href instanceof URI) {
            return (URI) href;
        }

        try {
            return new URI(String.valueOf(href));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid self/href: " + href);
        }
    }

    /**
     * Add a property to the state.
     *
     * <p>Property values may be either simple scalar
     * values, or complex {@link ResourceState} objects</p>
     *
     * @param name  The name of the property.
     * @param value The value of the property.
     */
    void putProperty(String name, Object value);

    /**
     * Retrieve a property value.
     *
     * @param name The property name.
     * @return The value of the property, as either a simple scalar, or as a
     *         more complex {@link ResourceState}.
     */
    Object getProperty(String name);

    /**
     * Retreive a property value as String
     *
     * @param name The property name.
     * @return The value of the property, as a String
     * @throw RuntimeException if value of the named property is not a String
     */
    default String getPropertyAsString(String name) {
        Object val = getProperty(name);
        if (val == null) {
            return null;
        }
        if (val instanceof String || val instanceof Number || val instanceof Boolean) {
            return String.valueOf(val);
        }
        throw new RuntimeException("Value can't be returned as String: " + val + " [" + val.getClass() + "]");
    }

    default Integer getPropertyAsInteger(String name) {
        Object val = getProperty(name);
        if (val == null) {
            return null;
        }
        if (val instanceof Integer || val instanceof Long || val instanceof Short) {
            return ((Number) val).intValue();
        }
        if (val instanceof String) {
            return Integer.valueOf((String) val);
        }
        throw new RuntimeException("Value can't be returned as Integer: " + val + " [" + val.getClass() + "]");
    }

    default Long getPropertyAsLong(String name) {
        Object val = getProperty(name);
        if (val == null) {
            return null;
        }
        if (val instanceof Integer || val instanceof Long || val instanceof Short) {
            return ((Number) val).longValue();
        }
        if (val instanceof String) {
            return Long.valueOf((String) val);
        }
        throw new RuntimeException("Value can't be returned as Long: " + val + " [" + val.getClass() + "]");
    }

    default Boolean getPropertyAsBoolean(String name) {
        Object val = getProperty(name);
        if (val == null) {
            return null;
        }
        if (val instanceof Boolean) {
            return ((Boolean) val).booleanValue();
        }
        if (val instanceof String) {
            return Boolean.valueOf((String) val);
        }
        throw new RuntimeException("Value can't be returned as Boolean: " + val + " [" + val.getClass() + "]");
    }

    default boolean isListPropertyOrNull(String name) {
        Object val = getProperty(name);
        return val == null || val instanceof List;
    }

    default List getPropertyAsList(String name) {
        Object val = getProperty(name);
        if (val == null) {
            return null;
        }
        if (val instanceof List) {
            return (List) val;
        }

        ArrayList ret = new ArrayList();
        ret.add(val);
        return ret;
    }

    default ResourceState getPropertyAsResourceState(String name) {
        Object val = getProperty(name);
        if (val == null) {
            return null;
        }
        if (val instanceof ResourceState) {
            return (ResourceState) val;
        }
        throw new RuntimeException("Value can't be returned as ResourceState: " + val + " [" + val.getClass() + "]");
    }

    default <P> P getProperty(String name, boolean required, Class<P> requestedType) throws PropertyException {
        return getProperty(name, required, requestedType, false);
    }

    default <T extends Enum<T>,P> P getProperty(String name, boolean required, Class<P> requestedType, boolean ignoreType) throws PropertyException {
        Object propertyObject = getProperty(name);
        if (required && (propertyObject == null || (propertyObject instanceof String && ((String) propertyObject).isEmpty()))) {
                throw new RequiredPropertyException(name, requestedType);
        } else if (propertyObject == null) {
            return null;
        } else if (requestedType.isInstance(propertyObject)) {
            return requestedType.cast(propertyObject);
        } else if (requestedType == Long.class && propertyObject.getClass() == Integer.class) {
            //special check for numbers
            return (P) new Long((Integer) propertyObject);
        } else if (requestedType.isEnum() && propertyObject instanceof String) {
            return (P) Enum.valueOf((Class<T>)requestedType, (String)propertyObject);
        } else if (ignoreType == false) {
            throw new InvalidPropertyTypeException(name, requestedType);
        } else {
            return null;
        }
    }

    Object removeProperty(String name);

    Set<String> getPropertyNames();

    void addMember(ResourceState member);

    List<ResourceState> members();

    default ResourceState member(String id) {
        ResourceState state = null;
        if (id != null && id.length() > 0) {
            Optional<ResourceState> optional = members().stream().filter(member -> member.id().equals(id)).findFirst();
            if (optional.isPresent()) {
                state = optional.get();
            }
        }
        return state;
    }
}
