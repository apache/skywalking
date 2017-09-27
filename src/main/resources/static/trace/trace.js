/**
 * @author pengys5
 */
var startDate = moment().subtract(2, 'days').format("YYYYMMDD") + "0000";
var endDate = moment().format("YYYYMMDD") + "9999";
var minCost = -1;
var maxCost = -1;
var globalTraceId = null;
var operationName = null;
var sort = null;
var isError = null;
var applicationId = 0;

$(document).ready(function () {
    $("#topTraceListDiv").load("./top-trace-list.html");
    $("#traceStackDiv").load("./trace-stack.html");
    $("#spanModalTempDiv").load("./span.html");

    bindRangePicker();
    loadApplications();

    $("#selectBtn").click(function () {
        minCost = $("#costMinInput").val();
        maxCost = $("#costMaxInput").val();
        globalTraceId = $("#globalTraceIdInput").val();
        operationName = $("#operationNameInput").val();
        applicationId = $("#applicationId").val();
        isError = $("#isError").val();

        if (minCost == null || minCost == "") {
            minCost = -1;
        }
        if (maxCost == null || maxCost == "") {
            maxCost = -1;
        }
        sort = $("input[name='sortColumn']:checked").val();

        $("#traceStackDiv").empty();
        $("#traceStackDiv").load("./trace-stack.html");
        postAjaxRequest();
    });
});

function bindRangePicker() {
    $('#dateRangeInput').daterangepicker({
        "timePicker": true,
        "timePicker24Hour": true,
        startDate: moment().subtract(2, 'days'),
        endDate: moment(),
        // minDate: moment().subtract(30, 'days'),
        maxDate: moment(),
        "opens": "left"
    }, function (start, end, label) {
        startDate = start.format("YYYYMMDDHHmm");
        endDate = end.format("YYYYMMDDHHmm");
    });
}

function loadApplications() {
    $.ajaxSettings.async = false;

    console.log(startDate);
    $.getJSON("/applications", {
        timeBucketType: "minute",
        startTime: startDate,
        endTime: endDate
    }, function (data) {
        console.log(data);
        var selOpt = $("#applicationId option");
        selOpt.remove();

        var selObj = $("#applicationId");
        selObj.append("<option value='0'>All</option>");
        for (var i = 0; i < data.length; i++) {
            selObj.append("<option value='" + data[i].applicationId + "'>" + data[i].applicationCode + "</option>");
        }
    });
}