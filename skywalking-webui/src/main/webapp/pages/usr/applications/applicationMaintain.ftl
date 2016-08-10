<#macro applicationList>
<script type="text/x-jsrender" id="applicationsAllTmpl">
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
                    <th>应用编码</th>
                    <th>创建时间</th>
                    <th>更新时间</th>
                    <th>操作</th>
                </tr>
                </thead>
                <tbody>
                {{for applications}}
                <tr>
                    <td>{{>appCode}}</td>
                    <td>{{>createTime}}</td>
                    <td>{{>updateTime}}</td>
                    <td>
                        <a class="btn btn-xs" href="javascript:void(0);" onclick="loadContent('modifyApplication','{{>appId}}');">Update</a>
                        <a class="btn btn-danger btn-xs" href="javascript:void(0)" onclick="del('{{>appId}}')">Delete</a>
                        <a class="btn btn-info btn-xs" href="javascript:void(0)" onclick="loadContent('downloadAuthFile','{{>appCode}}')">Download auth File</a>
                    </td>
                </tr>
                {{/for}}
                </tbody>
            </table>
        </div>
    </div>
    <div class="col-md-4">
    </div>
</div>
</script>
</#macro>

<#macro addApplication>
<script type="text/x-jsrender" id="addApplicationTmpl">
<div class="row" style="display: none;" id="globalConfigAlter">
    <div class="col-md-6 col-md-offset-2">
        <div class="alert alert-warning alert-dismissible" role="alert">
            <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                    aria-hidden="true">&times;</span></button>
            <strong>Warning!</strong>&nbsp;You don't have a global config,
            you may <a href="javascript:void(0);" onclick="loadContent('createGlobalApplication')">
            <ins>create global config</ins>
        </a>.
        </div>
    </div>
</div>
<div class="row" style="display: none;" id="successCreatedMessageAlter">
    <div class="col-md-6 col-md-offset-2">
        <div class="alert alert-success alert-dismissible" role="alert">
            <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                    aria-hidden="true">&times;</span></button>
            <strong>Congratuate!</strong>&nbsp;You had craete application success,
            you may <a href="javascript:void(0)" onclick="loadContent('applicationList');">
            <ins>see all application</ins>
        </a> or <a href="javascript:void(0);" onclick="loadContent('addApplication')">
            <ins>Create another application</ins>
        </a>.
        </div>
    </div>
</div>
<div class="row" id="errorMessageAlter" style="display: none">
    <div class="col-md-6 col-md-offset-2">
        <div class="alert alert-danger alert-dismissible" role="alert">
            <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                    aria-hidden="true">&times;</span></button>
            <strong>Error!</strong><span id="errormessage"></span>.
        </div>
    </div>
</div>
<div class="row">
    <div class="col-md-6 col-md-offset-2">
        <div class="row">
            <form class="form-horizontal">
                <div class="form-group">
                    <label for="appCode" class="col-sm-3 control-label">应用编码:</label>
                    <div class="col-sm-9">
                        <input type="text" class="form-control" id="appCode" placeholder="Application code">
                    </div>
                </div>
                <div class="form-group">
                    <label for="inputPassword3" class="col-sm-3 control-label">应用描述:</label>
                    <div class="col-sm-9">
                        <input type="text" class="form-control" id="appDesc" placeholder="Application Description">
                    </div>
                </div>
                <div class="form-group">
                    <label for="isGlobalConfig" class="col-sm-3 control-label">使用全局配置:</label>
                    <div class="col-sm-3">
                        <input data-toggle="toggle" type="checkbox" id="isGlobalConfig"/>
                    </div>
                    <div class="col-sm-6" style="margin-top: 1%;display: none" id="isModifyGlobalConfig">
                        <input type="checkbox" id="isUpdateGlobalConfig"/> Update the global config
                    </div>
                </div>
                <p id="defaultConfigID" value="" style="display: none"></p>
                <div class="panel panel-default" id="sysConfigParam">
                    <div class="panel-heading">
                        告警配置
                    </div>
                    <div class="panel-body">
                        <div class="form-group">
                            <label for="inputPassword3" class="col-sm-3 control-label">告警周期:</label>
                            <div class="col-sm-9">
                                <input type="text" class="form-control" id="period" name="period"
                                       placeholder="10(M)"/>
                            </div>
                        </div>
                        <div class="form-group">
                            <label for="inputPassword3" class="col-sm-3 control-label">告警类型:</label>
                            <div class="col-sm-9">
                                <span>发送邮件</span>
                            </div>
                        </div>
                        <div class="form-group">
                            <label for="inputPassword3" class="col-sm-3 control-label">收件人地址:</label>
                            <div class="col-sm-9">
                                <input type="text-are" class="form-control" id="mailTo" name="mailTo"
                                       placeholder="Application Description">
                            </div>
                        </div>
                        <div class="form-group">
                            <label for="inputPassword3" class="col-sm-3 control-label">抄送人地址:</label>
                            <div class="col-sm-9">
                                <input type="text" class="form-control" id="mailCc" name="mailCc"
                                       placeholder="Application Description">
                            </div>
                        </div>
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-sm-offset-4 col-sm-1">
                        <button type="button" id="saveApplicationBtn" data-loading-text="Loading..." class="btn btn-primary"
                                autocomplete="off">
                            <strong><ins>Save</ins></strong>
                        </button>
                    </div>
                    <div class="col-sm-offset-1 col-sm-1">
                        <button type="button" class="btn btn-default" onclick="loadContent('applicationList');"><strong><ins>Cancle</ins></strong></button>
                    </div>
                </div>
            </form>
        </div>
    </div>
