<#import "./common/commons.ftl" as common>
<!DOCTYPE html>
<html lang="zh-CN">

<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<@common.importResources />
    <script src="${_base}/bower_components/vue/dist/vue.min.js"></script>
    <script src="${_base}/bower_components/jquery-ui/jquery-ui.min.js"></script>
    <link href="${_base}/bower_components/jquery-treetable/css/jquery.treetable.css" rel="stylesheet" type="text/css"/>
    <link rel="stylesheet" href="${base}/bower_components/jquery-treetable/css/jquery.treetable.theme.default.css"/>
    <script src="${_base}/bower_components/jquery-treetable/jquery.treetable.js"></script>
    <link href="${_base}/bower_components/jquery-treetable/css/jquery.treetable.css" rel="stylesheet"/>
</head>

<body style="padding-top:80px">
<@common.navbar/>
<div class="container">
    <p id="key" style="display: none">${key}</p>
    <p id="searchType" style="display: none">${searchType}</p>
    <div class="row" id="data">
        <table id="traceTreeTable" >
            <caption>
            </caption>
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
            <template v-for="treeNode in traceTree.treeNodes">
                <tr v-if="treeNode.isEntryNode" statusCodeStr="{{treeNode.statusCodeStr}}"
                    data-tt-id='{{treeNode.colId}}'>
                    <td><b>{{treeNode.applicationIdStr}}</b></td>
                    <td>{{treeNode.spanTypeName}}</td>
                    <td>{{treeNode.statusCodeName}}</td>
                    <td>
                        <a href="#" >{{treeNode.viewPointIdSub}}</a>
                    </td>
                </tr>
                <tr v-else statusCodeStr="{{treeNode.statusCodeStr}}" data-tt-id='{{treeNode.colId}}'
                    data-tt-parent-id='{{treeNode.parentLevel}}'>
                    <td><b>{{treeNode.applicationIdStr}}</b></td>
                    <td>{{treeNode.spanTypeName}}</td>
                    <td>{{treeNode.statusCodeName}}</td>
                    <td>
                        <a href="#" >{{treeNode.viewPointIdSub}}</a>
                    </td>
                </tr>
            </template>
            </tbody>
        </table>
    </div>
</div>
<script>
    new Vue({
        el: "#data",
        methods: {
            changeData: function (data) {
                this.traceTree.totalTime = data.endTime - data.beginTime;
                var totalTime = this.traceTree.totalTime;
                this.traceTree.startTime = data.beginTime;
                this.traceTree.endTime = data.endTime;
                var tmpNode;
                for (var i = 0; i < data.nodes.length; i++) {
                    tmpNode = data.nodes[i];
                    if (tmpNode.colId == "0") {
                        tmpNode.isEntryNode = true;
                    } else {
                        tmpNode.isEntryNode = false;
                    }
                    if (tmpNode.spanTypeName == "") {
                        tmpNode.spanTypeName = "UNKNOWN";
                    }
                    tmpNode.statusCodeName = tmpNode.statusCodeName;
                    if (tmpNode.statusCodeName == "") {
                        tmpNode.statusCodeName = "MISSING";
                    }

                    if (tmpNode.timeLineList.length == 1) {
                        tmpNode.case = 1;
                        tmpNode.offset = tmpNode.cost;
                        tmpNode.totalLength = (tmpNode.startTime - this.traceTree.beginTime);
                    }

                    if (tmpNode.timeLineList.length == 2) {
                        if (tmpNode.timeLineList[1].startTime < tmpNode.timeLineList[0].startTime) {
                            tmpNode.case = 2;
                            tmpNode.serverLength = tmpNode.timeLineList[1].cost;
                            tmpNode.totalLength = (tmpNode.timeLineList[1].startTime - this.traceTree.beginTime);
                        } else if ((tmpNode.timeLineList[1].startTime >= tmpNode.timeLineList[0].startTime) &&
                                ((tmpNode.timeLineList[1].startTime) <= (tmpNode.timeLineList[0].startTime + tmpNode.timeLineList[0].cost))) {
                            if ((tmpNode.timeLineList[1].startTime + tmpNode.timeLineList[1].cost) <= (tmpNode.timeLineList[0].startTime + tmpNode.timeLineList[0].cost)) {
                                tmpNode.case = 3;
                                tmpNode.totalLength = tmpNode.timeLineList[0].startTime - this.traceTree.beginTime;
                                tmpNode.clientOffset = (tmpNode.timeLineList[1].startTime - tmpNode.timeLineList[0].startTime);
                                tmpNode.serverOffset = (tmpNode.timeLineList[1].startTime + tmpNode.timeLineList[1].cost - tmpNode.timeLineList[1].startTime);
                                tmpNode.serverLength = (tmpNode.timeLineList[0].startTime + tmpNode.timeLineList[0].cost - tmpNode.timeLineList[1].startTime - tmpNode.timeLineList[1].cost);
                            } else {
                                tmpNode.case = 4;
                                tmpNode.totalLength = (tmpNode.timeLineList[0].startTime - this.traceTree.startTime);
                                tmpNode.clientOffset = tmpNode.timeLineList[1].startTime - tmpNode.timeLineList[0].startTime;
                                tmpNode.serverLength = tmpNode.timeLineList[1].startTime + tmpNode.timeLineList[1].cost - tmpNode.timeLineList[1].startTime;
                            }
                        } else {
                            tmpNode.case = 5;
                            tmpNode.totalLength = (tmpNode.timeLineList[0].startTime - this.traceTree.startTime);
                            tmpNode.warningOffset = tmpNode.timeLineList[0].cost;
                            tmpNode.serverOffset = tmpNode.timeLineList[1].startTime - tmpNode.timeLineList[0].startTime;
                            tmpNode.serverLength = tmpNode.timeLineList[1].cos;
                        }
                    }

                    this.traceTree.treeNodes.push(tmpNode);
                }
            }
        },
        ready: function () {
            var self = this;
            var url = "${_base}/search/traceId/" + $("#key").text();
            $.ajax({
                type: 'POST',
                url: url,
                dataType: 'json',
                success: function (data) {
                    if (data.code == '200') {
                        self.changeData(jQuery.parseJSON(data.result));
                        self.$nextTick(function () {
                            $("#traceTreeTable").treetable({expandable: true, indent: 10, clickableNodeNames: true});
                        })
                    }
                },
                error: function () {
                    $("#errorMessage").text("Fatal Error, please try it again.");
                    $("#alertMessageBox").show();
                }
            });
        },
        data: {
            traceTree: {
                totalTime: 0,
                startTime: 0,
                endTime: 0,
                treeNodes: []
            }
        },
        filter: {}
    });


</script>
</body>
</html>
