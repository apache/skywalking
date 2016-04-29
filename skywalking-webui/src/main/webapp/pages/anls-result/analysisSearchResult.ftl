<#macro anlyResultTmpl>
<script type="text/x-jsrender" id="anlyResultPanelTmpl">
    <div class="row">
        <div class="col-md-8" id="anlyResultmPanel">
        </div>
        <div class="col-md-4">
        </div>
    </div>
    <hr/>
</script>
</#macro>

<#macro anlyResultDisplayTmpl>
<script type="text/x-jsrender" id="anlyResultDisplayTmpl">
    <div class="row">
        <h4><a href="${_base}/mainPage?loadType=showAnalysisResult&key=analysisresult:{{>treeId}}">{{>entranceViewpoint}}</a></h4>
        <p>
         {{for nodes}}
         {{if isPrintSlipDot}}
            <span style="margin-left:15%">....</span></br>
         {{/if}}
         <span style="margin-left:{{>marginLeftSize}}px" data={{>traceLevelId}}>{{>viewPoint}}</span></br>
         {{/for}}
         <span style="margin-left:15%">....</span></br>
         </p>
         <p style="font-color">{{>entranceAnlyResult.yearOfAnlyResult}}年{{>entranceAnlyResult.monthOfAnlyResult}}月已经被调用{{>entranceAnlyResult.totalCall}}次&nbsp;
         成功<span class="text-success"><strong>{{>entranceAnlyResult.correctNumber}}</strong></span>次&nbsp;
         失败<span class="text-danger"><strong>{{>entranceAnlyResult.humanInterruptionNumber}}</strong></span>次&nbsp;
         成功调用率<span class="
         {{if correctRate >= 99.00}}
         text-success
         {{else correctRate >= 97}}
         text-warning
         {{else}}
         text-danger
         {{/if}}
         "><strong>{{>correctRate}}%</strong></span>
         <a class="pull-right"><ins>more</ins></a></p>
         <hr/>
    </div>
</script>
</#macro>

<#macro pageInfoTmpl>
<script type="text/x-jsrender" id="pageInfoTmpl">
    <input type="hidden" value="{{>pageSize}}" id="pageSize"/>
    <nav>
    <ul class="pager">
            {{if hasPreviousPage}}
            <li><a href="javascript:void(0);" id="doPreviousPageBtn">Previous</a></li>
            {{/if}}
            {{if hasNextPage}}
            <li disabled><a href="javascript:void(0);" id="doNextPageBtn">Next</a></li>
            {{/if}}
    </ul>
    </nav>
</script>
</#macro>