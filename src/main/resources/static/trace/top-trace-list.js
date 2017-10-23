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
var topTraceDataTable;

$(document).ready(function () {
    createTopTraceDataTable();

    var table = $('#topTraceListTable').DataTable();
    $('#topTraceListTable tbody').on('click', 'tr', function () {
        if ($(this).hasClass('selected')) {
            $(this).removeClass('selected');
        }
        else {
            table.$('tr.selected').removeClass('selected');
            $(this).addClass('selected');

            var traceId = $(this).attr("id");
            drawStack(traceId);
        }
    });
});

function createTopTraceDataTable() {
    topTraceDataTable = $('#topTraceListTable').DataTable({
        "scrollY": "200px",
        "scrollCollapse": true,
        "searching": false,
        "ordering": false,
        select: true,
        "info": false,
        "bLengthChange": false,
        "serverSide": true,
        "processing": true,
        "pageLength": 30,
        "pagingType": "numbers",
        "columns": [
            {"data": "num"},
            {"data": "start_time"},
            {"data": "service_name"},
            {"data": "is_error"},
            {"data": "cost"},
            {"data": "global_trace_id"}
        ],
        ajax: function (data, callback, settings) {
            var param = {};
            param.limit = data.length;
            param.startTime = startDate;
            param.endTime = endDate;
            param.from = data.start;
            param.minCost = minCost;
            param.maxCost = maxCost;
            param.globalTraceId = globalTraceId;
            param.operationName = operationName;
            param.error = isError;
            param.applicationId = applicationId;
            param.sort = sort;

            $.ajax({
                type: "GET",
                url: "/topTraceListDataLoad",
                cache: false,
                data: param,
                dataType: "json",
                success: function (result) {
                    callback(result);
                }
            });
        }
    });
}

function postAjaxRequest() {
    topTraceDataTable.ajax.reload();
}
