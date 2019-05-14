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

package org.apache.skywalking.oap.server.core.analysis.generated.${packageName};

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
<#if (metrics?size>0)>
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsProcess;
    <#list metrics as metrics>
        <#if metrics.filterExpressions??>
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.*;
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
public class ${source}Dispatcher implements SourceDispatcher<${source}> {

    @Override public void dispatch(${source} source) {
<#list metrics as metrics>
        do${metrics.metricsName}(source);
</#list>
    }

<#list metrics as metrics>
    private void do${metrics.metricsName}(${source} source) {
        ${metrics.metricsName}Metrics metrics = new ${metrics.metricsName}Metrics();

    <#if metrics.filterExpressions??>
        <#list metrics.filterExpressions as filterExpression>
            <#if filterExpression.expressionObject == "GreaterMatch" || filterExpression.expressionObject == "LessMatch" || filterExpression.expressionObject == "GreaterEqualMatch" || filterExpression.expressionObject == "LessEqualMatch">
        if (!new ${filterExpression.expressionObject}().match(${filterExpression.left}, ${filterExpression.right})) {
            return;
        }
            <#else>
        if (!new ${filterExpression.expressionObject}().setLeft(${filterExpression.left}).setRight(${filterExpression.right}).match()) {
            return;
        }
            </#if>
        </#list>
    </#if>

        metrics.setTimeBucket(source.getTimeBucket());
    <#list metrics.fieldsFromSource as field>
        metrics.${field.fieldSetter}(source.${field.fieldGetter}());
    </#list>
        metrics.${metrics.entryMethod.methodName}(<#list metrics.entryMethod.argsExpressions as arg>${arg}<#if arg_has_next>, </#if></#list>);
        MetricsStreamProcessor.getInstance().in(metrics);
    }
</#list>
}
