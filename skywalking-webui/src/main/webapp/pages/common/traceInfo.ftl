<#macro traceTableTmpl>
<script type="text/x-jsrender" id="traceTreeTableTmpl">
        <table id="traceTreeTable">
            <thead>
            <tr>
                <th style="width: 25%" >服务名</th>
                <th style="width: 5%">类型</th>
                <th style="width: 5%">状态</th>
                <th style="width: 20%">服务/方法</th>
                <th style="width: 15%">主机信息</th>
                <th style="width: 30%">时间轴</th>
            </tr>
            </thead>
            <tbody>
                {{for treeNodes}}
                    <tr name='log' statusCodeStr="{{>statusCodeStr}}" data-tt-id='{{>colId}}'
                    {{if !isEntryNode }}data-tt-parent-id='{{>parentLevel}}'{{/if}}
                     >
                        <td><b>{{>applicationIdStr}}</b></td>
                        <td>{{>spanTypeName}}</td>
                        <td>{{>statusCodeName}}</td>
                        <td><a href="#" >{{>viewPointIdSub}}</a> </td>
                        <td>{{>address}}</td>
                        <td>
                         <div class="progress">
                            <div class="progress-bar" style="width: {{>totalLengthPercent}}%"></div>
                        {{if case == 1}}
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>spiltLengthPercent}}%;"></div>
                            <div class="progress-bar progress-split progress-bar-striped" style="color:black;">&nbsp;{{>cost}}ms </div>
                        {{/if}}
                        {{if case == 2}}
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>spiltLengthPercent}}%;"></div>
                            <div class="progress-bar progress-split progress-bar-striped" style="color:black;">&nbsp;{{>cost}}ms</div>
                        {{/if}}
                        {{if case == 3}}
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>clientCostPercent}}%;"></div>
                            <div class="progress-bar progress-bar-a progress-bar-striped" style="color:black;min-width: {{>networkCostPercent}}%;"></div>
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>serverCostPercent}}%;"></div>
                            <div class="progress-bar progress-split progress-bar-striped" style="color:black;">&nbsp;{{>clientCost}}/{{>networkCost}}/{{>serverCost}}ms</div>
                        {{/if}}
                        {{if case == 4}}
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>clientCostPercent}}%;"></div>
                            <div class="progress-bar progress-bar-a progress-bar-striped" style="color:black;min-width: {{>serverCostPercent}}%;"></div>
                            <div class="progress-bar progress-split progress-bar-striped" style="color:black;">&nbsp;{{>totalLength}}ms->{{>clientCost}}ms->{{>serverCost}}ms </div>
                        {{/if}}
                        {{if case == 5}}
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>clientCostPercent}}%;"></div>
                            <div class="progress-bar progress-bar-striped" style="color:black;min-width: {{>networkCostPercent}}%;"></div>
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>serverCostPercent}}%;"></div>
                            <div class="progress-bar progress-split progress-bar-striped" style="color:black;">&nbsp;{{>clientCost}}/{{>serverCost}}ms </div>
                        {{/if}}
                        </div>
                      </td>
                    </tr>
                {{/for}}
            </tbody>
        </table>
</script>
</#macro>

<#macro traceLogTmpl>
<script type="text/x-jsrender" id="traceLogTmpl">
    <div class="panel-group" id="accordion" role="tablist" aria-multiselectable="true">
        {{for treeNodes}}
        <div class="panel panel-default">
            <div class="panel-heading" role="tab" id="headingOne">
                <h4 class="panel-title">
                    <a role="button" data-toggle="collapse" data-parent="#accordion" href="#collapse{{: #index}}" aria-expanded="true" aria-controls="collapse{{: #index}}">
                            {{if parentLevel }}{{>parentLevel}}.{{/if}}{{>levelId}}---{{>viewPointIdSub}}
                    </a>
                </h4>
            </div>
            <div id="collapse{{: #index}}" class="panel-collapse collapse" role="tabpanel" aria-labelledby="headingOne">
                <div class="panel-body">
                    <ul class="list-group">
                        <li class="list-group-item" style="word-wrap:break-word"><strong>服务/方法：</strong>{{>viewPointId}}</li>
                        <li class="list-group-item"><strong>调用类型：</strong>{{>spanTypeName}}</li>
                        <li class="list-group-item"><strong>调用时间：</strong>{{>startDate}}</li>
                        <li class="list-group-item"><strong>花费时间：</strong>{{>cost}}<strong>毫秒</strong></li>
                        <li class="list-group-item"><strong>业务字段：</strong>{{>businessKey}}</li>
                        <li class="list-group-item"><strong>应用Code：</strong>{{>applicationId}}</li>
                        <li class="list-group-item"><strong>主机信息：</strong>{{>address}}}</li>
                        <li class="list-group-item"><strong>调用进程号：</strong>{{>processNo}}</li>
                        <li class="list-group-item"><strong>异常堆栈：</strong>
                            {{if　exceptionStack}}
                                {{>exceptionStack}}
                            {{else}}
                                无
                            {{/if}}
                        </li>
                    </ul>
                </div>
            </div>
        </div>
        {{/for}}
    </div>
</script>
</#macro>
<#macro traceTreeAllTmpl>
<script type="text/x-jsrender" id="traceTreeAllTmpl">
        <ul id="myTab" class="nav nav-tabs">
            <li class="active">
                <a href="#traceTree" data-toggle="tab">
                    Trace Tree
                </a>
            </li>
            <li><a href="#traceLog" data-toggle="tab">Trace log</a></li>
        </ul>

        <div id="myTabContent" class="tab-content">
            <div class="tab-pane fade in active" id="traceTree">
                <div class="row" id="traceTreeData">
                    {{include tmpl="#traceTreeTableTmpl"/}}
                </div>
            </div>
            <div class="tab-pane fade" id="traceLog">
                <div class="row" id="traceLogData">
                    {{include tmpl="#traceLogTmpl"/}}
                </div>
            </div>
        </div>
</script>
</#macro>