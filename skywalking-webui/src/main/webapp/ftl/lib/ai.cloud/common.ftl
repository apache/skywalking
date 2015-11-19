<#-- importJavaScript -->
<#macro importJavaScript>
<script src="${base}/js/jquery/jquery-2.1.4.js"></script>
<script src="${base}/js/jquery/jquery-ui-1.11.4.js"></script>
<script src="${base}/js/jquery/jquery.treetable-3.2.0.js"></script>
<script src="${base}/js/bootstrap.min-3.3.5.js"></script>
</#macro>

<#-- importMenuInfo -->
<#macro importMenuInfo menuInfo>
<ul class="nav navbar-nav">
<#assign text>
${menuInfo}
</#assign>
<#assign json=text?eval />
<#if json.isLogin == '1'>
<li><a name="menuUrl" href="#" url="user/useDoc/${json.uid}">使用说明</a></li>
<li><a name="menuUrl" href="#" url="user/">告警配置</a></li>
</#if>
</ul>
</#macro>

<#-- importSearchInfo -->
<#macro importSearchInfo isLogin>
<form class="navbar-form navbar-left" role="search">
	<div class="form-group">
<#if isLogin == '1'>
		<input id="srchKey" type="text" class="form-control" style="width: 450px" placeholder="TraceId">
<#else>
		<input id="srchKey" type="text" class="form-control" style="width: 750px" placeholder="TraceId">
</#if>
</div>
	<button id="srchBtn" type="button" class="btn btn-default">Search</button>
</form>
</#macro>

<#-- importUserInfo -->
<#macro importUserInfo userInfo>
<ul class="nav navbar-nav navbar-right">
<#assign text>
${userInfo}
</#assign>
<#assign json=text?eval />
<#if json.isLogin == '1'>
	<li class="dropdown"><a href="#" class="dropdown-toggle"
		data-toggle="dropdown" role="button" aria-haspopup="true"
		aria-expanded="false"> ${json.userName} <span class="caret"></span></a>
		<ul class="dropdown-menu">
			<li><a name="menuUrl" href="#" url="user/setting/${json.uid}">设置</a></li>
			<li role="separator" class="divider"></li>
			<li><a name="menuUrl" href="#" url="logout">退出</a></li>
		</ul>
	</li>
<#else>
	<li><a name="menuUrl" href="#" url="login">登录</a></li>
</#if>
</ul>
</#macro>

<#-- dealTraceLog -->
<#macro dealTraceLog>
<#if valueList??>
<div id="row">
	<div class="col-md-12">
		<table id="example-advanced">
			<caption>
			  <a href="#" onclick="jQuery('#example-advanced').treetable('expandAll'); return false;">Expand all</a>
			  &nbsp;
			  <a href="#" onclick="jQuery('#example-advanced').treetable('collapseAll'); return false;">Collapse all</a>
			</caption>
			<thead>
			  <tr>
				<th class="col-md-4" style="width: 16%">调用序列</th>
				<th class="col-md-1" style="width: 4%">类型</th>
				<th class="col-md-1" style="width: 4%">状态</th>
				<th class="col-md-3" style="width: 10%">业务信息</th>
				<th class="col-md-2" style="width: 8%">主机信息</th>
				<th class="col-md-3" style="width: 16%">时间轴</th>
			  </tr>
			</thead>
			<tbody>
<#list valueList as logInfo>
<#if logInfo.colId == "0">
				<tr data-tt-id='${logInfo.colId!}'>
<#else>
				<tr data-tt-id='${logInfo.colId!}' data-tt-parent-id='${logInfo.parentLevel!}'>
</#if>
<td><b>${logInfo.viewPointId!}</b></td>
<td>${spanTypeMap[logInfo.spanType]!'-'}</td>
<td>${statusCodeMap[logInfo.statusCode]!}</td>
<td>${logInfo.businessKey!}</td>
<td>${logInfo.address!}</td>
<td>
<#assign totalTime=(endTime-beginTime)>
<#list logInfo.timeLineList as log>
	<div class="progress" beginTime="${beginTime}" endTime="${endTime}" total_time="${totalTime}" cost="${log.cost}" curStartTime="${log.startTime}" beforePer="${100*(log.startTime-beginTime)/totalTime}" curPer="${100*log.cost/33?int}">
	<div class="progress-bar" style="width: ${100*(log.startTime-beginTime)/totalTime}%"></div>
	<div class="progress-bar progress-bar-info" style="min-width: ${100*log.cost/totalTime}%;">${log.cost}ms</div></div>
</#list>
</td>
</#list>
			</tbody>
		</table>
	</div>
</div>
</#if>
</#macro>