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


import React from 'react';
import { Card, Spin } from 'antd';
import classNames from 'classnames';

import styles from './index.less';

const ChartCard = ({
  loading = false, contentHeight, title, avatar, action, total, footer, children, ...rest
}) => {
  const content = (
    <div className={styles.chartCard}>
      <div
        className={classNames(styles.chartTop, { [styles.chartTopMargin]: (!children && !footer) })}
      >
        <div className={styles.avatar}>
          {
            avatar
          }
        </div>
        <div className={styles.metaWrap}>
          <div className={styles.meta}>
            <span className={styles.title}>{title}</span>
            <span className={styles.action}>{action}</span>
          </div>
          {
            // eslint-disable-next-line
            (total !== undefined) && (<div className={styles.total} dangerouslySetInnerHTML={{ __html: total }} />)
          }
        </div>
      </div>
      {
        children && (
          <div className={styles.content} style={{ height: contentHeight || 'auto' }}>
            <div className={contentHeight && styles.contentFixed}>
              {children}
            </div>
          </div>
        )
      }
      {
        footer && (
          <div className={classNames(styles.footer, { [styles.footerMargin]: !children })}>
            {footer}
          </div>
        )
      }
    </div>
  );

  return (
    <Card
      bodyStyle={{ padding: '20px 24px 8px 24px' }}
      {...rest}
    >
      {<Spin spinning={loading} wrapperClassName={styles.spin}>{content}</Spin>}
    </Card>
  );
};

export default ChartCard;
