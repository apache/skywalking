<#macro analysisResult>
<script type="text/x-jsrender" id="analysisResultPanelTmpl">
    <div class="row">
        <div class="col-md-4 ">
            <div class="input-group">
                <div class="input-group-btn">
                    <button type="button" class="btn btn-default dropdown-toggle" data-toggle="dropdown"
                            aria-haspopup="true" aria-expanded="false"><span id="analyTypeDropDown">Action</span><span
                            class="caret"></span></button>
                    <ul class="dropdown-menu">
                        <li><a href="javascript:void(0);" name="analyTypeDropDownOption" value="HOUR">时报表</a></li>
                        <li><a href="javascript:void(0);" name="analyTypeDropDownOption" value="DAY">日报表</a></li>
                        <li><a href="javascript:void(0);" name="analyTypeDropDownOption" value="MONTH">月报表</a></li>
                    </ul>
                </div>
                <input type="text" class="form-control" readonly id="analyDate">
            <span class="input-group-btn">
              <button class="btn btn-default" type="button" id="showAnalyResultBtn">Go!</button>
            </span>
            </div>
        </div>
        <div class="col-md-4 col-md-offset-4">
            <span><a href="javascript:void(0);" id="previousHourBtn">上个小时</a></span>&nbsp;&nbsp;&nbsp;&nbsp;
            <span><a href="javascript:void(0);" id="yesterdayBtn">昨天</a></span>&nbsp;&nbsp;&nbsp;&nbsp;
            <span><a href="javascript:void(0);" id="currentMonthBtn">本月</a></span>&nbsp;&nbsp;&nbsp;&nbsp;
            <span><a href="javascript:void(0);" id="previousMonthBtn">上月</a></span>&nbsp;&nbsp;&nbsp;&nbsp;
        </div>
    </div>
    <hr/>
    <div class="row">
            <input type="hidden" id="treeId" value="{{>treeId}}"/>
            <input type="hidden" id="analyType" value=""/>
            <table class="gridtable">
                <thead>
                <tr>
                    <th width="10%">LevelId</th>
                    <th width="62%">ViewPoint</th>
                    <th width="7%">调用次数</th>
                    <th width="7%">正确次数</th>
                    <th width="5%">正确率</th>
                    <th width="7%">平均耗时</th>
                </tr>
                </thead>
                <tbody id="dataBody">
                </tbody>
            </table>
    </div>
    <hr/>


</script>
</#macro>

<#macro analysisResultTableTmpl>
<script type="text/x-jsrender" id="analysisResultTableTmpl">
        <tr>
            {{if isPrintLevelId}}
                <td rowspan="{{>rowSpanCount}}" valign="middle">{{>traceLevelId}}</td>
            {{/if}}
            <td>
                <a href="javascript:void(0);" data-toggle="modal" data-target="#modal{{>nodeToken}}">{{>viewPoint}}</a>
                <span style="display:none" id="{{>nodeToken}}ViewPoint">{{>viewPoint}}</span>

                 </div>
            </td>
            <td>{{>anlyResult.totalCall}}</td>
            <td>{{>anlyResult.correctNumber}}</td>
            <td>
            <span class="
         {{if anlyResult.correctRate >= 99.00}}
         text-success
         {{else anlyResult.correctRate >= 97}}
         text-warning
         {{else}}
         text-danger
         {{/if}}
         ">
            <strong>{{>anlyResult.correctRate}}%</strong></span></td>
            <td>{{>anlyResult.averageCost}}ms
            <span id="{{>nodeToken}}" style="display:none">{{>anlyResultStr}}</span></td>

        </tr>


</script>
</#macro>


<#macro viewPointPickUp>
    <div class="modal fade" id="viewPointPickupModal" tabindex="-1" role="dialog" aria-labelledby="viewPointPickupModal" aria-hidden="true">
        <div class="modal-dialog" role="document">
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
                        <h4 class="modal-title">节点详情</h4>
                    </div>
                    <div class="modal-body">
                        <div class= "row">
                            <div  class="col-md-10">
                                <label>viewpoint：</label><br/>
                                <span style="word-wrap:break-word;">{{>viewPointStr}}</span>
                            </div>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button id="showTypicalCallTreeBtn" type="button" class="btn btn-primary" value="{{>nodeToken}}">查看调用链</button>
                        <button type="button" class="btn btn-default" data-dismiss="modal">取消</button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</#macro>

<#macro typicalCallChainTrees>
<script type="text/x-jsrender" id="typicalCallChainTreesTmpl">
    <br/>
      <div class="row">
        <small>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="javascript:void(0);" id="rebackCallChainTreeBtn">{{>entryViewPoint}}</a> </small>
        <br/>
        <small>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;{{>currentViewPoint}}</small>
      </div>

    <br/>
    <div class="panel panel-default">
      <div class="panel-body">
        <div class="row" id="typicalCheckBoxDiv">
        </div>
        <br/>
        <table class="gridtable" style="width:100%;">
          <thead>
            <tr>
              <th>LevelId</th>
              <th>ViewPoint</th>
              <th>调用次数</th>
              <th>正确次数</th>
              <th>正确率</th>
              <th>平均耗时</th>
            </tr>
          </thead>
          <tbody id="typicalTreeTableDataBody">
          </tbody>
        </table>
      </div>
    </div>
    <hr/>

</script>
</#macro>

<#macro typicalCallChainCheckBox>
<script type="text/x-jsrender" id="typicalTreeCheckBoxTmpl">
    <span>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;归属该节点下所有的典型调用链：</span>
    {{for typicalTreeIds}}
        <input name="typicalTreeCheckBox" type="checkbox" checked value="{{>callTreeToken}}"/>典型调用链{{: #index}}&nbsp;
    {{/for}}

</script>
</#macro>

<#macro typicalCallChainTreeTable>
<script type="text/x-jsrender" id="typicalTreeTableTmpl">
        <tr>
            {{if isPrintLevelId}}
                <td rowspan="{{>rowSpanCount}}" valign="middle">{{>traceLevelId}}</td>
            {{/if}}
            <td>
                <a href="javascript:void(0);" data-toggle="modal" data-target="#modal{{>nodeToken}}">{{>viewPoint}}</a>
            </td>
            <td>{{>anlyResult.totalCall}}</td>
            <td>{{>anlyResult.correctNumber}}</td>
            <td>
            <span class="
         {{if anlyResult.correctRate >= 99.00}}
         text-success
         {{else anlyResult.correctRate >= 97}}
         text-warning
         {{else}}
         text-danger
         {{/if}}
         ">
            <strong>{{>anlyResult.correctRate}}%</strong></span></td>
            <td>{{>anlyResult.averageCost}}ms
            <span id="{{>nodeToken}}" style="display:none">{{>anlyResultStr}}</span></td>
        </tr>


</script>
</#macro>


