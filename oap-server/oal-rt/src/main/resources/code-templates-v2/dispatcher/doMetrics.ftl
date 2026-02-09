private void do${metricsName}(${sourcePackage}${sourceName} source) {

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
