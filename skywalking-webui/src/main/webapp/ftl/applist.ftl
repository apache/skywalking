<#import "./lib/ai.cloud/common.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta http-equiv="content-type" content="text/html; charset=UTF-8">
    <meta charset="utf-8">
    <title>Sky Walking</title>
    <meta name="generator" content="Bootply" />
    <meta name="viewport"
          content="width=device-width, initial-scale=1, maximum-scale=1">
    <link href="${base}/css/bootstrap.min.css" rel="stylesheet">
    <!--[if lt IE 9]>
    <script src="//html5shim.googlecode.com/svn/trunk/html5.js"></script>
    <![endif]-->
    <link href="${base}/css/login.css" rel="stylesheet">
</head>
<body>
<div class="container">
    <!-- 创建应用-->
    <div id="createAppDiv" style="display:none" class="form-horizontal">
        <div class="form-group">
            <label for="appCode" class="col-sm-4 control-label">应用名称：</label>
            <div class="col-sm-4">
                <input id="appCode" type="text" class="form-control" placeholder="应用名称"
                       autofocus>
            </div>
        </div>
        <div class="form-group">
            <div class="col-sm-offset-4 col-sm-4">
                <button id="crtBtn" class="btn btn-lg btn-primary" type="button">创建应用</button>
                <button id="cannelBtn" class="btn btn-lg btn-primary" type="button">取消创建</button>
            </div>
        </div>
    </div>
    <!-- 创建规则-->
    <div class="form-horizontal" id="crtAlarmDiv" style="display:none">
        <div class="form-group">
            <label class="col-sm-4 control-label">应用名称：</label>
            <div class="col-sm-4">
                <p class="form-control-static" id="appName"></p>
            </div>
        </div>
        <div class="form-group">
            <label for="period" class="col-sm-4 control-label">告警频率：</label>
            <div class="col-sm-4">
                <input type="text" class="form-control" id="period" placeholder="告警发送时间间隔(秒)">
            </div>
        </div>
        <div class="form-group">
            <label for="todoType" class="col-sm-4 control-label">告警操作：</label>
            <div class="col-sm-4">
                <select class="form-control" id="todoType" >
                    <option value="0">发送邮件</option>
                    <option value="1">回调接口</option>
                </select>
            </div>
        </div>
        <div id="mailTempDiv" style="display:block">
            <div class="form-group">
                <label for="mailTemp" class="col-sm-4 control-label">收件人地址：</label>
                <div class="col-sm-4">
                    <textarea class="form-control" id="mailTo" rows="3" placeholder="收件人地址，请英文逗号,分割"></textarea>
                </div>
            </div>
            <div class="form-group">
                <label for="mailTemp" class="col-sm-4 control-label">抄送人地址：</label>
                <div class="col-sm-4">
                    <textarea class="form-control" id="mailCc" rows="3" placeholder="抄送人地址，请英文逗号,分割"></textarea>
                </div>
            </div>
            <div class="form-group">
                <label for="mailTemp" class="col-sm-4 control-label">邮件模板：</label>
                <div class="col-sm-4">
                    <textarea class="form-control" id="mailTemp" rows="4" placeholder="邮件模板"></textarea>
                </div>
            </div>
        </div>
        <div class="form-group" id="callBackDiv" style="display:none">
            <label for="urlCall" class="col-sm-4 control-label">回调接口：</label>
            <div class="col-sm-4">
                <input type="text" class="form-control" id="urlCall" placeholder="回调接口地址">
            </div>
        </div>
        <div class="form-group">
            <div class="col-sm-offset-4 col-sm-4">
                <input type='hidden' id="ruleId">
                <input type='hidden' id="appId">
                <input type='hidden' id="isGlobal">
                <button id="crtRuleBtn" class="btn btn-lg btn-primary " type="button">创建规则</button>
                <button id="cannelRuleBtn" class="btn btn-lg btn-primary " type="button">取消创建</button>
            </div>
        </div>
    </div>
    <div class="form-horizontal" id="authInfo" style="display:none">
        <div class="form-group">
            <label for="period" class="col-sm-4 control-label">需要排除的异常</label>
            <div class="col-sm-4">
                <input type="text" class="form-control" id="exclusiveException" placeholder="java.lang.Exception,java.io.IOException">
            </div>
        </div>
        <div class="form-group">
            <label for="period" class="col-sm-4 control-label">授权文件类型:</label>
            <div class="col-sm-4">
                <select class="form-control" id="authType">
                    <option value="1">外网</option>
                    <option value="0">内网</option>
                </select>
            </div>
        </div>
        <div class="form-group">
            <div class="col-sm-offset-4 col-sm-4">
                <input type='hidden' id="authAppCode">
                <button id="export" class="btn btn-lg btn-primary " type="button">生成授权文件</button>
                <button id="cannelExport" class="btn btn-lg btn-primary " type="button">取消生成</button>
            </div>
        </div>
    </div>
    <table class="table table-condensed">
        <caption>
            <button id="crtApp" type="button" class="btn btn-warning" href="#">创建应用</button>
            &nbsp;
            <button id="crtAlarm" type="button" class="btn btn-warning" href="#">默认告警规则</button>
            &nbsp;
            <button type="button" class="btn btn-success" href="#" onclick="window.location.reload(); return false;">刷新</button>
        </caption>
        <thead>
        <tr>
            <th style="width:5%">序号</th>
            <th style="width:60%">应用名称</th>
            <th style="width:20%">操作</th>
        </tr>
        </thead>
    <#if applist??>
        <tbody>
            <#list applist as appInfo>
            <tr>
                <th scope="row">${appInfo_index + 1}</th>
                <td>${appInfo.appCode!}</td>
                <td>
                    <button name="conf" appId="${appInfo.appId!}" appCode="${appInfo.appCode!}" type="button" class="btn btn-default btn-xs">配置告警规则</button>
                    <button name="exportAuthInfo" appId="${appInfo.appId!}" appCode="${appInfo.appCode!}" type="button" class="btn btn-default btn-xs">生成授权文件</button>
                    <button name="del" appId="${appInfo.appId!}" type="button" class="btn btn-default btn-xs">删除</button>
                </td>
            </tr>
            </#list>
        </tbody>
    </#if>
    </table>