</script>
</#macro>


<#macro createglobalConfig>
<script type="text/x-jsrender" id="createGlobalConfigTmpl">
<div class="row" style="display: none" id="saveGlobalSuccessAlter">
    <div class="col-md-6 col-md-offset-2">
        <div class="alert alert-success alert-dismissible" role="alert">
            <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                    aria-hidden="true">&times;</span></button>
            <strong>Congratulate!</strong>&nbsp;Create global success,
            you may <a href="javascript:void(0);" onclick="loadContent('addApplication')">
            <ins>Create an application</ins>
        </a> or <a href="javascript:void(0);" onclick="loadContent('applicationList')">
            <ins>See all application</ins>
        </a>.
        </div>
    </div>
</div>
<div class="row" style="display: none;" id="saveGlobalFailedConfigAlter">
    <div class="col-md-6 col-md-offset-2">
        <div class="alert alert-warning alert-dismissible" role="alert">
            <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                    aria-hidden="true">&times;</span></button>
            <strong>Warning!</strong>
            <p id="errormessage"></p>.
        </div>
    </div>
</div>
<div class="row">
    <div class="col-md-6 col-md-offset-2">
        <div class="row">
            <form class="form-horizontal">
                <div class="panel panel-default" id="sysConfigParam">
                    <div class="panel-heading">
                        告警配置
                    </div>
                    <div class="panel-body">
                        <div class="form-group">
                            <label for="inputPassword3" class="col-sm-3 control-label">告警周期:</label>
                            <div class="col-sm-9">
                                <input type="text" class="form-control" id="period" name="period"
                                       placeholder="10(M)">
                            </div>
                        </div>
                        <div class="form-group">
                            <label for="inputPassword3" class="col-sm-3 control-label">告警类型:</label>
                            <div class="col-sm-9">
                                <span>发送邮件</span>
                            </div>
                        </div>
                        <div class="form-group">
                            <label for="inputPassword3" class="col-sm-3 control-label">收件人地址:</label>
                            <div class="col-sm-9">
                                <input type="text-are" class="form-control" id="mailTo" name="mailTo"
                                       placeholder="test@test.com,test@test.com">
                            </div>
                        </div>
                        <div class="form-group">
                            <label for="inputPassword3" class="col-sm-3 control-label">抄送人地址:</label>
                            <div class="col-sm-9">
                                <input type="text" class="form-control" id="mailCc" name="mailCc"
                                       placeholder="test@test.com,test@test.com">
                            </div>
                        </div>
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-sm-offset-4 col-sm-1">
                        <button type="button" id="saveBtn" data-loading-text="Loading..." class="btn btn-primary"
                                autocomplete="off">
                            Save it!
                        </button>
                    </div>
                    <div class="col-sm-offset-1 col-sm-1">
                        <button type="button" class="btn btn-default" onclick="loadContent('applicationList');">Cancle</button>
                    </div>
                </div>
            </form>
        </div>
    </div>

