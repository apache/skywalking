public void dispatch(org.apache.skywalking.oap.server.core.source.${source} source) {
    <#list metrics as metrics>
        do${metrics.metricsName}(source);
    </#list>
}