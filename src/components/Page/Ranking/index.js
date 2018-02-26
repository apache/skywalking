import React, { PureComponent } from 'react';
import { List, Avatar } from 'antd';

export default class Ranking extends PureComponent {
  render() {
    let index = 0;
    const { data, title, content, unit } = this.props;
    return (
      <List
        size="small"
        itemLayout="horizontal"
        dataSource={data}
        renderItem={item => (
          <List.Item>
            <List.Item.Meta
              avatar={
                <Avatar
                  style={{ color: '#ff3333', backgroundColor: '#ffb84d' }}
                >
                  {(() => { index += 1; return index; })()}
                </Avatar>}
              title={item[title]}
              description={`${item[content]} ${unit}`}
            />
          </List.Item>
        )}
      />);
  }
}