</script>
</#macro>

<#macro modifyApplication>
<script type="text/x-jsrender" id="modifyApplicationTmpl">
<div class="row" style="display: none;" id="successModifiedMessageAlter">
    <div class="col-md-6 col-md-offset-2">
        <div class="alert alert-success alert-dismissible" role="alert">
            <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                    aria-hidden="true">&times;</span></button>
            <strong>Congratuate!</strong>&nbsp;You had craete application success,
            you may <a href="javascript:void(0);" onclick="loadContent('applicationList')">
            <ins>see all application</ins>
        </a> or <a href="javascript:void(0);" onclick="loadContent('addApplication')">
            <ins>Create another application</ins>
        </a>.
        </div>
    </div>
</div>
<div class="row" id="errorModifiedMessageAlter" style="display: none">
    <div class="col-md-6 col-md-offset-2">
        <div class="alert alert-danger alert-dismissible" role="alert">
            <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                    aria-hidden="true">&times;</span></button>
            <strong>Error!</strong><span id="errormessage"></span>.
        </div>
    </div>
</div>
<div class="row">
    <div class="col-md-6 col-md-offset-2">
        <div class="row">
            <form class="form-horizontal">
                <div class="form-group">
                    <input type="hidden" id="applicationId" name="applicationId" value="{{:applicationId}}"/>
                    <label for="appCode" class="col-sm-3 control-label">应用编码:</label>
                    <div class="col-sm-9">
                        <span style="padding-top: 10%"><p id="appCode"></p></span>
                    </div>
                </div>
                <div class="form-group">
                    <label for="inputPassword3" class="col-sm-3 control-label">应用描述:</label>
                    <div class="col-sm-9">
                        <input type="text" class="form-control" id="appDesc" placeholder="Application Description">
                    </div>
                </div>
                <div class="form-group">
                    <label for="isGlobalConfig" class="col-sm-3 control-label">使用全局配置:</label>
                    <div class="col-sm-3">
                        <input data-toggle="toggle" type="checkbox" id="isGlobalConfig"/>
                    </div>
                    <div class="col-sm-6" style="margin-top: 1%;display: none" id="isModifyGlobalConfig">
                        <input type="checkbox" id="isUpdateGlobalConfig"/> Update the global config
                    </div>
                </div>
                <p id="defaultConfigID" value="" style="display: none"></p>
                <div class="panel panel-default" id="sysConfigParam">
                    <div class="panel-heading">
                        告警配置
                    </div>
                    <div class="panel-body">
                        <div class="form-group">
                            <label for="inputPassword3" class="col-sm-3 control-label">告警周期:</label>
                            <div class="col-sm-9">
                                <input type="text" class="form-control" id="period" name="period"
                                       placeholder="10(M)"/>
                            </div>
                        </div>
                        <div class="form-group">
                            <label for="inputPassword3" class="col-sm-3 control-label">告警类型:</label>
                            <div class="col-sm-9">
                                <span>发送邮件</span>
                            </div>
                        </div>
                        <div class="form-group">
                            <label for="inputPassword3" class="col-sm-3 control-label">收件人地址:</label>
                            <div class="col-sm-9">
                                <input type="text-are" class="form-control" id="mailTo" name="mailTo"
                                       placeholder="Application Description">
                            </div>
                        </div>
                        <div class="form-group">
                            <label for="inputPassword3" class="col-sm-3 control-label">抄送人地址:</label>
                            <div class="col-sm-9">
                                <input type="text" class="form-control" id="mailCc" name="mailCc"
                                       placeholder="Application Description">
                            </div>
                        </div>
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-sm-offset-4 col-sm-1">
                        <button type="button" id="updateBtn" data-loading-text="Loading..." class="btn btn-primary"
                                autocomplete="off">
                            <strong>
                                <ins>Update</ins>
                            </strong>
                        </button>
                    </div>
                    <div class="col-sm-offset-1 col-sm-1">
                        <button type="button" class="btn btn-default" onclick="loadContent('applicationList');"><strong>
                            <ins>Cancel</ins>
                        </strong></button>
                    </div>
                </div>
            </form>
        </div>
    </div>
</script>
</#macro>
