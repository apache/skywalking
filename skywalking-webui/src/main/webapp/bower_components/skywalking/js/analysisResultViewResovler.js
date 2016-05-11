function AnalysisResultViewResolver(param) {
    AnalysisResultViewResolver.prototype.baseUrl = param.baseUrl;
    AnalysisResultViewResolver.prototype.treeId = param.treeId;
    this.changetAnalyCondition("MONTH", this.getCurrentMonth());
}

AnalysisResultViewResolver.prototype.callChainTreeData = [];
AnalysisResultViewResolver.prototype.callEntrance = {};
AnalysisResultViewResolver.prototype.typicCallChainData = [];

AnalysisResultViewResolver.prototype.analyType = "MONTH";
AnalysisResultViewResolver.prototype.analyDate = "";

AnalysisResultViewResolver.prototype.currentTypicalTreeNodes = {callChainTreeNodeList: []};
AnalysisResultViewResolver.prototype.currentTypicalTreeNodeMapping = {typicalTreeIds: []};


AnalysisResultViewResolver.prototype.paintChainTreeMainPage = function () {
    var template = $.templates("#analysisResultPanelTmpl");
    var htmlOutput = template.render({treeId: this.treeId});
    $("#mainPanel").empty();
    $("#mainPanel").html(htmlOutput);
}

AnalysisResultViewResolver.prototype.loadMainPage = function () {
    this.paintChainTreeMainPage();
    this.bindEvent();

    $("a[name='analyTypeDropDownOption'][value='" + this.analyType + "']").click();
    $("#analyDate").val(this.analyDate);
    $("#showAnalyResultBtn").click();
}

AnalysisResultViewResolver.prototype.reloadMainPage = function () {
    this.paintChainTreeMainPage();
    this.bindEvent();

    $("a[name='analyTypeDropDownOption'][value='" + this.analyType + "']").click();
    $("#analyDate").val(this.analyDate);

    this.paintChainTreeDataTable();
    this.bindGotoTypicalPageEvent();
}

AnalysisResultViewResolver.prototype.changetAnalyCondition = function (type, date) {
    this.analyDate = date;
    this.analyType = type;
}

AnalysisResultViewResolver.prototype.bindEvent = function () {
    var self = this;
    $('#analyDate').datetimepicker({
        format: 'yyyy-mm-dd',
        startView: 2,
        minView: 2,
        autoclose: true
    });

    this.bindHotSearchLink();
    this.bindEventForSearchDateInput();

    $("#showAnalyResultBtn").click(function () {
        self.loadData($("#analyType").val(), $("#analyDate").val());
    });
}

AnalysisResultViewResolver.prototype.showTypicalCallTree = function (nodeToken) {
    this.currentTypicalTreeNodes = {callChainTreeNodeList: []};
    this.currentTypicalTreeNodeMapping = {typicalTreeIds: []};
    for (var i = 0; i < this.typicCallChainData.length; i++) {
        var node = this.typicCallChainData[i];
        var tmpInfo = node.treeNodes[nodeToken];
        if (tmpInfo == undefined || tmpInfo == "") {
            continue;
        }

        var tmpTypicalCallChain = [];
        for (var key in node.treeNodes) {
            var tmpNode = node.treeNodes[key];
            tmpNode.anlyResult = JSON.parse($("#" + key).text());
            tmpTypicalCallChain.push(tmpNode);
            this.currentTypicalTreeNodes.callChainTreeNodeList.push(tmpNode);
        }
        this.currentTypicalTreeNodeMapping[node.callTreeId] = tmpTypicalCallChain;
        this.currentTypicalTreeNodeMapping.typicalTreeIds.push({"callTreeToken": node.callTreeId});
    }
}

AnalysisResultViewResolver.prototype.paintChainTreeDataTable = function () {
    var template = $.templates("#analysisResultTableTmpl");
    var htmlOutput = template.render(this.callChainTreeData);
    $("#dataBody").empty();
    $("#dataBody").html(htmlOutput);
}

AnalysisResultViewResolver.prototype.loadData = function (analyType, analyDate) {
    var self = this;
    var analysisResultUrl = this.baseUrl + "/analy/load/" + this.treeId + "/" +
        analyType + "/" + analyDate;
    $.ajax({
        type: 'POST',
        url: analysisResultUrl,
        dataType: 'json',
        async: true,
        success: function (data) {
            if (data.code == '200') {
                self.callChainTreeData = self.convertAnalysisResult(jQuery.parseJSON(data.result));
                for (var i = 0; i < self.callChainTreeData.length; i++) {
                    var node = self.callChainTreeData[i];
                    if (node.traceLevelId == "0") {
                        self.callEntrance = node.viewPoint;
                        break;
                    }
                }

                self.paintChainTreeDataTable();
                self.bindGotoTypicalPageEvent();
            }
        },
        error: function () {
            $("#errorMessage").text("Fatal Error, please try it again.");
            $("#alertMessageBox").show();
        }
    });

    var typicCallTreeUrl = this.baseUrl + "/analy/load/" + this.treeId + "/" + analyDate;
    $.ajax({
        type: 'POST',
        url: typicCallTreeUrl,
        dataType: 'json',
        async: true,
        success: function (data) {
            if (data.code == '200') {
                self.typicCallChainData = jQuery.parseJSON(data.result);
            }
        },
        error: function () {
            $("#errorMessage").text("Fatal Error, please try it again.");
            $("#alertMessageBox").show();
        }
    })
}

