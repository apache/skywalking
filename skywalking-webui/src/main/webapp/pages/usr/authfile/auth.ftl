<#macro downloadAuth>
<script type="text/x-jsrender" id="downloadAuthFileTmpl">
<div class="row" style="display: none;" id="warningAlter">
    <div class="col-md-6 col-md-offset-2">
        <div class="alert alert-warning alert-dismissible" role="alert">
            <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span
                    aria-hidden="true">&times;</span></button>
            <strong>Warning!</strong>&nbsp;<p id="message"></p>
        </div>
    </div>
</div>
<div class="row">
    <div class="col-md-6 col-md-offset-2">
        <div class="row">
            <form class="form-horizontal" role="form">
                <input type='hidden' id="applicationId" value="{{:applicationCode}}">
                <div class="form-group">
                    <label for="exclusiveException" class="col-sm-3 control-label">需要排除的异常:</label>
                    <div class="col-sm-9">
                        <input type="text" class="form-control" id="exclusiveException"
                               placeholder="java.lang.Exception,java.io.IOException">
                    </div>
                </div>
                <div class="form-group">
                    <label for="authType" class="col-sm-3 control-label">授权文件类型:</label>
                    <div class="col-sm-9">
                        <select class="form-control" id="authType" name="authType">
                            <option value="1">外网</option>
                            <option value="0">内网</option>
                        </select>
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-sm-offset-4 col-sm-1">
                        <button type="button" id="downloadBtn" data-loading-text="Loading..."
                                class="btn btn-primary"
                                autocomplete="off">
                            <strong>
                                <ins>Download</ins>
                            </strong>
                        </button>
                    </div>
                    <div class="col-sm-offset-1 col-sm-1">
                        <button type="button" class="btn btn-default" onclick="loadContent('applicationList');"><strong>
                            <ins>Cancle</ins>
                        </strong></button>
                    </div>
                </div>
            </form>
        </div>
    </div>
    <div id="authFiledownLoad"></div>
</div>
</script>
</#macro>