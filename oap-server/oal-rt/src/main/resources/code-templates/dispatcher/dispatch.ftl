public void dispatch(org.apache.skywalking.oap.server.core.source.ISource source) {
${sourcePackage}${source} _source = (${sourcePackage}${source})source;
<#list metrics as metrics>
    do${metrics.metricsName}(_source);
</#list>
}
