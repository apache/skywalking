/**
 * @author pengys5
 */
$(document).ready(function () {
    $("#mainDiv").load("/dag/dag.html");

    $("#traceDagMenu").click(function () {
        console.log("traceDagMenu");
        $("#mainDiv").load("/dag/dag.html");
    });
    $("#traceMetricMenu").click(function () {
        console.log("traceMetricMenu");
        $("#mainDiv").load("/metric/metric.html");
    });
    $("#traceSegmentMenu").click(function () {
        console.log("traceSegmentMenu");
        $("#mainDiv").load("/trace/trace.html");
    });
});