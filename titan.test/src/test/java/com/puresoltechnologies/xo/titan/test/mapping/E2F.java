package com.puresoltechnologies.xo.titan.test.mapping;

import com.buschmais.xo.api.Query.Result;
import com.buschmais.xo.api.annotation.ResultOf;
import com.buschmais.xo.api.annotation.ResultOf.Parameter;
import com.puresoltechnologies.xo.titan.api.annotation.EdgeDefinition;
import com.puresoltechnologies.xo.titan.api.annotation.EdgeDefinition.Incoming;
import com.puresoltechnologies.xo.titan.api.annotation.EdgeDefinition.Outgoing;
import com.puresoltechnologies.xo.titan.api.annotation.Gremlin;
import com.puresoltechnologies.xo.titan.impl.TitanStoreSession;

@EdgeDefinition("E2F")
public interface E2F {

	@Outgoing
	E getE();

	@Incoming
	F getF();

	@ResultOf(query = ByValue.class, usingThisAs = "e2f")
	Result<ByValue> getResultByValueUsingExplicitQuery(
			@Parameter("value") String value);

	@ResultOf(usingThisAs = "e2f")
	Result<ByValue> getResultByValueUsingReturnType(
			@Parameter("value") String value);

	@ResultOf(query = ByValue.class, usingThisAs = "e2f")
	ByValue getByValueUsingExplicitQuery(@Parameter("value") String value);

	@ResultOf(usingThisAs = "e2f")
	ByValue getByValueUsingReturnType(@Parameter("value") String value);

	@ResultOf
	ByValueUsingImplicitThis getByValueUsingImplicitThis(
			@Parameter("value") String value);

	@ResultOf
	// @Gremlin("match ()-[e2f:E2F]->(f:F) where e2f.value={value} return f")
	@Gremlin("_().outE.has('label', 'E2F').has('value', {value}).inV.has('"
			+ TitanStoreSession.XO_DISCRIMINATORS_PROPERTY + "F')")
	Result<F> getResultUsingGremlin(@Parameter("value") String value);

	@ResultOf
	// @Gremlin("match ()-[e2f:E2F]->(f:F) where e2f.value={value} return f")
	@Gremlin("_().outE.has('label', 'E2F').has('value', {value}).inV.has('"
			+ TitanStoreSession.XO_DISCRIMINATORS_PROPERTY + "F')")
	F getSingleResultUsingGremlin(@Parameter("value") String value);

	void setValue(String value);

	String getValue();

	// @Gremlin("match ()-[e2f:E2F]->(f:F) where e2f={e2f} and e2f.value={value} return f")
	@Gremlin(value = "_().outE.has('label', 'E2F').has('value', {value}).inV.has('"
			+ TitanStoreSession.XO_DISCRIMINATORS_PROPERTY + "F')", name = "f")
	public interface ByValue {
		F getF();
	}

	// @Gremlin("match ()-[e2f:E2F]->(f:F) where e2f={this} and e2f.value={value} return f")
	@Gremlin(value = "_().outE.has('label', 'E2F').has('value', {value}).inV.has('"
			+ TitanStoreSession.XO_DISCRIMINATORS_PROPERTY + "F')", name = "f")
	public interface ByValueUsingImplicitThis {
		F getF();
	}
}
