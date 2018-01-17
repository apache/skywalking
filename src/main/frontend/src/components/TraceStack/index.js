import React, { PureComponent } from 'react';
import { Tag } from 'antd';
import * as d3 from 'd3';
import styles from './index.less';

const colors = [
  '#F2C2CE',
  '#A7D8F0',
  '#FADDA2',
  '#8691C5',
  '#E8DB62',
  '#BDC8E7',
  '#F2A7A8',
  '#F5E586',
  '#91C3ED',
  '#96B87F',
  '#EE8D87',
  '#BDDCAB',
  '#68B9C7',
  '#93DAD6',
  '#EEBE84',
  '#83B085',
  '#8CCCD2',
  '#C5DFE8',
  '#F2B75B',
  '#C8DC60',
];
const height = 36;
const margin = 10;
const offX = 15;
const offY = 6;
class TraceStack extends PureComponent {
  state = {
    nodes: [],
    idMap: {},
    colorMap: {},
    bap: [],
  }
  componentWillMount() {
    const { spans } = this.props;
    spans.forEach(this.buildNode);
    const { nodes } = this.state;
    const minStartTimeNode = nodes.reduce((acc, n) => (acc.startTime > n.startTime ? n : acc));
    this.state.nodes = nodes.map(n =>
      ({ ...n, startOffset: n.startTime - minStartTimeNode.startTime }));
  }
  componentDidMount() {
    this.state.width = this.axis.parentNode.clientWidth - 50;
    this.drawAxis();
    this.displayData();
    window.addEventListener('resize', this.resize);
  }
  buildNode = (span, index) => {
    const { nodes, colorMap, idMap } = this.state;
    const node = {};
    node.applicationCode = span.applicationCode;
    node.startTime = span.startTime;
    node.endTime = span.endTime;
    node.duration = span.endTime - span.startTime;
    node.content = span.operationName;
    node.spanSegId = span.spanId;
    node.parentSpanSegId = span.parentSpanId;
    nodes.push(node);

    if (!colorMap[span.applicationCode]) {
      colorMap[span.applicationCode] = colors[index];
    }
    idMap[node.spanSegId] = nodes.length - 1;
  }
  drawAxis = () => {
    const { width } = this.state;
    const { nodes, bap } = this.state;
    const dataSet = nodes.map(node => node.startOffset + node.duration);
    const bits = d3.max(dataSet).toString().length;
    const percentScale = Math.ceil(d3.max(dataSet) / (10 ** (bits - 2)));
    const axisHeight = 20;

    const svg = d3.select(this.axis).append('svg')
      .attr('width', width)
      .attr('height', axisHeight)
      .attr('style', 'overflow: visible');

    const xScale = d3.scaleLinear()
      .domain([0, percentScale * (10 ** (bits - 2))])
      .range([0, width]);

    const axis = d3.axisTop(xScale).ticks(20);

    svg.append('g')
      .attr('class', styles.axis)
      .attr('transform', `translate(0, ${axisHeight})`)
      .call(axis);

    bap.push(bits);
    bap.push(percentScale);
    return bap;
  }
  displayData = () => {
    const { nodes, bap, width, colorMap } = this.state;
    const svgContainer = d3.select(this.duration).append('svg').attr('height', height * nodes.length).attr('style', 'overflow: visible');
    const positionMap = {};
    nodes.forEach((node, index) => {
      const { startOffset: startTime, duration, content,
        applicationCode, spanSegId, parentSpanSegId } = node;

      const rectWith = ((duration * width) / (bap[1] * (10 ** (bap[0] - 4)))) / 100;
      const beginX = ((startTime * width) / (bap[1] * (10 ** (bap[0] - 4)))) / 100;
      const bar = svgContainer.append('g').attr('transform', (d, i) => `translate(0,${i * height})`);

      const beginY = index * height;
      positionMap[spanSegId] = { x: beginX, y: beginY };

      bar.append('rect').attr('x', beginX).attr('y', beginY).attr('width', rectWith)
        .attr('height', height - margin)
        .style('fill', colorMap[applicationCode]);

      bar.append('rect').attr('spanSegId', spanSegId).attr('x', 0).attr('y', beginY)
        .attr('width', width)
        .attr('height', height - margin)
        .style('opacity', '0')
        .on('click', () => { console.info(spanSegId); });

      bar.append('text')
        .attr('x', beginX + 5)
        .attr('y', (index * height) + (height / 2))
        .attr('class', styles.rectText)
        .text(content);
      if (index > 0) {
        const parentX = positionMap[parentSpanSegId].x;
        const parentY = positionMap[parentSpanSegId].y;

        const defs = svgContainer.append('defs');
        const arrowMarker = defs.append('marker')
          .attr('id', 'arrow')
          .attr('markerUnits', 'strokeWidth')
          .attr('markerWidth', 12)
          .attr('markerHeight', 12)
          .attr('viewBox', '0 0 12 12')
          .attr('refX', 6)
          .attr('refY', 6)
          .attr('orient', 'auto');
        arrowMarker.append('path')
          .attr('d', 'M2,2 L10,6 L2,10 L6,6 L2,2')
          .attr('fill', '#333');

        const parentLeftBottomX = parentX;
        const parentLeftBottomY = (Number(parentY) + Number(height)) - Number(margin);
        const selfMiddleX = beginX;
        const selfMiddleY = beginY + ((height - margin) / 2);
        if ((beginX - parentX) < 10) {
          svgContainer.append('line').attr('x1', parentLeftBottomX - offX).attr('y1', parentLeftBottomY - offY).attr('class', styles.connlines)
            .attr('x2', parentLeftBottomX)
            .attr('y2', parentLeftBottomY - offY);

          svgContainer.append('line').attr('x1', parentLeftBottomX - offX).attr('y1', parentLeftBottomY - offY).attr('class', styles.connlines)
            .attr('x2', parentLeftBottomX - offX)
            .attr('y2', selfMiddleY);

          svgContainer.append('line').attr('x1', parentLeftBottomX - offX).attr('y1', selfMiddleY).attr('class', styles.connlines)
            .attr('x2', selfMiddleX)
            .attr('y2', selfMiddleY)
            .attr('marker-end', 'url(#arrow)');
        } else {
          svgContainer.append('line').attr('x1', parentLeftBottomX).attr('y1', parentLeftBottomY).attr('class', styles.connlines)
            .attr('x2', parentLeftBottomX)
            .attr('y2', selfMiddleY);

          svgContainer.append('line').attr('x1', parentLeftBottomX).attr('y1', selfMiddleY).attr('class', styles.connlines)
            .attr('x2', selfMiddleX)
            .attr('y2', selfMiddleY)
            .attr('marker-end', 'url(#arrow)');
        }
      }
    });
  }
  showSpanModal = () => {}
  resize = () => {
    this.state.width = this.axis.parentNode.clientWidth - 50;
    if (!this.axis || this.state.width <= 0) {
      return;
    }
    this.axis.innerHTML = '';
    this.duration.innerHTML = '';
    this.drawAxis();
    this.displayData();
  }
  render() {
    const { colorMap } = this.state;
    const legendButtons = Object.keys(colorMap).map(key =>
      (<Tag color={colorMap[key]}>{key}</Tag>));
    return (
      <div className={styles.stack}>
        <div style={{ 'padding-bottom': 10 }}>
          { legendButtons }
        </div>
        <div ref={(el) => { this.axis = el; }} />
        <div className={styles.duration} ref={(el) => { this.duration = el; }} />
      </div>
    );
  }
}

export default TraceStack;
