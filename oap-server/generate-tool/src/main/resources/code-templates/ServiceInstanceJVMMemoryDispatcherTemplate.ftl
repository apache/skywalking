/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.analysis.generated.serviceinstancejvmmemory;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
<#if (serviceInstanceJVMMemoryIndicators?size>0)>
import org.apache.skywalking.oap.server.core.analysis.worker.IndicatorProcess;
    <#list serviceInstanceJVMMemoryIndicators as indicator>
        <#if indicator.filterExpressions??>
import org.apache.skywalking.oap.server.core.analysis.indicator.expression.*;
            <#break>
        </#if>
    </#list>
</#if>
import org.apache.skywalking.oap.server.core.source.*;

/**
 * This class is auto generated. Please don't change this class manually.
 *
 * @author Observability Analysis Language code generator
 */
public class ServiceInstanceJVMMemoryDispatcher implements SourceDispatcher<ServiceInstanceJVMMemory> {

    @Override public void dispatch(ServiceInstanceJVMMemory source) {
<#list serviceInstanceJVMMemoryIndicators as indicator>
        do${indicator.metricName}(source);
</#list>
    }

<#list serviceInstanceJVMMemoryIndicators as indicator>
    private void do${indicator.metricName}(ServiceInstanceJVMMemory source) {
        ${indicator.metricName}Indicator indicator = new ${indicator.metricName}Indicator();

    <#if indicator.filterExpressions??>
        <#list indicator.filterExpressions as filterExpression>
        if (!new ${filterExpression.expressionObject}().setLeft(${filterExpression.left}).setRight(${filterExpression.right}).match()) {
            return;
        }
        </#list>
    </#if>

        indicator.setTimeBucket(source.getTimeBucket());
    <#list indicator.fieldsFromSource as field>
        indicator.${field.fieldSetter}(source.${field.fieldGetter}());
    </#list>
        indicator.${indicator.entryMethod.methodName}(<#list indicator.entryMethod.argsExpressions as arg>${arg}<#if arg_has_next>, </#if></#list>);
        IndicatorProcess.INSTANCE.in(indicator);
    }
</#list>
}
