public org.apache.skywalking.oap.server.core.analysis.metrics.Metrics toHour() {
${metricsClassPackage}${metricsName}Metrics metrics = new ${metricsClassPackage}${metricsName}Metrics();
<#list fieldsFromSource as sourceField>
    metrics.${sourceField.fieldSetter}(this.${sourceField.fieldGetter}());
</#list>
<#list persistentFields as field>
    metrics.${field.fieldSetter}(this.${field.fieldGetter}());
</#list>
metrics.setTimeBucket(this.toTimeBucketInHour());
return metrics;
}
