private void do${metricsName}(${sourcePackage}${sourceName} source) {

<#if filterExpressions?? && filterExpressions?size gt 0>
    <#list filterExpressions as filterExpression>
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
metrics.${entranceMethod.methodName}(
<#list entranceMethod.argsExpressions as arg>
    ${arg}<#if arg_has_next>, </#if>
</#list>);

org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor.getInstance().in(metrics);
}
