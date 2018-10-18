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


/* eslint eqeqeq: 0 */
import React from 'react';

function computeHeight(node) {
  const totalHeight = parseInt(getComputedStyle(node).height, 10);
  const padding =
    parseInt(getComputedStyle(node).paddingTop, 10) +
    parseInt(getComputedStyle(node).paddingBottom, 10);
  return totalHeight - padding;
}

function getAutoHeight(n) {
  if (!n) {
    return 0;
  }

  let node = n;

  let height = computeHeight(node);

  while (!height) {
    node = node.parentNode;
    if (node) {
      height = computeHeight(node);
    } else {
      break;
    }
  }

  return height;
}

const autoHeight = () => (WrappedComponent) => {
  return class extends React.Component {
    state = {
      computedHeight: 0,
    };

    componentDidMount() {
      const { height } = this.props;
      if (!height) {
        const h = getAutoHeight(this.root);
        // eslint-disable-next-line
        this.setState({ computedHeight: h });
      }
    }

    handleRoot = (node) => {
      this.root = node;
    };

    render() {
      const { height } = this.props;
      const { computedHeight } = this.state;
      const h = height || computedHeight;
      return (
        <div ref={this.handleRoot}>{h > 0 && <WrappedComponent {...this.props} height={h} />}</div>
      );
    }
  };
};

export default autoHeight;
