package com.puresoltechnologies.xo.titan.test.relation.implicit;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

import com.puresoltechnologies.xo.titan.api.annotation.EdgeDefinition;

@EdgeDefinition
@Retention(RUNTIME)
public @interface ImplicitOneToOne {
}
