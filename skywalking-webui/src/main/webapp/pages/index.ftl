<#import "./common/commons.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<@common.importResources />
    <title>Skywalking</title>
</head>
<body>
<div class="navbar">
    <div class="container">
        <div class="navbar-header">
            <button data-target=".navbar-collapse" data-toggle="collapse" type="button" class="navbar-toggle collapsed">
                <span class="sr-only">Toggle navigation</span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
            </button>
        </div>
        <div role="navigation" class="navbar-collapse collapse">
        <#if loginUser??>
            <div class="col-md-1 col-md-offset-10 pull-right">
                <div class="row" style="margin-top:7%">
                    <div class="dropdown">
                        <button class="btn btn-default dropdown-toggle" type="button" id="dropdownMenu1"
                                data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">
                        ${loginUser.userName}
                            <span class="caret"></span>
                        </button>
                        <ul class="dropdown-menu" aria-labelledby="dropdownMenu1">
                            <li><a href="${_base}/mainPage?loadType=applicationList">Edit Applications</a></li>
                            <li role="separator" class="divider"></li>
                            <li><a href="${_base}/mainPage?loadType=addApplication">Add Application</a></li>
                            <li role="separator" class="divider"></li>
                            <li><a href="javascript:void(0);" id="logoutBtn">Sign Out</a></li>
                        </ul>
                    </div>
                </div>
            </div>
        <#else >
            <ul class="nav navbar-nav navbar-right hidden-sm">
                <li>
                    <a onclick="javascript:void(0);" href="${_base}/usr/login">
                        <ins>sign in</ins>
                    </a>
                </li>
                <li>
                    <a onclick="javascript:void(0);" href="${_base}/usr/register">
                        <ins>sign up</ins>
                    </a>
                </li>
            </ul>
        </#if>
        </div>
    </div>
</div>
<div class="container-fluid">
    <div class="row">
        <div class="col-md-4 col-md-offset-4">
            <img src="${_base}/bower_components/skywalking/img/logo.png" class="img-responsive center-block"/>
        </div>
        <div class="input-group col-md-6 col-md-offset-3 col-">
            <input type="text" class="form-control" id="key">
            <a class="input-group-addon btn" href="javascript:void(0);" id="searchBtn">Search</a>
        </div>
    </div>
</div>
<script>
    $(document).ready(function () {
        $("#searchBtn").click(function () {
            var searchKey = $("#key").val();
            if (searchKey.match(/viewpoint:*/i)) {
                window.location.href = "${_base}/mainPage?loadType=showAnlySearchResult&key=" + searchKey;
            }else if (searchKey.match(/analysisresult:*/i)){
                window.location.href = "${_base}/mainPage?loadType=showAnalysisResult&key=" + searchKey;
            } else {
                window.location.href = "${_base}/" + searchKey;
            }
        });

        $("#logoutBtn").click(function () {
            var url = "${_base}/usr/doLogout";
            $.ajax({
                type: 'POST',
                url: url,
                dataType: 'json',
                async: true,
                success: function (data) {
                    if (data.code == '200') {
                        location.href = "${_base}/index";
                    }
                },
                error: function () {
                    $("#errorMessage").text("Fatal Error, please try it again.");
                    $("#alertMessageBox").show();
                }
            });
        });
    })
</script>
</body>
</html>
