<#macro importResources>
<link href="${_base}/node_modules/bootstrap/dist/css/bootstrap.min.css" rel="stylesheet">
<script src="${_base}/node_modules/jquery/dist/jquery.min.js"></script>
<script src="${_base}/node_modules/bootstrap/dist/js/bootstrap.min.js"></script>
</#macro>

<#macro navbar>
<nav class="navbar navbar-default navbar-fixed-top">
    <div class="container">
        <div class="navbar-body">
            <div class="row">
                <div class="col-md-2 col-xs-3 col-sm-2 col-lg-2">
                    <img src="${_base}/node_modules/skywalking/img/logo.png" class="img-responsive center-block">
                </div>
                <div class="col-md-6 col-xs-5 col-sm-6 col-lg-6">
                    <div class="input-group" style="margin-top:3%">
                        <input type="text" class="form-control">
              <span class="input-group-btn">
                  <button class="btn btn-default" type="button">搜索</button>
              </span>
                    </div>
                </div>
                <div class="col-md-3 col-md-offset-1 col-xs-4 col-sm-3 col-lg-3 col-xs-offset-1 col-sm-offset-1 col-lg-offset-1">
                    <#if loginUser??>
                        <div class="dropdown pull-right" style="margin-top: 7%">
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
                    <#else>
                        <ul class="nav navbar-nav navbar-right" style="margin-top:3%">
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
    </div>
</nav>
</#macro>