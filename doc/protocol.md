## network-protocol
* 描述采集传输过程中的包结构

<table>
  <tr align="center">
    <td rowspan="3">包长度（4位）</td>
    <td colspan="7">正文</td>
    <td rowspan="3">校验和（4位）</td>
  </tr>
  <tr align="center">
    <td colspan="3">子数据包1</td>
    <td colspan="3">子数据包2</td>
    <td>…… (n)</td>
  </tr>
  <tr align="center">
    <td>子包长度（4位)</td>
    <td>子包类型（4位）</td>
    <td>子包正文</td>
    <td>子包长度（4位)</td>
    <td>子包类型（4位）</td>
    <td>子包正文</td>
    <td>…… (n)</td>
  </tr>
</table>

## buffer-file-protocol
* 描述collector-server使用本地缓存的文件结构

### 标准文件结构
<table>
  <tr align="center">
    <td rowspan="3">包长度（4位）</td>
    <td colspan="7">正文</td>
    <td rowspan="3">分隔符（4位）127,127,127,127</td>
  </tr>
  <tr align="center">
    <td colspan="3">子数据包1</td>
    <td colspan="3">子数据包2</td>
    <td>…… (n)</td>
  </tr>
  <tr align="center">
    <td>子包长度（4位)</td>
    <td>子包类型（4位）</td>
    <td>子包正文</td>
    <td>子包长度（4位)</td>
    <td>子包类型（4位）</td>
    <td>子包正文</td>
    <td>…… (n)</td>
  </tr>
</table>

### 文件结束标识性数据包
<table>
  <tr align="center">
    <td rowspan="3">包长度（4位）</td>
    <td colspan="3">正文</td>
    <td rowspan="3">分隔符（4位）127,127,127,127</td>
  </tr>
  <tr align="center">
    <td colspan="3">子数据包1</td>
  </tr>
  <tr align="center">
    <td>子包长度（4位)</td>
    <td>子包类型（4位）</td>
    <td>EOFSpan</td>
  </tr>
</table>

* 更为详细的结构，可以参考protocol.xlsx
