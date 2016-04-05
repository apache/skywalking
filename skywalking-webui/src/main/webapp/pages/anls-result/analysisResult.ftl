<#import "../common/commons.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">

<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<@common.importResources />
    <script src="${_base}/bower_components/jsrender/jsrender.min.js"></script>
    <script src="${_base}/bower_components/jquery-ui/jquery-ui.min.js"></script>
    <link href="${_base}/bower_components/jquery-treetable/css/jquery.treetable.css" rel="stylesheet" type="text/css"/>
    <link rel="stylesheet" href="${base}/bower_components/jquery-treetable/css/jquery.treetable.theme.default.css"/>
    <script src="${_base}/bower_components/jquery-treetable/jquery.treetable.js"></script>
    <link href="${_base}/bower_components/jquery-treetable/css/jquery.treetable.css" rel="stylesheet"/>
    <link href="${_base}/bower_components/skywalking/css/tracelog.css" rel="stylesheet"/>
    <script src="${_base}/bower_components/skywalking/js/tracelog.js"></script>
</head>

<body style="padding-top:80px">
<@common.navbar/>
<div class="container" id="mainPanel">
    <div class="row">
        <div class="col-md-9">

        </div>
        <div class="col-md-3">

        </div>
    </div>
</div>

<script>
    $(document).ready(function () {
        loadAnalysisResult();
    });

    function loadAnalysisResult(){

    }
</script>
</body>
</html>
