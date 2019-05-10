/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import React, { PureComponent } from 'react';
import { Tag, List, Card, Row, Col, Badge, Button } from 'antd';
import * as d3 from 'd3';
import moment from 'moment';
import { formatDuration } from '../../utils/time';
import DescriptionList from "../DescriptionList";
import styles from './index.less';
import TraceTree from '../TraceTree';

const ButtonGroup = Button.Group;

const { Description } = DescriptionList;
const height = 36;
const margin = 10;
const offX = 15;
const offY = 6;
const timeFormat = 'YYYY-MM-DD HH:mm:ss.SSS';
class TraceStack extends PureComponent {
  state = {
    nodes: [],
    idMap: {},
    colorMap: {},
    bap: [],
    span: {},
    key: 'tags',
    treeMode: true,
  }

  componentWillMount() {
    const { spans } = this.props;
    const { colorMap } = this.state;
    const serviceList = Array.from(new Set(spans.map(i => i.serviceCode)));
    const sequentialScale = d3.scaleSequential()
    .domain([0, serviceList.length])
    .interpolator(d3.interpolateCool);
    spans.forEach((span) => {
      if (!colorMap[span.serviceCode]) {
        colorMap[span.serviceCode] = sequentialScale(serviceList.indexOf(span.serviceCode))
      }
      this.buildNode(span);
    });
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

  onTabChange = (key, type) => {
    this.setState({ [type]: key });
  }

  buildNode = (span) => {
    const { nodes, idMap } = this.state;
    const node = {};
    node.serviceCode = span.serviceCode;
    node.startTime = span.startTime;
    node.endTime = span.endTime;
    node.duration = span.endTime - span.startTime;
    node.content = span.endpointName;
    node.spanSegId = this.id(span.segmentId, span.spanId);
    node.parentSpanSegId = this.findParent(span);
    node.refs = span.refs;
    node.type = span.type;
    node.peer = span.peer;
    node.component = span.component;
    node.isError = span.isError;
    node.layer = span.layer;
    node.tags = span.tags;
    node.logs = span.logs;
    nodes.push(node);
    idMap[node.spanSegId] = nodes.length - 1;
  }

  id = (...seg) => seg.join();

  findParent = (span) => {
    const { spans } = this.props;
    if (span.refs) {
      const ref = span.refs.find(_ => spans.findIndex(s =>
        this.id(_.parentSegmentId, _.parentSpanId) === this.id(s.segmentId, s.spanId)) > -1);
      if (ref) {
        return this.id(ref.parentSegmentId, ref.parentSpanId);
      }
    }
    const result = this.id(span.segmentId, span.parentSpanId);
    if (spans.findIndex(s => result === this.id(s.segmentId, s.spanId)) > -1) {
      return result;
    }
    return null;
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
      .domain([0, d3.max(dataSet)])
      .range([0, width]);

    const axis = d3.axisTop(xScale).ticks(4).tickSize([(height * nodes.length) + 40])
      .tickFormat(formatDuration);

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
        serviceCode, spanSegId, parentSpanSegId } = node;

      const rectWith = ((duration * width) / (bap[1] * (10 ** (bap[0] - 4)))) / 100;
      const beginX = ((startTime * width) / (bap[1] * (10 ** (bap[0] - 4)))) / 100;
      const bar = svgContainer.append('g').attr('transform', (d, i) => `translate(0,${i * height})`);

      const beginY = index * height;
      positionMap[spanSegId] = { x: beginX, y: beginY };
      const rectHeight = height - margin;
      const position = { width, top: beginY, left: beginX };
      const container = bar.append('rect').attr('spanSegId', spanSegId).attr('x', -5).attr('y', beginY - 5)
        .attr('width', width + 10)
        .attr('height', rectHeight + 10)
        .attr('class', styles.backgroudHide)
        .on('mouseover', () => { this.selectTimeline(container, true); })
        .on('mouseout', () => { this.selectTimeline(container, false); })
        .on('click', () => { this.showSpanModal(node, position, container); });

      bar.append('rect').attr('x', beginX).attr('y', beginY).attr('width', rectWith + 0.1)
        .attr('height', rectHeight)
        .on('mouseover', () => { this.selectTimeline(container, true); })
        .on('mouseout', () => { this.selectTimeline(container, false); })
        .on('click', () => { this.showSpanModal(node, position, container); })
        .style('fill', colorMap[serviceCode]);

      bar.append('text')
        .attr('x', () => {
          if ((width - beginX) < (content.length * 5.2)) {
            return beginX - content.length * 5.2 - 45
          }
          return beginX + 8;
        })
        .attr('y', (index * height) + (height / 2) - 2)
        .attr('class', styles.rectText)
        .on('mouseover', () => { this.selectTimeline(container, true); })
        .on('mouseout', () => { this.selectTimeline(container, false); })
        .on('click', () => { this.showSpanModal(node, position, container); })
        .text(`${content} ${formatDuration(duration)}`);
      if (node.isError) {
        bar.append('svg:image')
          .attr('xlink:href', 'img/icon/error.png')
          .attr('x', width + (rectHeight / 2))
          .attr('y', beginY)
          .attr('width', rectHeight)
          .attr('height', rectHeight);
      }
      if (index > 0 && positionMap[parentSpanSegId]) {
        const parentX = positionMap[parentSpanSegId].x;
        const parentY = positionMap[parentSpanSegId].y;

        const defs = svgContainer.append('defs');
        const arrowMarker = defs.append('marker')
          .attr('id', 'arrow')
          .attr('markerUnits', 'strokeWidth')
          .attr('markerWidth', 5)
          .attr('markerHeight', 5)
          .attr('viewBox', '-5 -5 10 10')
          .attr('refX', 0)
          .attr('refY', 0)
          .attr('orient', 'auto');
        arrowMarker.append('path')
          .attr('d', 'M 0,0 m -5,-5 L 5,0 L -5,5 Z')
          .attr('fill', '#8543e0').attr('opacity', 0.8);

        const parentLeftBottomX = parentX;
        const parentLeftBottomY = (Number(parentY) + Number(height)) - Number(margin);
        const selfMiddleX = beginX;
        const selfMiddleY = beginY + ((height - margin) / 2);
        if ((beginX - parentX) < 10) {
          svgContainer.append('line').attr('x1', parentLeftBottomX - offX).attr('y1', parentLeftBottomY - offY).attr('class', styles.connlines)
            .on('mouseover', () => { this.selectTimeline(container, true); })
            .on('mouseout', () => { this.selectTimeline(container, false); })
            .on('click', () => { this.showSpanModal(node, position, container); })
            .attr('x2', parentLeftBottomX)
            .attr('y2', parentLeftBottomY - offY);

          svgContainer.append('line').attr('x1', parentLeftBottomX - offX).attr('y1', parentLeftBottomY - offY).attr('class', styles.connlines)
            .on('mouseover', () => { this.selectTimeline(container, true); })
            .on('mouseout', () => { this.selectTimeline(container, false); })
            .on('click', () => { this.showSpanModal(node, position, container); })
            .attr('x2', parentLeftBottomX - offX)
            .attr('y2', selfMiddleY);

          svgContainer.append('line').attr('x1', parentLeftBottomX - offX).attr('y1', selfMiddleY).attr('class', styles.connlines)
            .on('mouseover', () => { this.selectTimeline(container, true); })
            .on('mouseout', () => { this.selectTimeline(container, false); })
            .on('click', () => { this.showSpanModal(node, position, container); })
            .attr('x2', selfMiddleX - 5)
            .attr('y2', selfMiddleY)
            .attr('marker-end', 'url(#arrow)');
        } else {
          svgContainer.append('line').attr('x1', parentLeftBottomX).attr('y1', parentLeftBottomY).attr('class', styles.connlines)
            .on('mouseover', () => { this.selectTimeline(container, true); })
            .on('mouseout', () => { this.selectTimeline(container, false); })
            .on('click', () => { this.showSpanModal(node, position, container); })
            .attr('x2', parentLeftBottomX)
            .attr('y2', selfMiddleY);

          svgContainer.append('line').attr('x1', parentLeftBottomX).attr('y1', selfMiddleY).attr('class', styles.connlines)
            .on('mouseover', () => { this.selectTimeline(container, true); })
            .on('mouseout', () => { this.selectTimeline(container, false); })
            .on('click', () => { this.showSpanModal(node, position, container); })
            .attr('x2', selfMiddleX - 5)
            .attr('y2', selfMiddleY)
            .attr('marker-end', 'url(#arrow)');
        }
      }
    });
  }

  selectTimeline = (container, isOver) => {
    const {...stateData} = this.state;
    if (stateData.container === container) {
      return;
    }
    container.attr('class', isOver ? styles.backgroud : styles.backgroudHide);
  }

  showSpanModal = (span, position, container) => {
    const {...stateData} = this.state;
    const { container: old } = this.state;
    if (old) {
      old.attr('class', styles.backgroudHide);
    }
    container.attr('class', styles.backgroudSelected);
    this.setState({
      ...stateData,
      span,
      key: 'tags',
      position,
      container,
    });
  }

  hideSpanModal = () => {
    const { container: old } = this.state;
    const {...stateData} = this.state;
    if (old) {
      old.attr('class', styles.backgroudHide);
    }
    this.setState({
      ...stateData,
      span: {},
      container: undefined,
    });
  }

  resize = () => {
    const {...stateData} = this.state;
    if (!this.axis) {
      return;
    }
    this.setState({width:this.axis.parentNode.clientWidth - 50});
    if (!this.axis || stateData.width <= 0) {
      return;
    }
    this.axis.innerHTML = '';
    this.duration.innerHTML = '';
    this.drawAxis();
    this.displayData();
    this.setState({ ...stateData, span: {} });
  }

  renderTitle = (items) => {
    return (
      <Row type="flex" justify="start" gutter={15}>
        {
          items.map((_) => {
            return (
              <Col key={_.name}>
                <span>{_.name}</span>
                <Badge count={_.count} style={{ backgroundColor: '#1890FF', marginLeft: 5 }} />
              </Col>
            );
          })
        }
      </Row>
    );
  }

  render() {
    const { spans } = this.props;
    const { colorMap, span = {}, position = { width: 100, top: 0 } } = this.state;
    const legendButtons = Object.keys(colorMap).map(key =>
      (<Tag color={colorMap[key]} key={key}>{key}</Tag>));
    const tabList = [];
    const contentList = {};
    if (span.content) {

      tabList.push({
        key: 'tags',
        tab: 'Tags',
      });
      const base = [
        {
          title: 'span type',
          content: span.type,
        },
        {
          title: 'component',
          content: span.component,
        },
        {
          title: 'peer',
          content: span.peer,
        },
        {
          title: 'is error',
          content: `${span.isError}`,
        },
      ];
      const data = base.concat(span.tags.map(t => ({ title: t.key, content: t.value })));
      contentList.tags = (
        <DescriptionList layout="vertical">
          {data.map(_ =>
            <Description key={_.title} term={_.title}>{_.content}</Description>)}
        </DescriptionList>);
    }
    if (span.logs) {
      tabList.push({
        key: 'logs',
        tab: 'Logs',
      });
      contentList.logs = (
        <List
          itemLayout="horizontal"
          dataSource={span.logs}
          renderItem={log => (
            <List.Item>
              <List.Item.Meta
                size="small"
                title={moment(log.time).format('mm:ss.SSS')}
                description={
                  <DescriptionList layout="vertical" col={1}>
                    {log.data.map((_) => {
                      return (
                        <Description key={_.key} term={_.key}>
                          <pre className={styles.pre}>{_.value}</pre>
                        </Description>);
                    })}
                  </DescriptionList>
                }
              />
            </List.Item>
          )}
        />);
    }
    if (!span.parentSpanSegId && span.refs) {
      tabList.push({
        key: 'relatedTraces',
        tab: 'Related Trace',
      });
      contentList.relatedTraces = (
        <DescriptionList layout="vertical">
          {span.refs.map(_ => <Description key={_.type} term={_.type}>{_.traceId}</Description>)}
        </DescriptionList>);
    }
    const { top, left, width } = position;
    const {...stateData} = this.state;
    const toolTipStyle = { position: 'absolute', top: top + 86 };
    if (contentList.logs) {
      toolTipStyle.left = 0;
      toolTipStyle.width = width;
    } else {
      const right = width - left;
      if (left * 2 > width) {
        toolTipStyle.right = right;
        toolTipStyle.maxWidth = left;
      } else {
        toolTipStyle.left = left;
        toolTipStyle.maxWidth = right;
      }
    }
    return (
      <div className={styles.stack}>
        <div style={{ paddingBottom: 10 }}>
          <ButtonGroup>
            <Button type={stateData.treeMode ? "primary": ""} onClick={async () => {await this.setState({treeMode:true}); this.hideSpanModal();}}>TreeMode</Button>
            <Button type={stateData.treeMode ? "": "primary"} onClick={async () => {await this.setState({treeMode: false}); this.hideSpanModal();}}>ListMode</Button>
          </ButtonGroup>
        </div>

        <div style={{ paddingBottom: 10 }}>
          { legendButtons }
        </div>
        <div style={{display: stateData.treeMode?'none':'block'}} className={styles.duration} ref={(el) => { this.duration = el; }} />
        <div style={{display: stateData.treeMode?'none':'block'}} ref={(el) => { this.axis = el; }} />
        <div style={{display: stateData.treeMode?'block':'none'}}>
          <TraceTree showSpanModal={this.showSpanModal} data={spans} id="" />
        </div>
        {tabList.length > 0 ? (
          <Card
            type="inner"
            title={this.renderTitle([
              {
                name: 'Start Time',
                count: `${moment(span.startTime).format(timeFormat)}`,
              },
              {
                name: 'Duration',
                count: `${formatDuration(span.duration)}`,
              },
            ])}
            tabList={tabList}
            onTabChange={(key) => { this.onTabChange(key, 'key'); }}
            style={toolTipStyle}
            extra={<Button type="primary" shape="circle" icon="close" ghost onClick={this.hideSpanModal} />}
          >
            {contentList[stateData.key]}
          </Card>
        ) : null}
      </div>
    );
  }
}

export default TraceStack;
