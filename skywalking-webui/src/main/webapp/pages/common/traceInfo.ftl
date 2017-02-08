<#macro traceTableTmpl>
<script type="text/x-jsrender" id="traceTreeTableTmpl">
        <table id="traceTreeTable">
            <thead>
            <tr>
                <th style="width: 25%" >Application Code</th>
                <th style="width: 5%">Type</th>
                <th style="width: 5%">Status</th>
                <th style="width: 20%">Operation Name</th>
                <th style="width: 15%">Host</th>
                <th style="width: 30%">Time</th>
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
                        <td><a href="javascript:void(0);" data-toggle="modal" data-target="#modal{{>modalId}}">{{>viewPointIdSub}}</a> </td>
                        <!-- Modal -->
                        <div class="modal fade" id="modal{{>modalId}}" tabindex="-1" role="dialog" aria-labelledby="modal{{>modalId}}Label">
                            <div class="modal-dialog" role="document">
                                <div class="modal-content">
                                    <div class="modal-body">
                                       <ul class="list-group">
                                            <li class="list-group-item" style="word-wrap:break-word"><strong>Operation Name：</strong>{{>viewPointId}}</li>
                                            <li class="list-group-item"><strong>Span Type：</strong>{{>spanTypeName}}</li>
                                            <li class="list-group-item"><strong>Cost：</strong>{{>cost}}<strong>ms</strong></li>
                                            <li class="list-group-item" style="word-wrap: break-word;word-break: normal;"><strong>Business Key：</strong>{{>businessKey}}</li>
                                            <li class="list-group-item"><strong>Application Code：</strong>{{>applicationCode}}</li>
                                            <li class="list-group-item" style="word-wrap: break-word;word-break: normal;"><strong>Host：</strong>{{>address}}</li>
                                            <li class="list-group-item"><strong>Process No.：</strong>{{>processNo}}</li>
                                            <li class="list-group-item" style="word-wrap: break-word;word-break: normal;"><strong>Exception Stack：</strong>
                                                {{if　exceptionStack}}
                                                    {{>exceptionStack}}
                                                {{/if}}
                                                {{if serverExceptionStr}}
                                                    <br/>Server:{{>serverExceptionStr}}
                                                {{/if}}
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <td>{{>address}}</td>
                        <td>
                         <div class="progress">
                            <div class="progress-bar" style="width: {{>totalLengthPercent}}%"></div>
                        {{if case == 1}}
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>spiltLengthPercent}}%;">{{>cost}}ms </div>
                        {{/if}}
                        {{if case == 2}}
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>spiltLengthPercent}}%;">{{>cost}}ms</div>
                        {{/if}}
                        {{if case == 3}}
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>clientCostPercent}}%;"></div>
                            <div class="progress-bar progress-bar-a progress-bar-striped" style="color:black;min-width: {{>networkCostPercent}}%;">{{>clientCost}}/{{>networkCost}}/{{>serverCost}}ms</div>
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>serverCostPercent}}%;"></div>
                        {{/if}}
                        {{if case == 4}}
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>clientCostPercent}}%;">{{>totalLength}}ms</div>
                            <div class="progress-bar progress-bar-a progress-bar-striped" style="color:black;min-width: {{>serverCostPercent}}%;">->{{>clientCost}}->{{>serverCost}}ms</div>
                        {{/if}}
                        {{if case == 5}}
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>clientCostPercent}}%;">{{>clientCost}}</div>
                            <div class="progress-bar progress-bar-striped" style="color:black;min-width: {{>networkCostPercent}}%;">/{{>serverCost}}ms</div>
                            <div class="progress-bar progress-bar-b progress-bar-striped" style="color:black;min-width: {{>serverCostPercent}}%;"></div>
                            <div class="progress-bar progress-split progress-bar-striped" style="color:black;">&nbsp; </div>
                        {{/if}}
                        </div>
                      </td>
                    </tr>
                {{/for}}
                {{if totalSize > maxQueryNodeSize}}
                    <tr data-tt-parent-id='0' data-tt-id="greatThanMaxQueryNodeSize">
                        <td>....</td>
                        <td>....</td>
                        <td>....</td>
                        <td style="text-align:center;color:green;">The number of call chain node is great than the max query node size({{>maxQueryNodeSize}})， only call entry node is displayed.</td>
                        <td>....</td>
                        <td>....</td>
                    </tr>
                {{/if}}
                {{if totalSize > maxShowNodeSize && totalSize <= maxQueryNodeSize}}
                    <tr data-tt-parent-id='0' data-tt-id="greatThanMaxShowNodeSize">
                        <td>....</td>
                        <td>....</td>
                        <td>....</td>
                        <td style="text-align:center;color:green;">The total  of  call chain node is {{>totalSize}}，but the number is great than then max number of showing node({{>maxShowNodeSize}})
                        ， Only the first {{>showSize}} chain node are displayed.</td>
                        <td>....</td>
                        <td>....</td>
                    </tr>
                {{/if}}
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
                        <li class="list-group-item" style="word-wrap:break-word"><strong>Operation Name：</strong>{{>viewPointId}}</li>
                        <li class="list-group-item"><strong>Span Type ：</strong>{{>spanTypeName}}</li>
                        <li class="list-group-item"><strong>Start Time ：</strong>{{>startDate}}</li>
                        <li class="list-group-item"><strong>Cost ：</strong>{{>cost}}<strong>ms</strong></li>
                        <li class="list-group-item"><strong>Business Key：</strong>{{>businessKey}}</li>
                        <li class="list-group-item"><strong>Application Name：</strong>{{>applicationCode}}</li>
                        <li class="list-group-item"><strong>Host ：</strong>{{>address}}</li>
                        <li class="list-group-item"><strong>Process No. ：</strong>{{>processNo}}</li>
                        <li class="list-group-item"><strong>Exception stack：</strong>
                            {{if　exceptionStack}}
                                {{>exceptionStack}}
                            {{/if}}
                            {{if serverExceptionStr}}
                                <br/>Server:{{>serverExceptionStr}}
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
        <div class="row">
            <h5>
                Transaction <strong>{{>traceId}}</strong> starts in <strong>{{>callIP}}</strong> at <strong>{{>startTimeStr}}</strong> and it cost <strong>{{>totalTime}}</strong> ms.
                <br/>
                {{if totalSize > maxQueryNodeSize}}
                  This chain node number of transaction great than the max query node size(<strong>{{>maxQueryNodeSize}}</strong>)，Only the entry node is displayed.
                {{else totalSize > maxShowNodeSize}}
                  This transaction has <strong>{{>totalSize}}</strong> call chain node(s), but the number is great than then max number of showing node，Only the first <strong>{{>showSize}}</strong>
                   are
                  displayed.
                {{else}}
                   This transaction has <strong>{{>totalSize}}</strong> call chain node(s).
                {{/if}}
                </h5>
        </div>
        <ul id="myTab" class="nav nav-tabs">
            <li class="active">
                <a href="#traceTree" data-toggle="tab">
                   trace tree
                </a>
            </li>
            <li><a href="#traceLog" data-toggle="tab">trace log</a></li>
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
