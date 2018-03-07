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
import { Avatar, List } from 'antd';
import classNames from 'classnames';
import styles from './NoticeList.less';

export default function NoticeList({
  data = [], onClick, onClear, title, locale, emptyText, emptyImage,
}) {
  if (data.length === 0) {
    return (
      <div className={styles.notFound}>
        {emptyImage ? (
          <img src={emptyImage} alt="not found" />
        ) : null}
        <div>{emptyText || locale.emptyText}</div>
      </div>
    );
  }
  return (
    <div>
      <List className={styles.list}>
        {data.map((item, i) => {
          const itemCls = classNames(styles.item, {
            [styles.read]: item.read,
          });
          return (
            <List.Item className={itemCls} key={item.key || i} onClick={() => onClick(item)}>
              <List.Item.Meta
                className={styles.meta}
                avatar={item.avatar ? <Avatar className={styles.avatar} src={item.avatar} /> : null}
                title={
                  <div className={styles.title}>
                    {item.title}
                    <div className={styles.extra}>{item.extra}</div>
                  </div>
                }
                description={
                  <div>
                    <div className={styles.description} title={item.description}>
                      {item.description}
                    </div>
                    <div className={styles.datetime}>{item.datetime}</div>
                  </div>
                }
              />
            </List.Item>
          );
        })}
      </List>
      <div className={styles.clear} onClick={onClear}>
        {locale.clear}{title}
      </div>
    </div>
  );
}
