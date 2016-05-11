<#import "./common/commons.ftl" as common>
<#import "./common/traceInfo.ftl" as traceInfo>
<#import "./usr/applications/applicationMaintain.ftl" as applicationMaintain>
<#import "./usr/authfile/auth.ftl" as auth>
<#import "anls-result/analysisSearchResult.ftl" as anlySearchResult>
<#import "anls-result/analysisResult.ftl" as anlyResult>
<!DOCTYPE html>
<html lang="zh-CN">

<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<@common.importResources />
    <script src="${_base}/bower_components/jquery-ui/jquery-ui.min.js"></script>
    <link rel="stylesheet" href="${_base}/bower_components/jquery-treetable/css/jquery.treetable.theme.default.css"/>
    <script src="${_base}/bower_components/jquery-treetable/jquery.treetable.js"></script>
    <link href="${_base}/bower_components/jquery-treetable/css/jquery.treetable.css" rel="stylesheet"/>
    <link href="${_base}/bower_components/skywalking/css/tracelog.css" rel="stylesheet"/>
    <script src="${_base}/bower_components/skywalking/js/tracelog.js"></script>
    <script src="${_base}/bower_components/skywalking/js/application.js"></script>
    <script src="${_base}/bower_components/skywalking/js/analysisSearchResult.js"></script>
<#--<script src="${_base}/bower_components/skywalking/js/analysisResult.js"></script>-->
    <script src="${_base}/bower_components/skywalking/js/analysisResultViewResovler.js"></script>
    <script src="${_base}/bower_components/smalot-bootstrap-datetimepicker/js/bootstrap-datetimepicker.min.js"></script>
    <link href="${_base}/bower_components/smalot-bootstrap-datetimepicker/css/bootstrap-datetimepicker.min.css"
          rel="stylesheet">
    <link href="${_base}/bower_components/bootstrap-toggle/css/bootstrap-toggle.min.css" rel="stylesheet">
    <script src="${_base}/bower_components/bootstrap-toggle/js/bootstrap-toggle.min.js"></script>
</head>

<body style="padding-top:80px">
<@common.navbar/>
<!--Trace Info -->
<@traceInfo.traceTableTmpl/>
<@traceInfo.traceLogTmpl/>
<@traceInfo.traceTreeAllTmpl/>
<!--Application -->
<@applicationMaintain.applicationList/>
<@applicationMaintain.addApplication/>
<@applicationMaintain.createglobalConfig/>
<@applicationMaintain.modifyApplication/>
<@auth.downloadAuth/>
<@anlySearchResult.anlyResultTmpl/>
<@anlySearchResult.anlyResultDisplayTmpl/>
<@anlySearchResult.pageInfoTmpl/>
<@anlyResult.analysisResult/>
<@anlyResult.analysisResultTableTmpl/>
<@anlyResult.typicalCallChainTrees/>
<@anlyResult.typicalCallChainTreeTable/>
<@anlyResult.typicalCallChainCheckBox/>
<p id="baseUrl" style="display: none">${_base}</p>
<div class="container" id="mainPanel">
    <p id="searchType" style="display: none">${searchType!''}</p>
    <p id="loadType" style="display: none;">${loadType!''}</p>
</div>

<script>
    $(document).ready(function () {
        //
        var loadType = $("#loadType").text();
        loadContent(loadType);
        // bind
        $("#searchBtn").click(function () {
            var searchKey = $("#searchKey").val();
            if (searchKey.match(/viewpoint:*/i)) {
                loadContent("showAnlySearchResult")
            } else if (searchKey.match(/analysisresult:*/i)) {
                loadContent("showAnalysisResult");
            } else {
                loadContent("showTraceInfo");
            }
        })
    });

    var viewResolver;
    function loadContent(loadType, param) {

        if (loadType == "showTraceInfo") {
            loadTraceTreeData("${_base}");
            return;
        }

        if (loadType == "showAnlySearchResult") {
            var template = $.templates("#anlyResultPanelTmpl");
            var htmlOutput = template.render({});
            $("#mainPanel").empty();
            $("#mainPanel").html(htmlOutput);
            var searchKey = $("#searchKey").val();
            var index = searchKey.indexOf(':');
            if (index != -1) {
                searchKey = searchKey.substr(index + 1);
            }
            toSearchAnlyResult(searchKey);
            return;
        }

        if (loadType == "showAnalysisResult") {
            var searchKey = $("#searchKey").val();
            var index = searchKey.indexOf(':');
            if (index != -1) {
                searchKey = searchKey.substr(index + 1);
            }
            viewResolver = new AnalysisResultViewResolver({baseUrl: "${_base}", treeId: searchKey});
            viewResolver.loadMainPage();
            return;
        }

        if (loadType == "applicationList") {
            loadAllApplications();
            return;
        }

        if (loadType == "addApplication") {
            var template = $.templates("#addApplicationTmpl");
            var htmlOutput = template.render({});
            $("#mainPanel").empty();
            $("#mainPanel").html(htmlOutput);
            addApplication();
            return;
        }

        if (loadType == "createGlobalApplication") {
            var template = $.templates("#createGlobalConfigTmpl");
            var htmlOutput = template.render({});
            $("#mainPanel").empty();
            $("#mainPanel").html(htmlOutput);
            createGlobalConfig();
            return;
        }

        if (loadType == "modifyApplication") {
            var template = $.templates("#modifyApplicationTmpl");
            var htmlOutput = template.render({applicationId: param});
            $("#mainPanel").empty();
            $("#mainPanel").html(htmlOutput);
            modifyApplication();
            return;
        }

        if (loadType == "downloadAuthFile") {
            var template = $.templates("#downloadAuthFileTmpl");
            var htmlOutput = template.render({applicationCode: param});
            $("#mainPanel").empty();
            $("#mainPanel").html(htmlOutput);
            toDownloadAuthFile();
            return;
        }


        $("#mainPanel").empty();
    }
</script>
</body>
</html>
