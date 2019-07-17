private void do${metricsName}(org.apache.skywalking.oap.server.core.source.${sourceName} source) {
    org.apache.skywalking.oal.rt.metrics.${metricsName}Metrics metrics = new org.apache.skywalking.oal.rt.metrics.${metricsName}Metrics();

    <#if filterExpressions??>
        <#list filterExpressions as filterExpression>
            <#if filterExpression.expressionObject == "GreaterMatch" || filterExpression.expressionObject == "LessMatch" || filterExpression.expressionObject == "GreaterEqualMatch" || filterExpression.expressionObject == "LessEqualMatch">
                if (!new org.apache.skywalking.oap.server.core.analysis.metrics.expression.${filterExpression.expressionObject}().match(${filterExpression.left}, ${filterExpression.right})) {
                    return;
                }
            <#else>
                if (!new org.apache.skywalking.oap.server.core.analysis.metrics.expression.${filterExpression.expressionObject}().setLeft(${filterExpression.left}).setRight(${filterExpression.right}).match()) {
                    return;
                }
            </#if>
        </#list>
    </#if>

    metrics.setTimeBucket(source.getTimeBucket());
    <#list fieldsFromSource as field>
        metrics.${field.fieldSetter}(source.${field.fieldGetter}());
    </#list>
    metrics.${entryMethod.methodName}(
        <#list entryMethod.argsExpressions as arg>
            <#if entryMethod.argTypes[arg_index] == 1>
                ${arg}
            <#else>
                <#if arg.expressionObject == "GreaterMatch" || arg.expressionObject == "LessMatch" || arg.expressionObject == "GreaterEqualMatch" || arg.expressionObject == "LessEqualMatch">
                    new org.apache.skywalking.oap.server.core.analysis.metrics.expression.${arg.expressionObject}().match(${arg.left}, ${arg.right})
                <#else>
                    new org.apache.skywalking.oap.server.core.analysis.metrics.expression.${arg.expressionObject}().setLeft(${arg.left}).setRight(${arg.right}).match()
                </#if>
            </#if><#if arg_has_next>, </#if>
        </#list>);

    org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor.getInstance().in(metrics);
}
