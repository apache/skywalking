private void do${metricsName}(org.apache.skywalking.oap.server.core.source.${sourceName} source) {
    org.apache.skywalking.oal.rt.metrics.${metricsName}Metrics metrics = new org.apache.skywalking.oal.rt.metrics.${metricsName}Metrics();

    <#if filterExpressions??>
        <#list filterExpressions as filterExpression>
            if (!new org.apache.skywalking.oap.server.core.analysis.metrics.expression.${filterExpression.expressionObject}().match(${filterExpression.left}, ${filterExpression.right})) {
                return;
            }
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
                 new org.apache.skywalking.oap.server.core.analysis.metrics.expression.${arg.expressionObject}().match(${arg.left}, ${arg.right})
            </#if><#if arg_has_next>, </#if>
        </#list>);

    org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor.getInstance().in(metrics);
}
