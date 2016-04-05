<#import "../../common/commons.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">

<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<@common.importResources />
    <title>All Applications configuration</title>
    <script src="${_base}/bower_components/vue/dist/vue.min.js"></script>
</head>

<body style="padding-top:80px">
<@common.navbar/>
<div class="container">
    <div class="row" style="display: none;" id="errorMessageAlert">
        <div class="col-md-8">
            <div class="row">
                <div class="alert alert-warning alert-dismissible" role="alert">
                    <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                            aria-hidden="true">&times;</span></button>
                    <strong>Warning!</strong>&nbsp;<p id="errormessage"></p>
                    </a>.
                </div>
            </div>
        </div>
    </div>
    <div class="row">
        <div class="col-md-8">
            <div class="row">
                <table class="table table-hover">
                    <thead>
                    <tr>
                        <th>序号</th>
                        <th>应用编码</th>
                        <th>创建时间</th>
                        <th>更新时间</th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody id="allApplications">
                    <tr v-for="application in applications">
                        <td>{{ $index }}</td>
                        <td>{{application.appCode}}</td>
                        <td>{{application.createTime}}</td>
                        <td>{{application.updateTime}}</td>
                        <td>
                            <a class="btn btn-xs"
                               href="${_base}/usr/applications/modify/{{application.appId}}">Update</a>
                            <a class="btn btn-danger btn-xs" href="javascript:void(0)"
                               onclick="del('{{application.appId}}')">Delete</a>
                            <a class="btn btn-info btn-xs"
                               href="${_base}/usr/applications/authfile/todownload/{{application.appCode}}">Download
                                auth File</a>
                        </td>
                    </tr>
                    </tbody>
                </table>
            </div>
        </div>
        <div class="col-md-4">
        </div>
    </div>
</div>
<script>
    $(document).ready(function () {
        loadData();
    });

    function del(applicationId) {
        var url = "${_base}/usr/applications/del/" + applicationId;
        $.ajax({
            type: 'POST',
            url: url,
            dataType: 'json',
            success: function (data) {
                if (data.code == 200) {
                    $("#allApplications").empty();
                    loadData();
                } else {
                    $("#errormessage").text(data.message);
                    $("#errorMessageAlert").show();
                }
            },
            error: function () {
                $("#errormessage").text("Fatal error");
                $("#errorMessageAlert").show();
            }
        });
    }

    function loadData() {
        var url = "${_base}/usr/applications/all";
        $.ajax({
            type: 'POST',
            url: url,
            dataType: 'json',
            success: function (data) {
                if (data.code == 200) {
                    new Vue({
                        el: "#allApplications",
                        data: {
                            applications: jQuery.parseJSON(data.result)
                        }
                    });
                } else {
                    $("#errormessage").text(data.message);
                    $("#errorMessageAlert").show();
                }
            },
            error: function () {
                $("#errormessage").text("Fatal error");
                $("#errorMessageAlert").show();
            }
        });
    }
</script>
</body>
</html>
