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
		  <div class="form-group" id="emailTemplateDiv" style="display:block">
		    <label for="emailTemplate" class="col-sm-4 control-label">邮件模板：</label>
		    <div class="col-sm-4">
		      <textarea class="form-control" id="emailTemplate" rows="4" placeholder="邮件模板"></textarea>
		    </div>
		  </div>
		  <div class="form-group" id="callBackDiv" style="display:none">
		    <label for="callBackUrl" class="col-sm-4 control-label">回调接口：</label>
		    <div class="col-sm-4">
		      <input type="text" class="form-control" id="callBackUrl" placeholder="回调接口地址">
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
	          	<button name="export" appId="${appInfo.appId!}" type="button" class="btn btn-default btn-xs">生成授权</button>
	          	<button name="del" appId="${appInfo.appId!}" type="button" class="btn btn-default btn-xs">删除</button>
	          	</td>
	        </tr>
</#list>
	      </tbody>
</#if> 
	    </table>
	</div>
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
							var periodJson = jQuery.parseJSON(obj.configArgs);
							$("#period").val(periodJson.period);
							$("#todoType").val(obj.todoType).change();
							if(obj.todoType == 1){
								$("#callBackUrl").val(obj.todoContent);
							}else {
								$("#emailTemplate").val(obj.todoContent);
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
					$("#emailTemplateDiv").show();
					$("#callBackDiv").hide();
				}else if($(this).val() == 1){
					$("#emailTemplateDiv").hide();
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
								var periodJson = jQuery.parseJSON(obj.configArgs);
								$("#period").val(periodJson.period);
								if(obj.todoType == 1){
									$("#todoType").val(obj.todoType).change();
									$("#callBackUrl").val(obj.todoContent);
								}else {
									$("#emailTemplate").val(obj.todoContent);
								}
								$("#ruleId").val(obj.ruleId);
								$("#crtRuleBtn").text("修改规则");
								$("#cannelRuleBtn").text("取消修改");
							}else{
								$("#appName").html(appCode+"(<b>使用默认规则</b>)");
								$("#period").val("");
								$("#todoType").val("0").change();
								$("#callBackUrl").val("");
								$("#emailTemplate").val("");
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
				var callBackUrl = $("#callBackUrl").val();
				var emailTemplate = $("#emailTemplate").val();
				var todoContent = "";
				if(todoType == 1){
					if(callBackUrl == null || callBackUrl.length < 1){
						alert("回调接口不能为空");
						return false;
					}
					todoContent = callBackUrl;
				}else{
					if(emailTemplate == null || emailTemplate.length < 1){
						alert("邮件模板不能为空");
						return false;
					}
					todoContent = emailTemplate;
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
					jsonData = "{ruleId:'"+ruleId+"',appId:'"+appId+"',period:'"+period+"',isGlobal:'"+isGlobal+"',todoType:'"+todoType+"',todoContent:'"+todoContent+"'}";
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
					jsonData = "{appId:'"+appId+"',period:'"+period+"',isGlobal:'"+isGlobal+"',todoType:'"+todoType+"',todoContent:'"+todoContent+"'}";
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
		});
	</script>
</body>
</html>