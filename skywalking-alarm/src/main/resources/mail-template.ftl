<!DOCTYPE html PUBLIC"-//W3C//DTD XHTML 1.0 Transitional//EN""http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns=\"http://www.w3.org/1999/xhtml\">
<head>
    <meta http-equiv="Content-Type"content="text/html;charset=utf-8">
    <style type="text/css">
        li{text-indent:2em}
        body {
            color: #4f6b72;0
            background: #E6EAE9;
        }
        #mainTable {
            text-align:left;
            font:12px/15px simsun;
            line-height:20px;
            color:#000;
            background: #fff;
            padding:30px 20px;
            border:20px solid #fff;
            margin:0 auto;
            border-collapse:collapse;
            border-spacing:0;
        }
        a {
            color: #c75f3e;
        }
        .greetings {
            background:#f9f9f9;
            font-family:Microsoft YaHei,SimHei,Arial;

        }
        #mainTd {
            margin:0;
            padding:0;
        }
        #mainDiv {
            background:#f9f9f9;
            padding:25px;
            font-size:14px;
            line-height:25px;
            margin:5px 0;
            overflow:hidden;
        }
        #dataTable {
            border-collapse:collapse;
            border:none;
        }

        #dataTableHead {
            display: none
        }	
         
        #dataTable td {
            border-bottom: 1px solid #C1DAD7;
            background: #fff;
            font-size:11px;
            padding: 6px 6px 6px 12px;
            color: #4f6b72;
        }

        #dataTable  td.alt {
            border-right: 1px solid #C1DAD7;
            background: #F5FAFA;
            color: #797268;

        }

        th.spec {
            border: 1px solid #C1DAD7;
            background: #CAE8EA ;
            font: bold 10px "Trebuchet MS", Verdana, Arial, Helvetica, sans-serif;
            padding: 6px 6px 6px 12px;
        }

        th.specalt {
            border: 1px solid #C1DAD7;
            background: #f5fafa;
            font: bold 10px "Trebuchet MS", Verdana, Arial, Helvetica, sans-serif;
            color: #797268;
            padding: 6px 6px 6px 12px;
        }        
         .type td p {
            font: bold 14px "Trebuchet MS", Verdana, Arial, Helvetica, sans-serif;   
         }
        
    </style>
    <title>
        templete1_welcome
    </title>
</head>

<body>

<div id="mainDiv">
    <p class="greetings">Dear ${name!}:</p>
    <p class="greetings">&nbsp&nbsp&nbsp&nbspOur platform received alarm infomation between <b>${startDate!} </b>to<b> ${endDate!}</b> as follows:
    </p>
    <table id="dataTable" width="80%">
        <tr id="dataTableHead">
            <td width="15%"></td>
            <td></td>
        </tr>
    <#list alarmTypeList as alarmType>
        <#if warningMap?exists>
            <#if warningMap[alarmType.type]?exists>
                <tr class="type">
                    <td class="typeTd" colspan="2" style="padding: 6px 6px 6px 6px;"><p>${alarmType.desc}</p></td>
                </tr>
                <#list warningMap[alarmType.type] as element>
                    <tr>
                        <th class="spec"><p>traceid</p></th>
                        <td class="alt"><a href="${(portalAddr + element.traceid)!}">${element.traceid!}</a><#if element.date?exists>&nbsp&nbsp&nbsp&nbsp<span>${element.date?string("yyyy-MM-dd HH:mm:ss ")}</span></#if></td>
                    </tr>
                    <tr>
                        <th class="specalt"><p>${(alarmType.label)}</p></th>
                        <td class="alt"><p>${element.exceptionMsg}</p></td>
                    </tr>
                </#list>
            </#if>
        </#if>
    </#list>
    </table>
</body>

</html>