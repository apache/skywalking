<#import "./common/commons.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<@common.importResources />
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
                            <li><a href="${_base}/usr/applications/list">系统配置</a></li>
                            <li role="separator" class="divider"></li>
                            <li><a href="${_base}/usr/applications/add">新增应用</a></li>
                            <li role="separator" class="divider"></li>
                            <li><a href="">退出</a></li>
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
        <div class="input-group col-md-6 col-md-offset-3">
            <input type="text" class="form-control">
            <a class="input-group-addon btn btn-primary" href="./searchResult.html">搜索</a>
        </div>
    </div>
</div>
</body>
</html>
