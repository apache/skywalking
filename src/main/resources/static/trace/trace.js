/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking-ui
 */

/**
 * @author peng-yongsheng
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