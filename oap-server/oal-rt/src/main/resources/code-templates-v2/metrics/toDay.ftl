public org.apache.skywalking.oap.server.core.analysis.metrics.Metrics toDay() {
${metricsClassPackage}${metricsName}Metrics metrics = new ${metricsClassPackage}${metricsName}Metrics();
<#list fieldsFromSource as sourceField>
    metrics.${sourceField.fieldSetter}(this.${sourceField.fieldGetter}());
</#list>
<#list persistentFields as field>
    metrics.${field.fieldName} = this.${field.fieldName};
</#list>
metrics.setTimeBucket(org.apache.skywalking.oap.server.core.analysis.TimeBucket.getDayTimeBucket(this.getTimeBucket()));
return metrics;
}
