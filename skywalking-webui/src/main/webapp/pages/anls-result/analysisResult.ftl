<#import "../common/commons.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">

<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<@common.importResources />
    <link href="${_base}/bower_components/skywalking/css/tracelog.css" rel="stylesheet"/>
    <link href="${_base}/bower_components/smalot-bootstrap-datetimepicker/css/bootstrap-datetimepicker.min.css"
          rel="stylesheet"/>
    <script src="${_base}/bower_components/smalot-bootstrap-datetimepicker/js/bootstrap-datetimepicker.min.js"></script>
</head>

<body style="padding-top:100px">
<@common.navbar/>
<div class="container" id="mainPanel">
    <div class="row">
        <div class="col-md-4 ">
            <div class="input-group">
                <div class="input-group-btn">
                    <button type="button" class="btn btn-default dropdown-toggle" data-toggle="dropdown"
                            aria-haspopup="true" aria-expanded="false"><span id="analyTypeDropDown">Action</span><span
                            class="caret"></span></button>
                    <ul class="dropdown-menu">
                        <li><a href="javascript:void(0);" name="analyTypeDropDownOption" value="HOUR">时报表</a></li>
                        <li><a href="javascript:void(0);" name="analyTypeDropDownOption" value="DAY">日报表</a></li>
                        <li><a href="javascript:void(0);" name="analyTypeDropDownOption" value="MONTH">月报表</a></li>
                    </ul>
                </div>
                <input type="text" class="form-control" readonly id="analyDate">
            <span class="input-group-btn">
              <button class="btn btn-default" type="button" id="showAnalyResultBtn">Go!</button>
            </span>
            </div>
        </div>
        <div class="col-md-4 col-md-offset-4">
            <span><a href="javascript:void(0);" id="previousHourBtn">上个小时</a></span>&nbsp;&nbsp;&nbsp;&nbsp;
            <span><a href="javascript:void(0);" id="yesterdayBtn">昨天</a></span>&nbsp;&nbsp;&nbsp;&nbsp;
            <span><a href="javascript:void(0);" id="currentMonthBtn">本月</a></span>&nbsp;&nbsp;&nbsp;&nbsp;
            <span><a href="javascript:void(0);" id="previousMonthBtn">上月</a></span>&nbsp;&nbsp;&nbsp;&nbsp;
        </div>
    </div>
    <hr/>
    <div class="row">
        <div>
            <input type="hidden" id="treeId" value="${treeId}"/>
            <input type="hidden" id="analyType" value=""/>
            <table class="gridtable" style="width:100%">
                <thead>
                <tr>
                    <th width="10%">LevelId</th>
                    <th width="62%">ViewPoint</th>
                    <th width="7%">调用次数</th>
                    <th width="7%">正确次数</th>
                    <th width="5%">正确率</th>
                    <th width="7%">平均耗时</th>
                </tr>
                </thead>
                <tbody id="dataBody">
                </tbody>
            </table>
        </div>
    </div>
    <script type="text/x-jsrender" id="analysisResultTableTmpl">
        <tr id="a">
            {{if isPrintLevelId}}
                <td rowspan="{{>rowSpanCount}}" valign="middle">{{>traceLevelId}}</td>
            {{/if}}
            <td><a href="#">{{>viewPoint}}<a></td>
            <td>{{>anlyResult.totalCall}}</td>
            <td>{{>anlyResult.correctNumber}}</td>
            <td>
            <span class="
         {{if anlyResult.correctRate >= 99.00}}
         text-success
         {{else anlyResult.correctRate >= 97}}
         text-warning
         {{else}}
         text-danger
         {{/if}}
         ">
            <strong>{{>anlyResult.correctRate}}%</strong></span></td>
            <td>{{>anlyResult.totalCostTime}}ms</td>
        </tr>
    </script>
    <script>
        $(document).ready(function () {
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

            $("a[name='analyTypeDropDownOption'][value='DAY']").click();

            $("#showAnalyResultBtn").click(function () {
                paintAnalysisResult($("#treeId").val(), $("#analyType").val(), $("#analyDate").val())
            });
        });

        function paintAnalysisResult(treeId, analyType, analyDate) {
            var url = "${_base}/analy/load/" + treeId + "/" +
                    analyType + "/" + analyDate;
            $.ajax({
                type: 'POST',
                url: url,
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
        }

        function convertAnalysisResult(originData) {
            if (originData == undefined || originData.callChainTreeNodeList == undefined) {
                return [];
            }
            var previousNodeLevelId = "";
            var index = 0;
            var count = 1;
            for (var i = 0; i < originData.callChainTreeNodeList.length; i++) {
                var node = originData.callChainTreeNodeList[i];
                if (previousNodeLevelId == node.traceLevelId) {
                    if (count == 1) {
                        index = i - 1;
                    }
                    count++;
                    originData.callChainTreeNodeList[i].isPrintLevelId = false;
                } else {
                    if (count > 1) {
                        originData.callChainTreeNodeList[index].rowSpanCount = count;
                    } else {
                        originData.callChainTreeNodeList[i].rowSpanCount = count;
                    }
                    count = 1;
                    originData.callChainTreeNodeList[i].isPrintLevelId = true;
                }
                if (node.anlyResult.totalCall > 0) {
                    node.anlyResult.correctRate =
                            (parseFloat(node.anlyResult.correctNumber)
                            / parseFloat(node.anlyResult.totalCall) * 100).toFixed(2);
                }else{
                    node.anlyResult.correctRate = (0).toFixed(2);
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
    </script>
</div>
</body>
</html>