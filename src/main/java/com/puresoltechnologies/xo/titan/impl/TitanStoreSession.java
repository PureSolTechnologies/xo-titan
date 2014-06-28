package com.puresoltechnologies.xo.titan.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.buschmais.xo.api.ResultIterator;
import com.buschmais.xo.api.XOException;
import com.buschmais.xo.spi.datastore.DatastorePropertyManager;
import com.buschmais.xo.spi.datastore.DatastoreSession;
import com.buschmais.xo.spi.datastore.DatastoreTransaction;
import com.buschmais.xo.spi.datastore.TypeMetadataSet;
import com.buschmais.xo.spi.metadata.method.IndexedPropertyMethodMetadata;
import com.buschmais.xo.spi.metadata.method.PrimitivePropertyMethodMetadata;
import com.buschmais.xo.spi.metadata.type.EntityTypeMetadata;
import com.puresoltechnologies.xo.titan.impl.metadata.TitanVertexMetadata;
import com.puresoltechnologies.xo.titan.impl.metadata.TitanPropertyMetadata;
import com.puresoltechnologies.xo.titan.impl.metadata.TitanEdgeMetadata;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanGraphQuery;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.pipes.Pipe;

/**
 * This class implements a XO {@link DatastoreSession} for Titan database.
 * 
 * @author Rick-Rainer Ludwig
 */
