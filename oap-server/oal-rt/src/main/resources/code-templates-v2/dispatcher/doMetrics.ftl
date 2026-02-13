private void do${metricsName}(${sourcePackage}${from.sourceName} source) {

<#if filters.filterExpressions??>
    <#list filters.filterExpressions as filterExpression>
        if (!new ${filterExpression.expressionObject}().match(${filterExpression.left}, ${filterExpression.right})) {
        return;
        }
    </#list>
</#if>

${metricsClassPackage}${metricsName}Metrics metrics = new ${metricsClassPackage}${metricsName}Metrics();
<#if sourceDecorator??>
    source.decorate("${sourceDecorator}");
</#if>
metrics.setTimeBucket(source.getTimeBucket());
<#list fieldsFromSource as field>
    <#if field.attribute && !sourceDecorator??>
        <#--Metrics share the source instance, do not process attributes unless added decorator func-->
    <#else>
        metrics.${field.fieldSetter}(source.${field.fieldGetter}());
    </#if>
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