AnalysisResultViewResolver.prototype.bindGotoTypicalPageEvent = function () {
    var self = this;
    $("button[name='showTypicalCallTreeBtn']").each(function () {
        $(this).click(function () {
            var treeNodeToken = $(this).attr("value");
            $(".modal-backdrop").remove();
            self.showTypicalCallTree(treeNodeToken);

            var template = $.templates("#typicalCallChainTreesTmpl");
            var htmlOutput = template.render({
                "entryViewPoint": self.callEntrance,
                "currentViewPoint": $("#" + treeNodeToken + "ViewPoint").text()
            });
            $("#mainPanel").empty();
            $("#mainPanel").html(htmlOutput);

            template = $.templates("#typicalTreeCheckBoxTmpl");
            htmlOutput = template.render({"typicalTreeIds": self.currentTypicalTreeNodeMapping.typicalTreeIds});
            $("#typicalCheckBoxDiv").empty();
            $("#typicalCheckBoxDiv").html(htmlOutput);

            $("input[name='typicalTreeCheckBox']").each(function () {
                $(this).change(function () {
                    var treeIds = new Array();
                    $("input[name='typicalTreeCheckBox']").each(function () {
                        if ($(this).prop("checked")) {
                            treeIds.push($(this).attr("value"));
                        }
                    });

                    self.currentTypicalTreeNodes.callChainTreeNodeList = [];
                    for (var i = 0; i < treeIds.length; i++) {
                        var tmpNodes = self.currentTypicalTreeNodeMapping[treeIds[i]];
                        for (var j = 0; j < tmpNodes.length; j++) {
                            self.currentTypicalTreeNodes.callChainTreeNodeList.push(tmpNodes[j]);
                        }
                    }

                    template = $.templates("#typicalTreeTableTmpl");
                    var htmlOutput = template.render((self.convertAnalysisResult(self.currentTypicalTreeNodes)));
                    $("#typicalTreeTableDataBody").empty();
                    $("#typicalTreeTableDataBody").html(htmlOutput);

                });
            });

            template = $.templates("#typicalTreeTableTmpl");
            var htmlOutput = template.render((self.convertAnalysisResult(self.currentTypicalTreeNodes)));
            $("#typicalTreeTableDataBody").empty();
            $("#typicalTreeTableDataBody").html(htmlOutput);

            $("#rebackCallChainTreeBtn").click(function () {
                self.reloadMainPage();
            });
        })
    });
}

AnalysisResultViewResolver.prototype.convertAnalysisResult = function (originData) {
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

        if (flag) {
            originData.callChainTreeNodeList[i].isPrintLevelId = false;
            originData.callChainTreeNodeList[index].rowSpanCount = count;
            flag = false;
        } else {
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
        node.anlyResultStr = JSON.stringify(node.anlyResult);
        previousNodeLevelId = node.traceLevelId;
    }

    return originData.callChainTreeNodeList;
}

AnalysisResultViewResolver.prototype.getPreviousHour = function () {
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

AnalysisResultViewResolver.prototype.getYesterday = function () {
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

AnalysisResultViewResolver.prototype.getCurrentMonth = function () {
    var date = new Date();
    var seperator1 = "-";
    var month = date.getMonth() + 1;
    if (month >= 1 && month <= 9) {
        month = "0" + month;
    }
    return date.getFullYear() + seperator1 + month;
}

AnalysisResultViewResolver.prototype.getPreviousMonth = function () {
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

AnalysisResultViewResolver.prototype.bindEventForSearchDateInput = function () {
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
}

AnalysisResultViewResolver.prototype.bindHotSearchLink = function () {
    var self = this;
    $("#previousHourBtn").click(function () {
        $("a[name='analyTypeDropDownOption'][value='HOUR']").click();
        $("#analyDate").val(self.getPreviousHour());
        self.changetAnalyCondition("HOUR", self.getPreviousHour());
        $("#showAnalyResultBtn").click();
    });

    $("#yesterdayBtn").click(function () {
        $("a[name='analyTypeDropDownOption'][value='DAY']").click();
        $("#analyDate").val(self.getYesterday());
        self.changetAnalyCondition("DAY", self.getYesterday());
        $("#showAnalyResultBtn").click();
    });

    $("#currentMonthBtn").click(function () {
        $("a[name='analyTypeDropDownOption'][value='MONTH']").click();
        $("#analyDate").val(self.getCurrentMonth());
        self.changetAnalyCondition("MONTH", self.getCurrentMonth());
        $("#showAnalyResultBtn").click();
    });

    $("#previousMonthBtn").click(function () {
        $("a[name='analyTypeDropDownOption'][value='MONTH']").click();
        $("#analyDate").val(self.getPreviousMonth());
        self.changetAnalyCondition("MONTH", self.getPreviousMonth());
        $("#showAnalyResultBtn").click();
    });
}