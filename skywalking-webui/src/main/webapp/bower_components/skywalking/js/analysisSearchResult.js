function toSearchAnlyResult(searchKey) {
    loadAnalyResult(searchKey, 1);
}

function bindPagerBtn() {
    $("#doPreviousPageBtn").click(function () {
        var pageSize = $("#pageSize").val();
        $("#anlyResultmPanel").empty();
        loadAnalyResult(parseInt(pageSize) - 1);
    });

    $("#doNextPageBtn").click(function () {
        var pageSize = $("#pageSize").val();
        $("#anlyResultmPanel").empty();
        loadAnalyResult(parseInt(pageSize) + 1);
    });
}

function loadAnalyResult(searchKey, pageSize) {
    var baseUrl = $("#baseUrl").text();
    var url = baseUrl + '/search/chainTree?key=' + searchKey;
    $.ajax({
        type: 'POST',
        url: url,
        dataType: 'json',
        data: {
            pageSize: pageSize
        },
        success: function (data) {
            if (data.code == '200') {
                var template = $.templates("#anlyResultDisplayTmpl");
                var jsonValue = JSON.parse(data.result);
                var showResult = jsonValue.children;
                if (showResult != undefined && showResult.length > 0) {
                    for (var i = 0; i < showResult.length; i++) {
                        var tmpNodes = showResult[i].nodes;
                        var preNodesTraceleveId = "0";
                        for (var j = 0; j < tmpNodes.length; j++) {
                            //preNodeLenght = spiltArray.length;
                            tmpNodes[j].isPrintSlipDot = !compareTraceLevelId(preNodesTraceleveId, tmpNodes[j].traceLevelId);
                            if (tmpNodes[j].isPrintSlipDot) {
                                tmpNodes[j].marginLeftSize = 0;
                            } else {
                                var spiltArray = tmpNodes[j].traceLevelId.split(".");
                                var preSplitArray = preNodesTraceleveId.split(".");
                                var flag = false;
                                var length = spiltArray.length;
                                if (length > preSplitArray.length) {
                                    length = preSplitArray.length;
                                    flag = true;
                                }
                                if (preSplitArray[length - 1] == spiltArray[length - 1]) {
                                    if (flag){
                                        tmpNodes[j].marginLeftSize = 10;
                                    }else{
                                        tmpNodes[j].marginLeftSize = 0;
                                    }
                                } else if (preSplitArray[length - 1] < spiltArray[length - 1]) {
                                    //异常情况
                                    tmpNodes[j].marginLeftSize = 0;
                                } else {
                                    tmpNodes[j].marginLeftSize = 10;
                                }
                            }
                            preNodesTraceleveId = tmpNodes[j].traceLevelId;
                        }
                        if (showResult[i].entranceAnlyResult.totalCall != 0) {
                            showResult[i].correctRate = (parseFloat(showResult[i].entranceAnlyResult.correctNumber)
                            / parseFloat(showResult[i].entranceAnlyResult.totalCall) * 100).toFixed(2);
                        } else {
                            showResult[i].correctRate = (0).toFixed(2);
                        }
                    }

                    var htmlOutput = template.render(jsonValue.children);
                    $("#anlyResultmPanel").empty();
                    $("#anlyResultmPanel").html(htmlDecode(htmlOutput));

                    template = $.templates("#pageInfoTmpl");
                    var hasPreviousPage = true;
                    if (pageSize == 1) {
                        hasPreviousPage = false;
                    }
                    htmlOutput = template.render({
                        "pageSize": pageSize,
                        "hasPreviousPage": hasPreviousPage,
                        "hasNextPage": jsonValue.hasNextPage
                    });
                    $("#anlyResultmPanel").append(htmlOutput);
                    bindPagerBtn();
                }
            }
        },
        error: function () {
            $("#errorMessage").text("Fatal Error, please try it again.");
            $("#alertMessageBox").show();
        }
    });
}
function htmlDecode(str) {
    var _str = '';
    if (str.length == 0) return '';
    _str = str.replace(/&lt;/g, '<');
    _str = _str.replace(/&gt;/g, '>');
    _str = _str.replace(/&#39;/g, "'");
    return _str;
}

function compareTraceLevelId(preTraceLevelId, currentTraceLevelId) {
    var preLevelArray = preTraceLevelId.split('.');
    var curLevelArray = currentTraceLevelId.split('.');

    if (Math.abs(preLevelArray.length - curLevelArray.length) > 1) {
        return false;
    }

    var length = preLevelArray.length;
    if (curLevelArray.length > length) {
        length = curLevelArray.length;
    }

    var result = true;
    for (var index = 0; index < length; index++) {
        var value = parseInt(preLevelArray[index]) - parseInt(curLevelArray[index]);
        if (value < -1) {
            result = false;
            break;
        }

        if (value == -1) {
            if (preLevelArray.length < index + 1 && curLevelArray[index + 1] != "0") {
                result = false;
                break;
            }
        }
    }

    return result;
}