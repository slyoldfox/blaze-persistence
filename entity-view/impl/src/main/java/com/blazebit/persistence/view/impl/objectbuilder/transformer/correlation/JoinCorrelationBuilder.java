/*
 * Copyright 2014 - 2020 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazebit.persistence.view.impl.objectbuilder.transformer.correlation;

import com.blazebit.persistence.CorrelationQueryBuilder;
import com.blazebit.persistence.FromProvider;
import com.blazebit.persistence.FullQueryBuilder;
import com.blazebit.persistence.FullSelectCTECriteriaBuilder;
import com.blazebit.persistence.JoinOnBuilder;
import com.blazebit.persistence.ParameterHolder;
import com.blazebit.persistence.SubqueryBuilder;
import com.blazebit.persistence.spi.DbmsDialect;
import com.blazebit.persistence.spi.LateralStyle;
import com.blazebit.persistence.view.CorrelationBuilder;
import com.blazebit.persistence.view.impl.objectbuilder.Limiter;

import javax.persistence.metamodel.EntityType;
import java.util.Map;

/**
 *
 * @author Christian Beikov
 * @since 1.2.0
 */
public class JoinCorrelationBuilder implements CorrelationBuilder {

    private final ParameterHolder<?> parameterHolder;
    private final Map<String, Object> optionalParameters;
    private final FullQueryBuilder<?, ?> criteriaBuilder;
    private final String joinBase;
    private final String correlationAlias;
    private final String correlationExternalAlias;
    private final String attributePath;
    private final Limiter limiter;
    private boolean correlated;
    private Object correlationBuilder;

    public JoinCorrelationBuilder(ParameterHolder<?> parameterHolder, Map<String, Object> optionalParameters, FullQueryBuilder<?, ?> criteriaBuilder, String joinBase, String correlationAlias, String correlationExternalAlias, String attributePath, Limiter limiter) {
        this.parameterHolder = parameterHolder;
        this.optionalParameters = optionalParameters;
        this.criteriaBuilder = criteriaBuilder;
        this.joinBase = joinBase;
        this.correlationAlias = correlationAlias;
        this.correlationExternalAlias = correlationExternalAlias;
        this.attributePath = attributePath;
        this.limiter = limiter;
    }

    @Override
    public <T> T getService(Class<T> serviceClass) {
        return criteriaBuilder.getService(serviceClass);
    }

    @Override
    public FromProvider getCorrelationFromProvider() {
        return criteriaBuilder;
    }

    @Override
    public String getCorrelationAlias() {
        return correlationAlias;
    }

    public void finish() {
        if (correlationBuilder instanceof SubqueryBuilder<?>) {
            ((SubqueryBuilder<JoinOnBuilder<?>>) correlationBuilder).end().end();
        } else  if (correlationBuilder instanceof FullSelectCTECriteriaBuilder<?>) {
            ((FullSelectCTECriteriaBuilder<?>) correlationBuilder).end();
        }
    }

    @Override
    public JoinOnBuilder<CorrelationQueryBuilder> correlate(Class<?> entityClass) {
        if (correlated) {
            throw new IllegalArgumentException("Can not correlate with multiple entity classes!");
        }

        correlated = true;
        if (limiter == null) {
            return (JoinOnBuilder<CorrelationQueryBuilder>) (JoinOnBuilder<?>) criteriaBuilder.leftJoinOn(joinBase, entityClass, correlationAlias);
        } else {
            if (getService(DbmsDialect.class).getLateralStyle() == LateralStyle.NONE) {
                checkLimitSupport();
                JoinOnBuilder<?> joinOnBuilder = criteriaBuilder.leftJoinOn(joinBase, entityClass, correlationExternalAlias);
                SubqueryBuilder<?> subqueryBuilder = joinOnBuilder.on(correlationExternalAlias).in().from(entityClass, correlationAlias);
                limiter.apply(parameterHolder, optionalParameters, subqueryBuilder);
                this.correlationBuilder = subqueryBuilder;
                return subqueryBuilder.getService(JoinOnBuilder.class);
            } else {
                FullSelectCTECriteriaBuilder<?> lateralBuilder = criteriaBuilder.leftJoinLateralEntitySubquery(joinBase, entityClass, correlationExternalAlias, correlationAlias);
                limiter.apply(parameterHolder, optionalParameters, lateralBuilder);
                this.correlationBuilder = lateralBuilder;
                return lateralBuilder.getService(JoinOnBuilder.class);
            }
        }
    }

    @Override
    public JoinOnBuilder<CorrelationQueryBuilder> correlate(EntityType<?> entityType) {
        if (correlated) {
            throw new IllegalArgumentException("Can not correlate with multiple entity classes!");
        }

        correlated = true;
        if (limiter == null) {
            return (JoinOnBuilder<CorrelationQueryBuilder>) (JoinOnBuilder<?>) criteriaBuilder.leftJoinOn(joinBase, entityType, correlationAlias);
        } else {
            if (getService(DbmsDialect.class).getLateralStyle() == LateralStyle.NONE) {
                checkLimitSupport();
                JoinOnBuilder<?> joinOnBuilder = criteriaBuilder.leftJoinOn(joinBase, entityType, correlationExternalAlias);
                SubqueryBuilder<?> subqueryBuilder = joinOnBuilder.on(correlationExternalAlias).in().from(entityType, correlationAlias);
                limiter.apply(parameterHolder, optionalParameters, subqueryBuilder);
                this.correlationBuilder = subqueryBuilder;
                return subqueryBuilder.getService(JoinOnBuilder.class);
            } else {
                FullSelectCTECriteriaBuilder<?> lateralBuilder = criteriaBuilder.leftJoinLateralEntitySubquery(joinBase, entityType, correlationExternalAlias, correlationAlias);
                limiter.apply(parameterHolder, optionalParameters, lateralBuilder);
                this.correlationBuilder = lateralBuilder;
                return lateralBuilder.getService(JoinOnBuilder.class);
            }
        }
    }

    private void checkLimitSupport() {
        if (!getService(DbmsDialect.class).supportsLimitInQuantifiedPredicateSubquery()) {
            throw new IllegalStateException("Can't limit the amount of elements for the attribute path " + attributePath + " because the DBMS doesn't support lateral or the use of LIMIT in quantified predicates! Use the SELECT strategy with batch size 1 if you really need this.");
        }
    }
}
