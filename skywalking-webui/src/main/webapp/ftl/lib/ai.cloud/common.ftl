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
${userInfo}
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
<#assign text>
${userInfo}
</#assign>
<#assign json=text?eval />
<form class="navbar-form navbar-left" role="search">
	<div class="form-group">
<#if json.isLogin == '1'>
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
		<h5 style="color:black">
			${traceId!}</br>
			调度入口IP：${(valueList[0].address)!}，开始时间：${beginTime?number_to_datetime}，${(valueList?size)!}条调用记录，消耗总时长：${(endTime - beginTime)!'0'} ms.<a id="originLog" href="#">显示原文</a>
		</h5>
		<div id="tableDiv">
		<table id="example-advanced">
			<caption>
				<button type="button" class="btn btn-success" href="#" onclick="jQuery('#example-advanced').treetable('expandAll'); return false;">Expand all</button>
			  	&nbsp;
			  	<button type="button" class="btn btn-warning" href="#" onclick="jQuery('#example-advanced').treetable('collapseAll'); return false;">Collapse all</button>
			</caption>
			<thead>
			  <tr>
				<th style="width: 25%">服务名</th>
				<th style="width: 5%">类型</th>
				<th style="width: 5%">状态</th>
				<th style="width: 20%">服务/方法</th>
				<th style="width: 15%">主机信息</th>
				<th style="width: 30%">时间轴</th>
			  </tr>
			</thead>
			<tbody>
<#list valueList as logInfo>
<#if logInfo.colId == "0">
				<tr name='log' statusCodeStr="${logInfo.statusCodeStr!}" data-tt-id='${logInfo.colId!}'>
<#else>
				<tr name='log' statusCodeStr="${logInfo.statusCodeStr!}" data-tt-id='${logInfo.colId!}' data-tt-parent-id='${logInfo.parentLevel!}'>
</#if>
					<td><b>${logInfo.applicationIdStr!}</b></td>
					<td>${logInfo.spanTypeName!'UNKNOWN'}</td>
					<td>${logInfo.statusCodeName!'MISSING'}</td>
					<td>
						<a href="#" data-toggle="tooltip" data-placement="bottom" title="${logInfo.viewPointId!}">${logInfo.viewPointIdSub!}</a>
					</td>
					<td>${logInfo.address!}</td>
					<td>
<#assign totalTime=(endTime-beginTime)*1.15>
						<div class="progress">
<#if (logInfo.timeLineList?size=1)>
	<#list logInfo.timeLineList as log>
							<input type="hidden" beginTime="${beginTime}" endTime="${endTime}" total_time="${totalTime}" cost="${log.cost}" curStartTime="${log.startTime}" beforePer="${100*(log.startTime-beginTime)/totalTime}" curPer="${100*log.cost/totalTime}">
							<div class="progress-bar" style="width: ${100*(log.startTime-beginTime)/totalTime}%"></div>
							<div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: ${100*log.cost/totalTime}%;"></div>
							<div class="progress-bar progress-split progress-bar-striped" style="color:black;">&nbsp;${log.cost}ms</div>
	</#list>
</#if>

<#if (logInfo.timeLineList?size=2)>
	<#if (logInfo.timeLineList[1].startTime) < (logInfo.timeLineList[0].startTime)>
		<#--服务端开始时间 小于 客户端开始时间(异常,直接显示服务端时间轴)-->
		<#assign a=(logInfo.timeLineList[1].startTime - beginTime)! />
		<#assign b=(logInfo.timeLineList[1].cost)! />
		<input type="hidden" a="${a!}" b="${b!}" beginTime="${beginTime!}" totalTime="${totalTime!}">
		<div class="progress-bar" style="width: ${100*(a)/totalTime}%"></div>
		<div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: ${100*(b)/totalTime}%;"></div>
		<div class="progress-bar progress-split progress-bar-striped" style="color:black;">&nbsp;${b}ms</div>
	<#elseif (logInfo.timeLineList[1].startTime >= logInfo.timeLineList[0].startTime) && ((logInfo.timeLineList[1].startTime) <= (logInfo.timeLineList[0].startTime + logInfo.timeLineList[0].cost))>
		<#--服务端开始时间 大于等于 客户端开始时间,并且 小于等于 客户端的结束时间-->
		<#if (logInfo.timeLineList[1].startTime + logInfo.timeLineList[1].cost) <= (logInfo.timeLineList[0].startTime + logInfo.timeLineList[0].cost)>
			<#--服务端结束时间 小于等于 客户端结束时间(客户端时间段包含服务端时间段)-->
			<#assign a=(logInfo.timeLineList[0].startTime - beginTime)! />
			<#assign b=(logInfo.timeLineList[1].startTime - logInfo.timeLineList[0].startTime)! />
			<#assign c=(logInfo.timeLineList[1].startTime + logInfo.timeLineList[1].cost - logInfo.timeLineList[1].startTime) />
			<#assign d=(logInfo.timeLineList[0].startTime + logInfo.timeLineList[0].cost - logInfo.timeLineList[1].startTime - logInfo.timeLineList[1].cost) />
			<input type="hidden" a="${a!}" b="${b!}" c="${c!}" d="${d!}" beginTime="${beginTime!}" totalTime="${totalTime!}">
			<div class="progress-bar" style="width: ${100*(a)/totalTime}%"></div>
			<div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: ${100*(b)/totalTime}%;"></div>
			<div class="progress-bar progress-bar-a progress-bar-striped" style="color:black;min-width: ${100*(c)/totalTime}%;"></div>
			<div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: ${100*(d)/totalTime}%;"></div>
			<div class="progress-bar progress-split progress-bar-striped" style="color:black;">&nbsp;${b}/${c}/${d}ms</div>
		<#else>
			<#--服务端开始时间 大于 客户端开始时间(客户端时间轴与服务端时间轴有一部分重合，重合后的部分算为服务端)-->
			<#assign a=(logInfo.timeLineList[0].startTime - beginTime) />
			<#assign b=(logInfo.timeLineList[1].startTime - logInfo.timeLineList[0].startTime) />
			<#assign c=(logInfo.timeLineList[1].startTime + logInfo.timeLineList[1].cost - logInfo.timeLineList[1].startTime) />
			<input type="hidden" a="${a!}" b="${b!}" c="${c!}" totalTime="${totalTime!}">
			<div class="progress-bar" style="width: ${100*(a)/totalTime}%"></div>
			<div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: ${100*(b)/totalTime}%;"></div>
			<div class="progress-bar progress-bar-a progress-bar-striped" style="color:black;min-width: ${100*(c)/totalTime}%;"></div>
			<div class="progress-bar progress-split progress-bar-striped" style="color:black;">&nbsp;${a}ms->${b}ms->${c}ms</div>
		</#if>
	<#else>
		<#--服务端开始时间 大于 客户端结束始时间(客户端一段时间，一段空格，一段服务端时间)-->
		3333
	</#if>
</#if>
						</div>
					</td>
</#list>
			</tbody>
		</table>
		</div>
	</div>
</div>
</#if>
</#macro>

<#macro importOriginLog>
<div id="originRow" style="display:none">
	<div class="col-md-12">
		<table class="table table-bordered table-hover">
		  <thead>
		    <tr>
		      <th style="width:2%">#</th>
		      <th style="width:98%">日志内容</th>
		    </tr>
		  </thead>
<#if valueList??>
		  <tbody>
<#list valueList as logInfo>
		    <tr>
		      <th scope="row">${logInfo_index}</th>
		      <td>${logInfo.originData!}</td>
		    </tr>
</#list>
		  </tbody>
</#if>
		</table>
	</div>
</div>
</#macro>