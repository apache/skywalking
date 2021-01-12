private void do${metricsName}(${sourcePackage}${sourceName} source) {
${metricsClassPackage}${metricsName}Metrics metrics = new ${metricsClassPackage}${metricsName}Metrics();

<#if filterExpressions??>
    <#list filterExpressions as filterExpression>
        if (!new ${filterExpression.expressionObject}().match(${filterExpression.left}, ${filterExpression.right})) {
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
    <#if entryMethod.argTypes[arg_index] < 3>
        ${arg}
    <#else>
        new ${arg.expressionObject}().match(${arg.left}, ${arg.right})
    </#if><#if arg_has_next>, </#if>
</#list>);

org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor.getInstance().in(metrics);
}
