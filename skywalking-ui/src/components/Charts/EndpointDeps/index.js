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
import { Sankey } from '..';

class EndpointDeps extends PureComponent {

  componentDidUpdate({ deps: preDeps }) {
    const { deps } = this.props;
    if (deps === preDeps) {
      return;
    }
    const { calls } = deps;
    if (calls.length < 1) {
      return;
    } 
    const { onLoadMetrics } = this.props;
    onLoadMetrics(deps);
  }

  edgeWith = (edge) => {
    const { metrics: { cpm: { values } } } = this.props;
    if (values.length < 1) {
      return 1;
    }
    const v = values.find(_ => _.id === edge.id);
    if (!v) {
      return 1;
    }
    return v.value;
  }

  render() {
    const { deps: { nodes, calls } } = this.props;
    if (nodes.length < 2) {
      return <span style={{ display: 'none' }} />;
    }
    const nodesMap = new Map();
    nodes.forEach((_, i) => {
      nodesMap.set(`${_.id}`, i);
    });
    const nData = {
      nodes,
      edges: calls
        .filter(_ => nodesMap.has(`${_.source}`) && nodesMap.has(`${_.target}`))
        .map(_ =>
          ({ ..._, value: (this.edgeWith(_) < 1 ? 1000 : this.edgeWith(_)), source: nodesMap.get(`${_.source}`), target: nodesMap.get(`${_.target}`) })),
    };
    return (
      <Sankey
        data={nData}
        edgeTooltip={['target*source*value', (target, source, value) => {
          return {
            name: `${source.name} to ${target.name} </span>`,
            value: `${value} cpm`,
          };
        }]}
        edgeColor="#bbb"
      />);
    }
}

export default EndpointDeps;