</div>
<div id="authFiledownLoad">

</div>
<#--<iframe id="authFiledownLoad" style="width:0px;height:0px;display:none"></iframe>-->
<!-- script references -->
<@common.importJavaScript />
<script type="text/javascript">
    $().ready(function(){
        $("#crtApp").bind("click",function(){
            $("#cannelRuleBtn").click();
            $("#createAppDiv").show();
        });

        $("#crtBtn").bind("click",function(){
            if($("#appCode").val() == ''){
                alert("请输入应用名称");
                return false;
            }
            var urlStr = '${base}/appinfo/create';
            $.ajax({
                type: 'POST',
                url: urlStr,
                contentType:"application/json",
                data:"{'appCode':'" + $("#appCode").val() + "'}",
                dataType: 'json',
                async : false,
                success: function(data){
                    console.log(data);
                    var result = data.result;
                    if(result == 'OK'){
                        alert(data.msg);
                        //$("#createAppDiv").hide();
                        window.location.reload();
                    }else{
                        alert(data.msg);
                    }
                },
                error: function(xhr, type){
                    alert("操作失败");
                }
            });
        });

        $("button[name='del']").each(function(){
            $(this).bind("click",function(){
                var appId = $(this).attr("appId");
                if(appId < 0){
                    alert("请选择应用");
                    return false;
                }
                var urlStr = '${base}/appinfo/delete/' + appId;
                $.ajax({
                    type: 'POST',
                    url: urlStr,
                    contentType:"application/json",
                    data:{},
                    dataType: 'json',
                    async : false,
                    success: function(data){
                        console.log(data);
                        var result = data.result;
                        if(result == 'OK'){
                            alert(data.msg);
                            window.location.reload();
                        }else{
                            alert(data.msg);
                        }
                    },
                    error: function(xhr, type){
                        alert("操作失败");
                    }
                });
            });
        });

        $("#cannelBtn").bind("click",function(){
            $("#appCode").val("");
            $("#createAppDiv").hide();
        });

        $("#cannelRuleBtn").bind("click",function(){
            $("#crtAlarmDiv").hide();
        });

        $("#crtAlarm").bind("click",function(){
            $("#cannelBtn").click();
            $("#crtAlarmDiv").show();

            var urlStr = '${base}/alarmRule/default';
            $.ajax({
                type: 'POST',
                url: urlStr,
                contentType:"application/json",
                data:"{}",
                dataType: 'json',
                async : false,
                success: function(data){
                    console.log(data);
                    var result = data.result;
                    $("#isGlobal").val("1");
                    $("#appName").text("所有应用");
                    if(result == 'OK'){
                        var obj = jQuery.parseJSON(data.data);
                        var confArg = jQuery.parseJSON(obj.configArgs);
                        $("#period").val(confArg.period);
                        $("#todoType").val(obj.todoType).change();
                        if(obj.todoType == 1){
                            $("#urlCall").val(confArg.urlInfo.urlCall);
                        }else {
                            $("#mailTo").val(confArg.mailInfo.mailTo);
                            $("#mailCc").val(confArg.mailInfo.mailCc);
                            $("#mailTemp").val(confArg.mailInfo.mailTemp);
                        }
                        $("#ruleId").val(obj.ruleId);
                        $("#crtRuleBtn").text("修改规则");
                        $("#cannelRuleBtn").text("取消修改");
                        $("#appId").val("");
                    }
                },
                error: function(xhr, type){
                    alert("操作失败");
                }
            });
        });

        $("#todoType").bind("change",function(){
            if($(this).val() == 0){
                $("#mailTempDiv").show();
                $("#callBackDiv").hide();
            }else if($(this).val() == 1){
                $("#mailTempDiv").hide();
                $("#callBackDiv").show();
            }
        });

        $("button[name='conf']").each(function(){
            $(this).bind("click",function(){
                $("#cannelBtn").click();
                $("#crtAlarmDiv").show();

                var appId = $(this).attr("appId");
                var appCode = $(this).attr("appCode");
                if(appId < 0){
                    alert("请选择应用");
                    return false;
                }

                var urlStr = '${base}/alarmRule/' + appId;
                $.ajax({
                    type: 'POST',
                    url: urlStr,
                    contentType:"application/json",
                    data:"{}",
                    dataType: 'json',
                    async : false,
                    success: function(data){
                        console.log(data);
                        var result = data.result;
                        $("#isGlobal").val("0");
                        $("#appId").val(appId);
                        if(result == 'OK'){
                            $("#appName").text(appCode);
                            var obj = jQuery.parseJSON(data.data);
                            var confArg = jQuery.parseJSON(obj.configArgs);
                            $("#period").val(confArg.period);
                            $("#todoType").val(obj.todoType).change();
                            if(obj.todoType == 1){
                                $("#urlCall").val(confArg.urlInfo.urlCall);
                            }else {
                                $("#mailTo").val(confArg.mailInfo.mailTo);
                                $("#mailCc").val(confArg.mailInfo.mailCc);
                                $("#mailTemp").val(confArg.mailInfo.mailTemp);
                            }
                            $("#ruleId").val(obj.ruleId);
                            $("#crtRuleBtn").text("修改规则");
                            $("#cannelRuleBtn").text("取消修改");
                        }else{
                            $("#appName").html(appCode+"(<b>使用默认规则</b>)");
                            $("#period").val("");
                            $("#todoType").val("0").change();
                            $("#urlCall").val("");
                            $("#mailTo").val("");
                            $("#mailCc").val("");
                            $("#mailTemp").val("");
                            $("#ruleId").val("");
                            $("#crtRuleBtn").text("创建规则");
                            $("#cannelRuleBtn").text("取消创建");
                        }
                    },
                    error: function(xhr, type){
                        alert("操作失败");
                    }
                });
            });
        });

        //创建规则操作
        $("#crtRuleBtn").bind("click",function(){
            var ruleId = $("#ruleId").val();
            var appId = $("#appId").val();//可空
            var period = $("#period").val();//不可空
            if(period == null || period.length < 1){
                alert("告警频率不能为空");
                return false;
            }
            var isGlobal = $("#isGlobal").val();//不可空
            if(isGlobal == null || isGlobal.length < 1){
                alert("规则标识不能为空");
                return false;
            }else{
                if(isGlobal ==0){
                    if(appId == null || appId.length < 1){
                        alert("应用标识不能为空");
                        return false;
                    }
                }
            }

            var todoType = $("#todoType").val();//不可空
            if(todoType == null || todoType.length < 1){
                alert("告警操作不能为空");
                return false;
            }
            var urlCall = $("#urlCall").val();
            var mailTemp = $("#mailTemp").val();
            var todoContent = "";
            if(todoType == 0){
                if(mailTemp == null || mailTemp.length < 1){
                    alert("邮件模板不能为空");
                    return false;
                }
                var mailTo = $("#mailTo").val();
                var mailCc = $("#mailCc").val();
            }else if(todoType == 1){
                if(urlCall == null || urlCall.length < 1){
                    alert("回调接口不能为空");
                    return false;
                }
            }else{
                alert("告警规则不能为空");
                return false;
            }
            var ruleId = $("#ruleId").val();
            var jsonData = "";
            if(ruleId > 0){
                //调用修改规则
                if(ruleId == null || ruleId.length < 0){
                    alert("告警规则不能为空");
                    return false;
                }
                var urlStr = '${base}/alarmRule/modify';
                if(todoType ==0){
                    jsonData = "{ruleId:'"+ruleId+"',appId:'"+appId+"',period:'"+period+"',isGlobal:'"+isGlobal+"',todoType:'"+todoType+"',mailTemp:'"+mailTemp+"',mailTo:'"+mailTo+"',mailCc:'"+mailCc+"'}";
                }else if(todoType ==1){
                    jsonData = "{ruleId:'"+ruleId+"',appId:'"+appId+"',period:'"+period+"',isGlobal:'"+isGlobal+"',todoType:'"+todoType+"',urlCall:'"+urlCall+"'}";
                }else{
                    alert("请选择正确的告警操作");
                    return false;
                }
                //alert(jsonData);
                $.ajax({
                    type: 'POST',
                    url: urlStr,
                    contentType:"application/json",
                    data:jsonData,
                    dataType: 'json',
                    async : false,
                    success: function(data){
                        if(data.result == "OK"){
                            alert(data.msg);
                            window.location.reload();
                        }else{
                            alert(data.msg);
                        }
                    },
                    error: function(xhr, type){
                        alert("操作失败");
                    }
                });
            }else{
                //调用创建规则
                var urlStr = '${base}/alarmRule/create';
                if(todoType ==0){
                    jsonData = "{appId:'"+appId+"',period:'"+period+"',isGlobal:'"+isGlobal+"',todoType:'"+todoType+"',mailTemp:'"+mailTemp+"',mailTo:'"+mailTo+"',mailCc:'"+mailCc+"'}";
                }else if(todoType ==1){
                    jsonData = "{appId:'"+appId+"',period:'"+period+"',isGlobal:'"+isGlobal+"',todoType:'"+todoType+"',urlCall:'"+urlCall+"'}";
                }else{
                    alert("请选择正确的告警操作");
                    return false;
                }
                alert(jsonData);
                $.ajax({
                    type: 'POST',
                    url: urlStr,
                    contentType:"application/json",
                    data:jsonData,
                    dataType: 'json',
                    async : false,
                    success: function(data){
                        if(data.result == "OK"){
                            alert(data.msg);
                            window.location.reload();
                        }else{
                            alert(data.msg);
                        }
                    },
                    error: function(xhr, type){
                        alert("操作失败");
                    }
                });
            }
        });

        $("button[name='export']").each(function(){
            $(this).bind("click",function(){
                var downLoadFrame = document.getElementById('authFiledownLoad');
                downLoadFrame.src = '${base}/exportAuth/test';
            });
        });

        $("button[name='exportAuthInfo']").each(function(){
            $(this).bind("click",function(){
                $("#authInfo").show();
                $("#authAppCode").val($(this).attr("appCode"));
                //var downLoadFrame = document.getElementById('authFiledownLoad');
                //downLoadFrame.src = '${base}/exportAuth/test';
            });
        });

        $("#export").bind("click",function(){
            <#--var downLoadFrame = document.getElementById('authFiledownLoad');-->
            <#--downLoadFrame.src = '${base}/exportAuth/test';-->
            var appCode = $("#authAppCode").val();
            if (appCode == null){
                alert("没有发现应用编码.");
                return;
            }
            var form=$("<form>");//定义一个form表单
            form.attr("style","display:none");
            //form.attr("target","${base}/exportAuth/test");
            form.attr("method","post");
            form.attr("action","${base}/exportAuth/" + appCode);
            var exportData=$("<input>");
            exportData.attr("type","hidden");
            exportData.attr("name","exportData");
            exportData.attr("value",(new Date()).getMilliseconds());
            var exclusiveException=$("<input>");
            exclusiveException.attr("type","hidden");
            exclusiveException.attr("name","exclusiveException");
            exclusiveException.attr("value",$("#exclusiveException").val());
            var authType=$("<input>");
            authType.attr("type","hidden");
            authType.attr("name","authType");
            authType.attr("value",$("#authType").val());
            $("#authFiledownLoad").append(form);//将表单放置在web中
            form.append(exportData);
            form.append(exclusiveException);
            form.append(authType);

            form.submit();//表单提交
        });

        $("#cannelExport").bind("click",function(){
            $("#authInfo").hide();
        });

    });
</script>
</body>
</html>