public class TitanStoreSession
		implements
		DatastoreSession<Object, Vertex, TitanVertexMetadata, String, Object, Edge, TitanEdgeMetadata, String> {

	/**
	 * This constant contains the prefix for discriminator properties.
	 */
	public static final String XO_DISCRIMINATORS_PROPERTY = "_xo_discriminator_";

	/**
	 * This field contains the Titan graph as {@link TitanGraph} object.
	 */
	private final TitanGraph titanGraph;

	/**
	 * This is the initial value constructor.
	 * 
	 * @param titanGraph
	 *            is the Titan graph as {@link TitanGraph} object on which this
	 *            session shall work on.
	 */
	public TitanStoreSession(TitanGraph titanGraph) {
		this.titanGraph = titanGraph;
	}

	/**
	 * Returns the Titan graph which is currently opened.
	 * 
	 * @return A {@link TitanGraph} object is returned.
	 */
	public final TitanGraph getTitanGraph() {
		return titanGraph;
	}

	@Override
	public DatastoreTransaction getDatastoreTransaction() {
		return new TitanStoreTransaction(titanGraph);
	}

	@Override
	public boolean isEntity(Object o) {
		return Vertex.class.isAssignableFrom(o.getClass());
	}

	@Override
	public boolean isRelation(Object o) {
		return Edge.class.isAssignableFrom(o.getClass());
	}

	@Override
	public Set<String> getEntityDiscriminators(Vertex vertex) {
		Set<String> discriminators = new HashSet<>();
		for (String key : vertex.getPropertyKeys()) {
			if (key.startsWith(XO_DISCRIMINATORS_PROPERTY)) {
				String discriminator = vertex.getProperty(key);
				discriminators.add(discriminator);
			}
		}
		if (discriminators.size() == 0) {
			throw new XOException(
					"A vertex was found without discriminators. Does another framework alter the database?");
		}
		return discriminators;
	}

	@Override
	public String getRelationDiscriminator(Edge edge) {
		return edge.getLabel();
	}

	@Override
	public Object getEntityId(Vertex vertex) {
		return vertex.getId();
	}

	@Override
	public Object getRelationId(Edge edge) {
		return edge.getId();
	}

	@Override
	public Vertex createEntity(
			TypeMetadataSet<EntityTypeMetadata<TitanVertexMetadata>> types,
			Set<String> discriminators) {
		Vertex vertex = titanGraph.addVertex(null);
		for (String discriminator : discriminators) {
			vertex.setProperty(XO_DISCRIMINATORS_PROPERTY + discriminator,
					discriminator);
		}
		return vertex;
	}

	@Override
	public void deleteEntity(Vertex vertex) {
		vertex.remove();
	}

	@Override
	public ResultIterator<Vertex> findEntity(
			EntityTypeMetadata<TitanVertexMetadata> type, String discriminator,
			Object value) {
		TitanGraphQuery query = titanGraph.query();
		query = query.has(XO_DISCRIMINATORS_PROPERTY + discriminator);

		IndexedPropertyMethodMetadata<?> indexedProperty = type
				.getDatastoreMetadata().getIndexedProperty();
		if (indexedProperty == null) {
			indexedProperty = type.getIndexedProperty();
		}
		if (indexedProperty == null) {
			throw new XOException("Type "
					+ type.getAnnotatedType().getAnnotatedElement().getName()
					+ " has no indexed property.");
		}
		PrimitivePropertyMethodMetadata<TitanPropertyMetadata> propertyMethodMetadata = indexedProperty
				.getPropertyMethodMetadata();
		query = query.has(propertyMethodMetadata.getDatastoreMetadata()
				.getName(), value);
		Iterable<Vertex> vertices = query.vertices();
		final Iterator<Vertex> iterator = vertices.iterator();

		return new ResultIterator<Vertex>() {

			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public Vertex next() {
				return iterator.next();
			}

			@Override
			public void remove() {
				iterator.remove();
			}

			@Override
			public void close() {
				// intentionally left empty
			}
		};
	}

	@Override
	public <QL> ResultIterator<Map<String, Object>> executeQuery(QL query,
			Map<String, Object> parameters) {
		final GremlinExpression gremlinExpression = GremlinManager
				.getGremlinExpression(query, parameters);
		String expression = gremlinExpression.getExpression();
		@SuppressWarnings("unchecked")
		final Pipe<Vertex, ?> pipe = com.tinkerpop.gremlin.groovy.Gremlin
				.compile(expression);
		if (parameters.containsKey("this")) {
			Object setThis = parameters.get("this");
			if (Vertex.class.isAssignableFrom(setThis.getClass())) {
				Vertex vertex = (Vertex) setThis;
				pipe.setStarts(Arrays.asList(vertex));
			} else if (Edge.class.isAssignableFrom(setThis.getClass())) {
				Edge edge = (Edge) setThis;
				pipe.setStarts(Arrays.asList(edge.getVertex(Direction.IN),
						edge.getVertex(Direction.OUT)));
			} else {
				throw new XOException("Unsupported start point '"
						+ String.valueOf(setThis) + "' (class="
						+ setThis.getClass() + ")");
			}
		} else {
			pipe.setStarts(titanGraph.query().vertices());
		}
		return new ResultIterator<Map<String, Object>>() {

			@Override
			public boolean hasNext() {
				return pipe.hasNext();
			}

			@Override
			public Map<String, Object> next() {
				Map<String, Object> results = new HashMap<>();
				Object next = pipe.next();
				if (next instanceof Vertex) {
					results.put(gremlinExpression.getResultName(), next);
				} else if (next instanceof Edge) {
					results.put(gremlinExpression.getResultName(), next);
				} else if (next instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> map = (Map<String, Object>) next;
					results.putAll(map);
				} else {
					results.put("unknown_type", next);
				}
				return results;
			}

			@Override
			public void remove() {
				pipe.remove();
			}

			@Override
			public void close() {
				// there is no close required in pipe
			}
		};
	}

	@Override
	public void migrateEntity(Vertex vertex,
			TypeMetadataSet<EntityTypeMetadata<TitanVertexMetadata>> types,
			Set<String> discriminators,
			TypeMetadataSet<EntityTypeMetadata<TitanVertexMetadata>> targetTypes,
			Set<String> targetDiscriminators) {
		for (String discriminator : discriminators) {
			if (!targetDiscriminators.contains(discriminator)) {
				vertex.removeProperty(XO_DISCRIMINATORS_PROPERTY
						+ discriminator);
			}
		}
		for (String discriminator : targetDiscriminators) {
			if (!discriminators.contains(discriminator)) {
				vertex.setProperty(XO_DISCRIMINATORS_PROPERTY + discriminator,
						discriminator);
			}
		}
	}

	@Override
	public void flushEntity(Vertex vertex) {
		// intentionally left empty
	}

	@Override
	public void flushRelation(Edge edge) {
		// intentionally left empty
	}

	@Override
	public DatastorePropertyManager<Vertex, Edge, ?, TitanEdgeMetadata> getDatastorePropertyManager() {
		return new TitanStorePropertyManager();
	}

	@Override
	public void close() {
		// Nothing to do here...
	}

}
