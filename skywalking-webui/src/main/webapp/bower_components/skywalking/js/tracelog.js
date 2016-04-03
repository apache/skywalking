function changeData(data) {
    var result = {
        traceTree: {
            totalTime: 0,
            beginTime: 0,
            endTime: 0,
            treeNodes: []
        }
    };
    result.traceTree.totalTime = data.endTime - data.beginTime;
    var totalTime = result.traceTree.totalTime;
    result.traceTree.startTime = data.beginTime;
    result.traceTree.endTime = data.endTime;
    var tmpNode;
    for (var i = 0; i < data.nodes.length; i++) {
        tmpNode = data.nodes[i];
        if (tmpNode.colId == "0") {
            tmpNode.isEntryNode = true;
        } else {
            tmpNode.isEntryNode = false;
        }
        if (tmpNode.spanTypeName == "") {
            tmpNode.spanTypeName = "UNKNOWN";
        }
        tmpNode.statusCodeName = tmpNode.statusCodeName;
        if (tmpNode.statusCodeName == "") {
            tmpNode.statusCodeName = "MISSING";
        }

        if (tmpNode.timeLineList.length == 1) {
            tmpNode.case = 1;
            tmpNode.cost = tmpNode.cost;
            tmpNode.totalLengthPercent = 100 * (tmpNode.startDate - result.traceTree.startTime) / result.traceTree.totalTime;
            tmpNode.spiltLengthPercent = 100 * tmpNode.cost / result.traceTree.totalTime;
        }

        if (tmpNode.timeLineList.length == 2) {
            if (tmpNode.timeLineList[1].startTime < tmpNode.timeLineList[0].startTime) {
                tmpNode.cost = tmpNode.timeLineList[1].cost;
                tmpNode.totalLengthPercent = 100 * (tmpNode.timeLineList[1].startTime - result.traceTree.startTime) / result.traceTree.totalTime;
                tmpNode.spiltLengthPercent = 100 * tmpNode.timeLineList[1].cost / result.traceTree.totalTime;
            } else if ((tmpNode.timeLineList[1].startTime >= tmpNode.timeLineList[0].startTime) &&
                ((tmpNode.timeLineList[1].startTime) <= (tmpNode.timeLineList[0].startTime + tmpNode.timeLineList[0].cost))) {
                if ((tmpNode.timeLineList[1].startTime + tmpNode.timeLineList[1].cost) <= (tmpNode.timeLineList[0].startTime + tmpNode.timeLineList[0].cost)) {
                    tmpNode.case = 3;
                    tmpNode.totalLengthPercent = 100 * (tmpNode.timeLineList[0].startTime - result.traceTree.startTime) / result.traceTree.totalTime;
                    tmpNode.clientCost = (tmpNode.timeLineList[1].startTime - tmpNode.timeLineList[0].startTime);
                    tmpNode.clientCostPercent = 100 * tmpNode.clientCost / result.traceTree.totalTime;
                    tmpNode.networkCost = (tmpNode.timeLineList[1].startTime + tmpNode.timeLineList[1].cost - tmpNode.timeLineList[1].startTime);
                    tmpNode.networkCostPercent = 100 * tmpNode.networkCost / result.traceTree.totalTime;
                    tmpNode.serverCost = (tmpNode.timeLineList[0].startTime + tmpNode.timeLineList[0].cost - tmpNode.timeLineList[1].startTime - tmpNode.timeLineList[1].cost);
                    tmpNode.serverCostPercent = 100 * tmpNode.serverCost / result.traceTree.totalTime;
                } else {
                    tmpNode.case = 4;
                    tmpNode.totalLength = (tmpNode.timeLineList[0].startTime - result.traceTree.startTime);
                    tmpNode.totalLengthPercent = 100 * tmpNode.totalLength / result.traceTree.totalTime;
                    tmpNode.clientCost = tmpNode.timeLineList[1].startTime - tmpNode.timeLineList[0].startTime;
                    tmpNode.clientCostPercent = 100 * tmpNode.clientCost / result.traceTree.totalTime;
                    tmpNode.serverCost = tmpNode.timeLineList[1].startTime + tmpNode.timeLineList[1].cost - tmpNode.timeLineList[1].startTime;
                    tmpNode.serverCostPercent = 100 * tmpNode.serverCost / result.traceTree.totalTime;
                }
            } else {
                tmpNode.case = 5;
                tmpNode.totalLength = (tmpNode.timeLineList[0].startTime - result.traceTree.startTime);
                tmpNode.totalLengthPercent = 100 * tmpNode.totalLength / result.traceTree.totalTime;
                tmpNode.clientCost = tmpNode.timeLineList[0].cost;
                tmpNode.clientCostPercent = 100 * tmpNode.clientCost / result.traceTree.totalTime;
                tmpNode.networkCost = tmpNode.timeLineList[1].startTime - tmpNode.timeLineList[0].startTime;
                tmpNode.networkCostPercent = 100 * tmpNode.networkCost / result.traceTree.totalTime;
                tmpNode.serverCost = tmpNode.timeLineList[1].cost;
                tmpNode.serverCost = 100 * tmpNode.serverCost / result.traceTree.totalTime;
            }
        }

        result.traceTree.treeNodes.push(tmpNode);
    }

    return result;
}

function loadTraceTreeData(baseUrl) {
    var url = baseUrl + "/search/traceId/" + $("#searchKey").val();
    $.ajax({
        type: 'POST',
        url: url,
        dataType: 'json',
        async: true,
        success: function (data) {
            if (data.code == '200') {
                var changedData = changeData(jQuery.parseJSON(data.result));
                var template = $.templates("#traceTreeAllTmpl");
                var htmlOutput = template.render(changedData.traceTree);
                $("#mainPanel").empty();
                $("#mainPanel").html(htmlOutput);
                $("#traceTreeTable").treetable({expandable: true, indent: 10, clickableNodeNames: true});

                $("tr[name='log']").each(function () {
                    var code = $(this).attr("statusCodeStr");
                    if (code != 0 || code == '') {
                        var node = $(this).attr("data-tt-id");
                        $(this).css("color", "red");
                    }
                });
            }
        },
        error: function () {
            $("#errorMessage").text("Fatal Error, please try it again.");
            $("#alertMessageBox").show();
        }
    });
}