define(['jquery', 'text!serviceTreeListHtml', "treeTable"], function ($, serviceTreeListHtml, treetable) {
        var vueData = {
            list: [],
            handler: undefined
        };

        function render(renderToDivId) {
            $("#" + renderToDivId).html(serviceTreeListHtml);
            $("#serviceTreeTableId").treetable({
                expandable: true,
                onInitialized: function () {
                    var rows = "";
                    for (var i = 0; i < vueData.list.length; i++) {
                        var row = vueData.list[i];
                        if (row.frontServiceId == 1) {
                            rows = rows + "<tr data-tt-id=" + row.behindServiceId + "><td>" + row.behindServiceName + "</td>";
                        } else {
                            rows = rows + "<tr data-tt-id=" + row.behindServiceId + " data-tt-parent-id=" + row.frontServiceId + "><td>" + row.behindServiceName + "</td>";
                        }
                        rows = rows + "<td>" + row.s1Lte + "</td>";
                        rows = rows + "<td>" + row.s3Lte + "</td>";
                        rows = rows + "<td>" + row.s5Lte + "</td>";
                        rows = rows + "<td>" + row.s5Gt + "</td>";
                        rows = rows + "<td>" + row.error + "</td>";
                        rows = rows + "<td>" + row.summary + "</td>";
                        rows = rows + "<td>" + (row.costSummary / row.summary).toFixed(2) + "</td>";
                        rows = rows + "<td>" + (((row.summary - row.error) / row.summary) * 100).toFixed(2) + "%</td>";
                        rows = rows + "</tr>";
                    }
                    $("#serviceTreeTableId").treetable("loadBranch", null, rows);
                }
            });
            return this;
        }

        function loadByEntryServiceId(entryServiceId, startTime, endTime) {
            var param = {
                timeBucketType: "minute",
                entryServiceId: entryServiceId,
                startTime: startTime,
                endTime: endTime
            };

            $.getJSON("/service/tree/entryServiceId", param, function (data) {
                vueData.list = data;
            });
            return this;
        }

        function loadByEntryServiceName(entryApplicationId, entryServiceName, startTime, endTime) {
            var param = {
                timeBucketType: "minute",
                entryApplicationId: entryApplicationId,
                entryServiceName: entryServiceName,
                startTime: startTime,
                endTime: endTime
            };

            $.getJSON("/service/tree/entryServiceName", param, function (data) {
                vueData.list = data;
            });
            return this;
        }

        function registryItemClickHandler(handler) {
            vueData.handler = handler;
            return this;
        }

        return {
            loadByEntryServiceId: loadByEntryServiceId,
            loadByEntryServiceName: loadByEntryServiceName,
            render: render,
            registryItemClickHandler: registryItemClickHandler
        }
    }
);
