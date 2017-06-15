/**
 * @author pengys5
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
            {"data": "startTime"},
            {"data": "operationName"},
            {"data": "isError"},
            {"data": "cost"},
            {"data": "traceIds"}
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