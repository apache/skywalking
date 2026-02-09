public org.apache.skywalking.oap.server.core.analysis.metrics.Metrics toHour() {
${metricsClassPackage}${metricsName}Metrics metrics = new ${metricsClassPackage}${metricsName}Metrics();
<#list fieldsFromSource as sourceField>
    metrics.${sourceField.fieldSetter}(this.${sourceField.fieldGetter}());
</#list>
<#list persistentFields as field>
    metrics.${field.fieldName} = this.${field.fieldName};
</#list>
metrics.setTimeBucket(org.apache.skywalking.oap.server.core.analysis.TimeBucket.getHourTimeBucket(this.getTimeBucket()));
return metrics;
}
