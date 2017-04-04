/**
 * @author pengys5
 */
var colors = [
    "#F2C2CE",
    "#A7D8F0",
    "#FADDA2",
    "#8691C5",
    "#E8DB62",
    "#BDC8E7",
    "#F2A7A8",
    "#F5E586",
    "#91C3ED",
    "#96B87F",
    "#EE8D87",
    "#BDDCAB",
    "#68B9C7",
    "#93DAD6",
    "#EEBE84",
    "#83B085",
    "#8CCCD2",
    "#C5DFE8",
    "#F2B75B",
    "#C8DC60"
];

var nodes = [];
var idMap = {};
var colorMap = {};
var colorIndex = 0;
var percentScale;
var dataSet = [];
var bap = [];
var width = $("#traceStackDiv").width();
var height = 36;
var margin = 10;

function buildNodes(data) {
    sortChildren(data);

    $.each(data, function (item) {
        buildNode(data[item]);

        var children = data[item].childSpans;
        buildNodes(children);
    })
}
var group = 0;
function sortChildren(data) {
    if (data.length < 2) {
        return;
    }

    var tmp;
    for (var i = 0; i < data.length; i++) {
        for (var j = i + 1; j < data.length; j++) {
            var spanId_i = Number(data[i].spanId);
            var spanId_j = Number(data[j].spanId);
            if (spanId_j < spanId_i) {
                tmp = data[i];
                data[i] = data[j];
                data[j] = tmp;
            }
        }
    }
    console.log("group: " + group);
    console.log(data);

    group++;
}

function buildNode(data) {
    var node = {};

    var spanSegId = data.spanSegId.replace(/\./g, 'A');
    var parentSpanSegId = data.parentSpanSegId.replace(/\./g, 'A');

    node.segId = data.segId;
    node.appCode = data.appCode;
    node.startTime = data.relativeStartTime;
    node.duration = data.cost;
    node.content = data.operationName;
    node.spanSegId = spanSegId;
    node.parentSpanSegId = parentSpanSegId;
    nodes.push(node);

    if (!colorMap[data.appCode]) {
        colorMap[data.appCode] = colors[colorIndex];
        colorIndex++;
    }

    idMap[spanSegId] = nodes.length - 1;
}

function drawStack(globalId) {
    reset();
    nodes = [];
    colorMap = {};

    $.ajax({
        type: "GET",
        url: "/loadGlobalTraceData?globalId=" + globalId,
        dataType: "json",
        success: function (data) {
            buildNodes($.parseJSON(data));
            drawAxis();
            displayData();
            drawLegend();
        }
    });
    d3.select(window).on('resize', resize);
}

function reset() {
    d3.select("svg").remove();
    idMap = {};
    colorIndex = 0;
    dataSet = [];
    bap = [];
}

function drawAxis() {
    console.log(nodes);
    for (var key in nodes) {
        var startTime = nodes[key].startTime,
            duration = nodes[key].duration;
        dataSet.push(startTime + duration);
    }
    var bits = d3.max(dataSet).toString().length;
    percentScale = Math.ceil(d3.max(dataSet) / Math.pow(10, (bits - 2)));
    var axisHeight = 20;

    var svg = d3.select(".axis").append("svg")
        .attr("width", width)
        .attr("height", axisHeight);

    var xScale = d3.scale.linear()
        .domain([0, percentScale * Math.pow(10, (bits - 2))])
        .range([0, width]);

    var axis = d3.svg.axis()
        .scale(xScale)
        .orient("top")
        .ticks(20);

    svg.append("g")
        .attr("class", "axis")
        .attr("transform", "translate(0," + axisHeight + ")")
        .call(axis);

    bap.push(bits);
    bap.push(percentScale);
    return bap;
}

function drawLegend() {
    $("#legendDiv").empty();
    $.each(colorMap, function (key, val) {
        $("#legendDiv").append("<button type='button' class='btn btn-xs legend' style='background-color: " + val + ";'>" + key + "</button>")

    });
}

