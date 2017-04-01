/**
 * @author pengys5
 */

var network, nodes, edges;
startNetwork();

window.onresize = function () {
    resizeDagDivSize();
}

$(document).ready(function () {
    resizeDagDivSize();
});

function resizeDagDivSize() {
    var width = $(document.body).width();
    var height = $(document).height();
    $("#traceDagDiv").width(width - 360).height(height - 100);
}

function loadDag(data) {
    for (var i in data.nodes) {
        addNode(data.nodes[i]);
    }

    for (var i in data.nodeRefs) {
        addEdge(data.nodeRefs[i]);
    }
};

function addNode(node) {
    nodes.add({
        id: node.id,
        label: node.label,
        image: createNode(node.image, node.instNum),
        shape: 'image'
    });
}

function addEdge(nodeRef) {
    edges.add({from: nodeRef.from, to: nodeRef.to, label: nodeRef.resSum});
}

function startNetwork() {
    // create a network
    var container = document.getElementById('traceDagDiv');
    nodes = new vis.DataSet();
    edges = new vis.DataSet();

    var data = {
        nodes: nodes,
        edges: edges
    };
    var options = {
        nodes: {
            borderWidth: 1,
            brokenImage: './public/img/node/UNDEFINED.png',
            color: {
                background: '#ffffff'
            },
            shapeProperties: {
                useImageSize: true
            },
            fixed: true
        },
        edges: {
            color: '#dd7e6b',
            arrows: {
                to: {enabled: true, scaleFactor: 0.5, type: 'arrow'}
            },
            smooth: false
        },
        layout: {
            improvedLayout: true,
            hierarchical: {
                enabled: true,
                levelSeparation: 200,
                nodeSpacing: 150,
                parentCentralization: false,
                direction: "LR",
                sortMethod: 'directed'
            }
        }
    };

    network = new vis.Network(container, data, options);
}

function startUpdateTimer() {
    $('body').everyTime('5s', function () {
        todayDagLoad();
    });
}

function stopUpdateTimer() {
    $('body').stopTime();
}

function todayDagLoad() {
    var endTimeStr = moment().format("YYYYMMDD") + "0000";
    var startTimeStr = moment().subtract(30, 'days').format("YYYYMMDD") + "0000";

    loadDateRangeDag("day", startTimeStr, endTimeStr);
    loadCostData("day", startTimeStr, endTimeStr);
}

function loadDateRangeDag(slice, startTimeStr, endTimeStr) {
    console.log("slice: " + slice + ", startTimeStr: " + startTimeStr + ", endTimeStr:" + endTimeStr);
    $.getJSON("dagNodesLoad?timeSliceType=" + slice + "&startTime=" + startTimeStr + "&endTime=" + endTimeStr, function (data) {
        nodes.clear();
        edges.clear();
        loadDag(data);
        network.stabilize();
    });
}