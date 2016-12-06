<#macro importResources>
<link href="${_base}/bower_components/bootstrap/dist/css/bootstrap.min.css" rel="stylesheet"/>
<link href="${_base}/bower_components/bootstrap/dist/css/bootstrap-theme.min.css" rel="stylesheet"/>
<script src="${_base}/bower_components/jquery/dist/jquery.min.js"></script>
<script src="${_base}/bower_components/bootstrap/dist/js/bootstrap.min.js"></script>
<script src="${_base}/bower_components/jsrender/jsrender.min.js"></script>
</#macro>

<#macro navbar>
<nav class="navbar navbar-default navbar-fixed-top">
    <div class="container">
        <div class="navbar-body">
            <div class="row">
                <div class="col-md-2 col-xs-3 col-sm-2 col-lg-2">
                    <a href="${_base}/index">
                    <img src="${_base}/bower_components/skywalking/img/logo.png" class="img-responsive center-block">
                    </a>
                </div>
                <div class="col-md-6 col-xs-5 col-sm-6 col-lg-6">
                    <div class="input-group" style="margin-top:3%">
                        <input type="text" class="form-control" value="${key!''}" id="searchKey">
                        <span class="input-group-btn">
                            <button class="btn btn-default" type="button" id="searchBtn">Search</button>
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
                                <li><a href="javascript:void(0);" onclick="loadContent('applicationList')">Edit Applications</a></li>
                                <li role="separator" class="divider"></li>
                                <li><a href="javascript:void(0);" onclick="loadContent('addApplication')">Add Application</a></li>
                                <li role="separator" class="divider"></li>
                                <li><a href="javascript:void(0);" id="logoutBtn">Sign Out</a></li>
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
<script>
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
    })
</script>
</#macro>
