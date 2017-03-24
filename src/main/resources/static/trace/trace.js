$(document).ready(function () {
    var nodes = [];
    $.ajax({
        type: "GET",
        url: "../trace/trace.json",
        dataType: "json",
        success: function (data) {
            $.each(data, function (i, item) {
                nodes[i] = data[i];
                nodes[i].startTime = item.startTime;
                nodes[i].duration = item.duration;
                nodes[i].content = item.operationName;
                nodes[i].bgcolor = item.bgcolor;
                nodes[i].levelId = item.levelId;
                nodes[i].parentLevelId = item.parentLevelId;
                nodes[i].modal = item.modal;
                nodes[i].modal.type = item.modal.type;
                nodes[i].modal.cost = item.modal.cost;
                nodes[i].modal.businessFeild = item.modal.businessFeild;
                nodes[i].modal.code = item.modal.code;
                nodes[i].modal.hostMessage = item.modal.hostMessage;
                nodes[i].modal.process = item.modal.process;
                nodes[i].modal.exceptionStack = item.modal.exceptionStack;
                nodes[i].modal.method = item.modal.method;
            })
            drawAxis();
            displayData(nodes);
            animate();
            barHover();
            modalEffect();
            /*计算chart高度*/
            var chartHeight = ($(".chart li").height() + 2 * parseInt($(".chart li").css("padding-top"))) * (nodes.length);
            $(".chart").css({"height": chartHeight, "width": width});
        }

    })
    //list of nodes


    var margin = {top: 10, right: 20, bottom: 10, left: 20};
    var width = $(window).width() - margin.left - margin.right;
    var height = 50;
    var dataset = [];
    // var percentScale;
    // var bits;
    var bap = []; // bits and percentageScale => bap[0]=> bits; bap[1]=> percentageScale
    function drawAxis() {
        for (var key in nodes) {
            var startTime = nodes[key].startTime,
                duration = nodes[key].duration;
            dataset.push(startTime + duration);
        }
        bits = d3.max(dataset).toString().length;//json中最大值得位数
        percentScale = Math.ceil(d3.max(dataset) / Math.pow(10, (bits - 2)));//坐标轴缩放比例
        // console.log(Math.pow(10,(bits-2)));
        var svg = d3.select(".axis").append("svg")
            .attr("width", width)
            .attr("height", height);

        var xScale = d3.scale.linear()
            .domain([0, percentScale * Math.pow(10, (bits - 2))])
            .range([0, width]);

        var axis = d3.svg.axis() //新建一个坐标轴 
            .scale(xScale) //量度  
            .orient("top")//横坐标的刻度标注位于轴上方
            .ticks(20);

        svg.append("g")
            .attr("class", "axis")
            .attr("transform", "translate(0,30)")
            .call(axis);

        bap.push(bits);
        bap.push(percentScale);
        // console.log(bap);
        return bap;
    }

    //draw axis


    //display data
    function displayData(nodes) {
        $('.duration').html('');
        $('.nodes').html('');
        for (var key in nodes) {
            var startTime = nodes[key].startTime,
                duration = nodes[key].duration,
                content = nodes[key].content,
                bgcolor = nodes[key].bgcolor,
                id = nodes[key].id;


            $('.duration').append("<li><div data-percentage='"
                + startTime / (bap[1] * Math.pow(10, (bap[0] - 4)))
                + "' class='bar'>"
                + startTime
                + "</div><div data-percentage='"
                + duration / (bap[1] * Math.pow(10, (bap[0] - 4)))
                + "' data-modal='modal-1' class='bar md-trigger' id = '"
                + id
                + "' style='background-color:"
                + bgcolor
                + "'><span>"
                + content
                + "</span></div></li>");
            // var spanWidth = $(".duration #"+id+" span").width();
            // $(".duration #"+id+" span").css("margin-left",-spanWidth/2);
        }

    }


    //animate the data
    function animate() {
        $('.bar').css('width', '0px');
        $(".duration .bar").delay(1000).each(function (i) {
            var percentage = $(this).data('percentage');
            $(this).delay(i + "00").animate({'width': percentage + '%'}, 700);
        });
        $(".duration .bar span").css('opacity', '0');
        $(".duration .bar span").delay(1000).each(function (i) {
            $(this).delay((i + 5) * 100).animate({'opacity': '1'}, 500);
        });
    }

    //highlight all the parents when hover
    function barHover() {
        $(".chart .duration .bar:nth-child(even)").mouseenter(function () {
            var arr = [];
            //找到悬停的节点及其所有父节点函数          
            function traceParents(nodesid) {
                if (nodes[nodesid].parentLevelId == -1) {
                    arr.push(nodesid);
                    //console.log("arrlength="+arr.length);
                    var arrlength = arr.length;
                    return;
                }
                else {
                    for (var j = 0; j < nodes.length; j++) {
                        if (nodes[j].levelId == nodes[nodesid].parentLevelId) {
                            arr.push(nodesid);
                            traceParents(j);
                        }
                    }
                }

            }

            //获得当前悬停节点的位置
            var nodesid = $(this).attr("id").replace(/[^0-9]/ig, "");
            traceParents(nodesid);
            //console.log("id="+nodesid);

            //讲json数据节点数组与当前悬停节点及其父节点数组求差，即得到所有透明度降低的节点数组
            var nodesids = [];
            for (var i = 0; i < nodes.length; i++) {
                nodesids[i] = i;
            }
            var result = [];
            var tmp = nodesids.concat(arr);
            var o = {};
            for (var i = 0; i < tmp.length; i++) {
                (tmp[i] in o) ? o[tmp[i]]++ : o[tmp[i]] = 1;
            }
            for (x in o) if (o[x] == 1) {
                result.push(x);
            }
            //console.log(result);

            for (var k = 0; k < result.length; k++) {
                $("#nodes" + result[k]).css("opacity", 0.1);
            }


        }).mouseleave(function () {
            $(".chart .duration .bar:nth-child(even)").css("opacity", 1);
        });
    }

    //modal dialog show when click trigger
    function modalEffect() {
        var overlay = $(".md-overlay");
        var modal = $("#modal");
        var close = $(".md-close");
        var flag;

        function removeModal() {
            modal.removeClass('md-show');
        }

        $(".md-trigger").click(function (event) {
            var nodesid = $(this).attr("id").replace(/[^0-9]/ig, "");
            var method = nodes[nodesid].modal.method,
                type = nodes[nodesid].modal.type,
                cost = nodes[nodesid].modal.cost,
                businessField = nodes[nodesid].modal.businessField,
                code = nodes[nodesid].modal.code,
                hostMessage = nodes[nodesid].modal.hostMessage,
                process = nodes[nodesid].modal.process,
                exceptionStack = nodes[nodesid].modal.exceptionStack;
            $("#method").text(method);
            $("#type").text(type);
            $("#cost").text(cost);
            $("#businessField").text(businessField);
            $("#code").text(code);
            $("#hostMessage").text(hostMessage);
            $("#process").text(process);
            $("#exceptionStack").text(exceptionStack);

            modal.addClass("md-show");
            flag = 1;
            event.stopPropagation();//阻止事件冒泡,影响后面的点击关闭模态框事件 
        })
        $(".md-overlay").click(function () {
            if (!$(this).hasClass("md-modal") && flag == 1) {
                removeModal();
                flag = 0;
            }
        });
    }


    function resize() {
        $.ajax({
            type: "GET",
            url: "../trace/trace.json",
            dataType: "json",
            success: function (data) {
                $.each(data, function (i, item) {
                    nodes[i] = data[i];
                    nodes[i].startTime = item.startTime;
                    nodes[i].duration = item.duration;
                    nodes[i].content = item.operationName;
                    nodes[i].bgcolor = item.bgcolor;
                    nodes[i].levelId = item.levelId;
                    nodes[i].parentLevelId = item.parentLevelId;
                })

                width = $(window).width() - margin.left - margin.right;//重新取svg的宽度
                d3.selectAll("svg").remove();//清空原绘版区域svg坐标轴
                drawAxis();//重新绘制坐标轴
                displayData(nodes);
                animate();
                barHover();
                /*计算chart高度*/
                chartHeight = ($(".chart li").height() + 2 * parseInt($(".chart li").css("padding-top"))) * (nodes.length);
                $(".chart").css({"height": chartHeight, "width": width});
            }
        })
    }

    d3.select(window).on('resize', resize);
});  