function displayData() {
    $('.duration').html('');
    $('.nodes').html('');

    var svgContainer = d3.select(".duration").append("svg").attr("height", height * nodes.length);

    for (var key in nodes) {
        var startTime = nodes[key].startTime,
            duration = nodes[key].duration,
            content = nodes[key].content,
            segId = nodes[key].segId,
            appCode = nodes[key].appCode,
            spanSegId = nodes[key].spanSegId,
            parentSpanSegId = nodes[key].parentSpanSegId;

        // svgContainer.append("rect").attr("x", 0).attr("y", key * height).attr("width", "100%").attr("height", height).style("fill", color);

        var rectWith = ((duration * width) / (bap[1] * Math.pow(10, (bap[0] - 4)))) / 100;
        var beginX = ((startTime * width) / (bap[1] * Math.pow(10, (bap[0] - 4)))) / 100;
        var bar = svgContainer.append("g")
            .attr("transform", function (d, i) {
                return "translate(0," + i * height + ")";
            });

        var beginY = key * height;
        console.log("x: " + beginX + ",y: " + beginY + ",width: " + rectWith + ",id: " + spanSegId);
        bar.append("rect").attr("x", beginX).attr("y", beginY).attr("width", rectWith).attr("height", height - margin)
            .attr("id", spanSegId).style("fill", colorMap[appCode])
            .on("click", function () {
                showSpanModal(d3.select(this).attr("id"));
            });

        bar.append("text")
            .attr("x", beginX + 5)
            .attr("y", key * height + (height / 2))
            .attr("class", "rectText")
            .text(content);

        if (parentSpanSegId != "-1") {
            var parentSvg = d3.select("#" + parentSpanSegId);
            var parentX = parentSvg.attr("x");
            var parentY = parentSvg.attr("y");

            var defs = svgContainer.append("defs");
            var arrowMarker = defs.append("marker")
                .attr("id", "arrow")
                .attr("markerUnits", "strokeWidth")
                .attr("markerWidth", 12)
                .attr("markerHeight", 12)
                .attr("viewBox", "0 0 12 12")
                .attr("refX", 6)
                .attr("refY", 6)
                .attr("orient", "auto")
            var arrow_path = "M2,2 L10,6 L2,10 L6,6 L2,2";
            arrowMarker.append("path")
                .attr("d", arrow_path)
                .attr("fill", "#333")

            var parentLeftBottomX = parentX;
            var parentLeftBottomY = Number(parentY) + Number(height) - Number(margin);
            var selfMiddleX = beginX;
            var selfMiddleY = beginY + ((height - margin) / 2);

            var offX = 15;
            var offY = 6;
            if ((beginX - parentX) < 10) {
                svgContainer.append("line").attr("x1", parentLeftBottomX - offX).attr("y1", parentLeftBottomY - offY).attr("class", "connlines")
                    .attr("x2", parentLeftBottomX).attr("y2", parentLeftBottomY - offY);

                svgContainer.append("line").attr("x1", parentLeftBottomX - offX).attr("y1", parentLeftBottomY - offY).attr("class", "connlines")
                    .attr("x2", parentLeftBottomX - offX).attr("y2", selfMiddleY);

                svgContainer.append("line").attr("x1", parentLeftBottomX - offX).attr("y1", selfMiddleY).attr("class", "connlines")
                    .attr("x2", selfMiddleX).attr("y2", selfMiddleY).attr("marker-end", "url(#arrow)");
            } else {
                svgContainer.append("line").attr("x1", parentLeftBottomX).attr("y1", parentLeftBottomY).attr("class", "connlines")
                    .attr("x2", parentLeftBottomX).attr("y2", selfMiddleY);

                svgContainer.append("line").attr("x1", parentLeftBottomX).attr("y1", selfMiddleY).attr("class", "connlines")
                    .attr("x2", selfMiddleX).attr("y2", selfMiddleY).attr("marker-end", "url(#arrow)");
            }
        }
    }

    var vlineSum = percentScale;

    function vLineSumPow(vlineSum) {
        vlineSum = vlineSum * 2;
        if (vlineSum < 100) {
            return vLineSumPow(vlineSum);
        } else {
            return vlineSum;
        }
    }

    if (vlineSum < 100) {
        vlineSum = vLineSumPow(vlineSum);
    }

    var columnWidth = width / vlineSum;
    d3.range(vlineSum).forEach(function (i) {
        svgContainer.append("line")
            .attr("x1", function () {
                return i * columnWidth;
            })
            .attr("y1", 0)
            .attr("x2", function () {
                return i * columnWidth;
            })
            .attr("y2", 200)
            .attr("class", "vlines")
            .on("mouseover", function (d) {
                d3.select(this).style("stroke-opacity", "1");
            })
            .on("mouseout", function (d) {
                d3.select(this).style("stroke-opacity", "0");
            });
        ;
    });
}

function resize() {
    reset();
    width = $("#traceStackDiv").width();
    console.log("drawAxis");
    drawAxis();
    console.log("displayData");
    displayData(nodes);
    drawLegend();
}