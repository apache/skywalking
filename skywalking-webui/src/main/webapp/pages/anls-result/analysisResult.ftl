<#macro analysisResult>
<script type="text/x-jsrender" id="analysisResultList">
<div class="row">
    <div class="col-md-9">
        {{for trees}}
        <div class="row">
              <h4><a>{{>entranceViewpoint}}</a></h4>
              <p>{{>entranceViewpoint}}<br/>
                &nbsp;&nbsp;com.ai.test.controller.com.ai.test.controllersaveOrder()<br/>
                &nbsp;&nbsp;&nbsp;&nbsp;com.ai.test.controller.saveOrder()<br/>
                &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; com.ai.test.controller.saveOrder()<br/>
                &nbsp;&nbsp;com.ai.test.controller.saveOrder()<br/>
                ....
              </p>
              <p style="font-color">2016年03月已经被调用100次，成功100次，失败0次 <a class="pull-right"><ins>more</ins></a></p>
              <hr/>
        </div>
        {{/for}}
    </div>
    <div class="col-md-3">

    </div>
</div>
</script>
</#macro>