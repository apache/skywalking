function initAnalysisResult() {


    $('#analyDate').datetimepicker({
        format: 'yyyy-mm-dd',
        startView: 2,
        minView: 2,
        autoclose: true
    });

    $("#previousHourBtn").click(function () {
        var analyType = "HOUR";
        var analyDate = getPreviousHour();
        paintAnalysisResult($("#treeId").val(), analyType, analyDate)
    });

    $("#yesterdayBtn").click(function () {
        var analyType = "DAY";
        var analyDate = getYesterday();
        paintAnalysisResult($("#treeId").val(), analyType, analyDate)
    });

    $("#currentMonthBtn").click(function () {
        var analyType = "MONTH";
        var analyDate = getCurrentMonth();
        paintAnalysisResult($("#treeId").val(), analyType, analyDate)
    });

    $("#previousMonthBtn").click(function () {
        var analyType = "MONTH";
        var analyDate = getPreviousMonth();
        paintAnalysisResult($("#treeId").val(), analyType, analyDate)
    });


    $("a[name='analyTypeDropDownOption']").each(function () {
        $(this).click(function () {
            $('#analyDate').val("");
            $("#analyTypeDropDown").text($(this).text());
            var value = $(this).attr("value");
            var modelView = 2;
            var formatStr = 'yyyy-mm-dd';
            if (value == "HOUR") {
                formatStr = 'yyyy-mm-dd:hh';
                modelView = 1;
            } else if (value == "DAY") {
                formatStr = 'yyyy-mm-dd';
                modelView = 2;
            } else if (value == "MONTH") {
                formatStr = 'yyyy-mm';
                modelView = 3;
            } else {
                formatStr = 'yyyy-mm-dd';
                modelView = 2;
            }
            $('#analyDate').datetimepicker('remove');
            $('#analyDate').datetimepicker({
                format: formatStr,
                startView: modelView,
                minView: modelView,
                autoclose: true
            });

            $("#analyType").val(value);
        });
    });

    $("a[name='analyTypeDropDownOption'][value='MONTH']").click();
    $("#analyDate").val(getCurrentMonth());

    $("#showAnalyResultBtn").click(function () {
        paintAnalysisResult($("#treeId").val(), $("#analyType").val(), $("#analyDate").val())
    });

    $("#showAnalyResultBtn").click();
}

function paintAnalysisResult(treeId, analyType, analyDate) {
    var baseUrl = $("#baseUrl").text();
    var analysisResultUrl = baseUrl + "/analy/load/" + treeId + "/" +
        analyType + "/" + analyDate;
    $.ajax({
        type: 'POST',
        url: analysisResultUrl,
        dataType: 'json',
        async: true,
        success: function (data) {
            if (data.code == '200') {
                var dataResult = convertAnalysisResult(jQuery.parseJSON(data.result));
                var template = $.templates("#analysisResultTableTmpl");
                var htmlOutput = template.render(dataResult);
                $("#dataBody").empty();
                $("#dataBody").html(htmlOutput);
            }
        },
        error: function () {
            $("#errorMessage").text("Fatal Error, please try it again.");
            $("#alertMessageBox").show();
        }
    });
    var typicalCallUrl = baseUrl + "/analy/load/" + treeId + "/" + analyDate;

    $.ajax({
        type: 'POST',
        url: typicalCallUrl,
        dataType: 'json',
        async: true,
        success: function (data) {
            if (data.code == '200') {
                //data.result
            }
        },
        error: function () {
            $("#errorMessage").text("Fatal Error, please try it again.");
            $("#alertMessageBox").show();
        }
    });
}

function convertAnalysisResult(originData) {
    if (originData == undefined || originData.callChainTreeNodeList == undefined) {
        return [];
    }
    var previousNodeLevelId = "";
    var index = -1;
    var count = 1;
    var flag = false;
    for (var i = 0; i < originData.callChainTreeNodeList.length; i++) {
        var node = originData.callChainTreeNodeList[i];

        if (previousNodeLevelId == node.traceLevelId) {
            if (count == 1) {
                index = i - 1;
            }
            count++;
            flag = true;
        }

        if (flag){
            originData.callChainTreeNodeList[i].isPrintLevelId = false;
            originData.callChainTreeNodeList[index].rowSpanCount = count;
            flag = false;
        }else{
            count = 1;
            originData.callChainTreeNodeList[i].rowSpanCount = count;
            originData.callChainTreeNodeList[i].isPrintLevelId = true;
        }

        if (node.anlyResult.totalCall > 0) {
            node.anlyResult.correctRate =
                (parseFloat(node.anlyResult.correctNumber)
                / parseFloat(node.anlyResult.totalCall) * 100).toFixed(2);

            node.anlyResult.averageCost =
                (parseFloat(node.anlyResult.totalCostTime)
                / parseFloat(node.anlyResult.totalCall)).toFixed(2);
        } else {
            node.anlyResult.correctRate = (0).toFixed(2);
            node.anlyResult.averageCost = (0).toFixed(2);
        }
        previousNodeLevelId = node.traceLevelId;
    }

    return originData.callChainTreeNodeList;
}

function getPreviousHour() {
    var date = new Date();
    var seperator1 = "-";
    var seperator2 = ":";
    var month = date.getMonth() + 1;
    var strDate = date.getDate();
    if (month >= 1 && month <= 9) {
        month = "0" + month;
    }
    if (strDate >= 0 && strDate <= 9) {
        strDate = "0" + strDate;
    }
    return date.getFullYear() + seperator1 + month + seperator1 + strDate + seperator2 + (date.getHours() - 1);
}

function getYesterday() {
    var date = new Date();
    var seperator1 = "-";
    var month = date.getMonth() + 1;
    var strDate = date.getDate() - 1;
    if (month >= 1 && month <= 9) {
        month = "0" + month;
    }
    if (strDate >= 0 && strDate <= 9) {
        strDate = "0" + strDate;
    }
    return date.getFullYear() + seperator1 + month + seperator1 + strDate;
}

function getCurrentMonth() {
    var date = new Date();
    var seperator1 = "-";
    var month = date.getMonth() + 1;
    if (month >= 1 && month <= 9) {
        month = "0" + month;
    }
    return date.getFullYear() + seperator1 + month;
}

function getPreviousMonth() {
    var date = new Date();
    var seperator1 = "-";
    var month = date.getMonth();
    var year = date.getFullYear();
    if (month == 0) {
        year = date.getFullYear() - 1;
        month = 12;
    }
    if (month >= 1 && month <= 9) {
        month = "0" + month;
    }
    return year + seperator1 + month;
}