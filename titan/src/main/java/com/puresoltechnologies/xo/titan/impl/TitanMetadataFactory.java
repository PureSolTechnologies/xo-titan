package com.puresoltechnologies.xo.titan.impl;

import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.buschmais.xo.api.XOException;
import com.buschmais.xo.spi.datastore.DatastoreMetadataFactory;
import com.buschmais.xo.spi.metadata.method.IndexedPropertyMethodMetadata;
import com.buschmais.xo.spi.metadata.type.TypeMetadata;
import com.buschmais.xo.spi.reflection.AnnotatedElement;
import com.buschmais.xo.spi.reflection.AnnotatedMethod;
import com.buschmais.xo.spi.reflection.AnnotatedType;
import com.buschmais.xo.spi.reflection.PropertyMethod;
import com.puresoltechnologies.xo.titan.api.annotation.EdgeDefinition;
import com.puresoltechnologies.xo.titan.api.annotation.EdgeDefinition.Incoming;
import com.puresoltechnologies.xo.titan.api.annotation.EdgeDefinition.Outgoing;
import com.puresoltechnologies.xo.titan.api.annotation.Indexed;
import com.puresoltechnologies.xo.titan.api.annotation.Property;
import com.puresoltechnologies.xo.titan.api.annotation.VertexDefinition;
import com.puresoltechnologies.xo.titan.impl.metadata.TitanCollectionPropertyMetadata;
import com.puresoltechnologies.xo.titan.impl.metadata.TitanEdgeMetadata;
import com.puresoltechnologies.xo.titan.impl.metadata.TitanIndexedPropertyMetadata;
import com.puresoltechnologies.xo.titan.impl.metadata.TitanPropertyMetadata;
import com.puresoltechnologies.xo.titan.impl.metadata.TitanReferencePropertyMetadata;
import com.puresoltechnologies.xo.titan.impl.metadata.TitanVertexMetadata;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Element;
import com.tinkerpop.blueprints.Vertex;

/**
 * This class implements the XO DatastoreMetadataFactory for Titan database.
 * 
 * @author Rick-Rainer Ludwig
 */
public class TitanMetadataFactory
	implements
	DatastoreMetadataFactory<TitanVertexMetadata, String, TitanEdgeMetadata, String> {

    @Override
    public TitanVertexMetadata createEntityMetadata(
	    AnnotatedType annotatedType,
	    Map<Class<?>, TypeMetadata> metadataByType) {
	VertexDefinition annotation = annotatedType
		.getAnnotation(VertexDefinition.class);
	String value = null;
	IndexedPropertyMethodMetadata<?> indexedProperty = null;
	if (annotation != null) {
	    value = annotation.value();
	    if ((value == null) || (value.isEmpty())) {
		value = annotatedType.getName();
	    }
	    Class<?> usingIndexOf = annotation.usingIndexedPropertyOf();
	    if (!Object.class.equals(usingIndexOf)) {
		TypeMetadata typeMetadata = metadataByType.get(usingIndexOf);
		indexedProperty = typeMetadata.getIndexedProperty();
	    }
	}
	return new TitanVertexMetadata(value, indexedProperty);
    }

    @Override
    public <ImplementedByMetadata> ImplementedByMetadata createImplementedByMetadata(
	    AnnotatedMethod annotatedMethod) {
	return null;
    }

    @Override
    public TitanCollectionPropertyMetadata createCollectionPropertyMetadata(
	    PropertyMethod propertyMethod) {
	String name = determinePropertyName(propertyMethod);
	Direction direction = determineEdgeDirection(propertyMethod);
	return new TitanCollectionPropertyMetadata(name, direction);
    }

    @Override
    public TitanReferencePropertyMetadata createReferencePropertyMetadata(
	    PropertyMethod propertyMethod) {
	String name = determinePropertyName(propertyMethod);
	Direction direction = determineEdgeDirection(propertyMethod);
	return new TitanReferencePropertyMetadata(name, direction);
    }

    @Override
    public TitanPropertyMetadata createPropertyMetadata(
	    PropertyMethod propertyMethod) {
	Property property = propertyMethod
		.getAnnotationOfProperty(Property.class);
	String name = property != null ? property.value() : propertyMethod
		.getName();
	return new TitanPropertyMetadata(name);
    }

    /**
     * This method is a helper method to extract the name from a
     * {@link PropertyMethod}.
     * 
     * @param propertyMethod
     *            is the {@link PropertyMethod} object which represents the
     *            method for which the name is to be checked.
     * @return A {@link String} object is returned containing the name of the
     *         edge.
     */
    private static String determinePropertyName(PropertyMethod propertyMethod) {
	VertexDefinition property = propertyMethod
		.getAnnotationOfProperty(VertexDefinition.class);
	return property != null ? property.value() : propertyMethod.getName();
    }

    /**
     * This method is a helper method to extract the edge direction from a
     * {@link PropertyMethod}.
     * 
     * @param propertyMethod
     *            is the {@link PropertyMethod} object which represents the
     *            method for which the edge direction is to be checked.
     * @return A {@link Direction} object is returned containing the direction
     *         of the edge.
     */
    private static Direction determineEdgeDirection(
	    PropertyMethod propertyMethod) {
	Outgoing outgoingAnnotation = propertyMethod
		.getAnnotation(Outgoing.class);
	Incoming incomingAnnotation = propertyMethod
		.getAnnotation(Incoming.class);
	if ((outgoingAnnotation != null) && (incomingAnnotation != null)) {
	    return Direction.BOTH;
	} else if (incomingAnnotation != null) {
	    return Direction.IN;
	} else {
	    return Direction.OUT;
	}
    }

    @Override
    public TitanIndexedPropertyMetadata createIndexedPropertyMetadata(
	    PropertyMethod propertyMethod) {
	Property property = propertyMethod
		.getAnnotationOfProperty(Property.class);
	String name = property != null ? property.value() : propertyMethod
		.getName();
	Class<?> declaringClass = propertyMethod.getAnnotatedElement()
		.getDeclaringClass();
	Class<? extends Element> type = null;
	if (declaringClass.getAnnotation(VertexDefinition.class) != null) {
	    type = Vertex.class;
	} else if (declaringClass.getAnnotation(EdgeDefinition.class) != null) {
	    type = Edge.class;
	} else {
	    throw new XOException(
		    "Property '"
			    + name
			    + "' was found with index annotation, but the declaring type is neither a vertex nor an edge.");
	}
	Indexed indexedAnnotation = propertyMethod.getAnnotation(Indexed.class);
	boolean unique = indexedAnnotation.unique();
	Class<?> dataType = propertyMethod.getType();
	return new TitanIndexedPropertyMetadata(name, unique, dataType, type);
    }

    @Override
    public TitanEdgeMetadata createRelationMetadata(
	    AnnotatedElement<?> annotatedElement,
	    Map<Class<?>, TypeMetadata> metadataByType) {
	EdgeDefinition relationAnnotation;
	if (annotatedElement instanceof PropertyMethod) {
	    relationAnnotation = ((PropertyMethod) annotatedElement)
		    .getAnnotationOfProperty(EdgeDefinition.class);
	} else {
	    relationAnnotation = annotatedElement
		    .getAnnotation(EdgeDefinition.class);
	}
	String name = null;
	if (relationAnnotation != null) {
	    String value = relationAnnotation.value();
	    if (!EdgeDefinition.DEFAULT_VALUE.equals(value)) {
		name = value;
	    }
	}
	if (name == null) {
	    name = StringUtils.uncapitalize(annotatedElement.getName());
	}
	return new TitanEdgeMetadata(name);
    }
}
