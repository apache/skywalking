Apache SkyWalking
==========

<img src="http://skywalking.apache.org/assets/logo.svg" alt="Sky Walking logo" height="90px" align="right" />

**SkyWalking**: an APM(application performance monitor) system, especially designed for
microservices, cloud native and container-based (Docker, Kubernetes, Mesos) architectures.

[![GitHub stars](https://img.shields.io/github/stars/apache/skywalking.svg?style=for-the-badge&label=Stars&logo=github)](https://github.com/apache/skywalking)
[![Twitter Follow](https://img.shields.io/twitter/follow/asfskywalking.svg?style=for-the-badge&label=Follow&logo=twitter)](https://twitter.com/AsfSkyWalking)

[![Maven Central](https://img.shields.io/maven-central/v/org.apache.skywalking/apache-skywalking-apm.svg)](http://skywalking.apache.org/downloads/)

# Abstract
**SkyWalking** is an open source APM system, including monitoring, tracing, diagnosing capabilities for distributed system
in Cloud Native architecture.
The core features are following.

- Service, service instance, endpoint metrics analysis
- Root cause analysis
- Service topology map analysis
- Service, service instance and endpoint dependency analysis
- Slow services and endpoints detected
- Performance optimization
- Distributed tracing and context propagation
- Database access metrics. Detect slow database access statements(including SQL statements).
- Alarm


<img src="http://skywalking.apache.org/assets/frame.jpeg?u=20190518"/>

SkyWalking supports to collect telemetry (traces and metrics) data from multiple sources
and multiple formats,
including
1. Java, [.NET Core](https://github.com/SkyAPM/SkyAPM-dotnet), [NodeJS](https://github.com/SkyAPM/SkyAPM-nodejs) and [PHP](https://github.com/SkyAPM/SkyAPM-php-sdk) auto-instrument agents in SkyWalking format
1. Manual-instrument [Go agent](https://github.com/tetratelabs/go2sky) in SkyWalking format.
1. Istio telemetry format
1. Envoy gRPC Access Log Service (ALS) format in Istio controlled service mesh
1. Envoy Metrics Service format.
1. Zipkin v1/v2 format.
1. Jaeger gRPC format.


# Document
- [6.x Documents](docs/README.md).


# Downloads
Please head to the [releases page](http://skywalking.apache.org/downloads/) to download a release of Apache SkyWalking.


# Code of conduct
This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.
Please follow the [REPORTING GUIDELINES](CODE_OF_CONDUCT.md#reporting-guidelines) to report unacceptable behavior.

# Live Demo
Host in Beijing. Go to [demo](http://122.112.182.72:8080).

**Video on youtube.com**

[![RocketBot UI](http://img.youtube.com/vi/JC-Anlshqx8/0.jpg)](http://www.youtube.com/watch?v=JC-Anlshqx8)


# Screenshot
<table>
  <tr>
    <td width="100%" align="center" colspan="2"><b>Dashboard</b></td>
  </tr>
  <tr>
    <td><img src="http://skywalking.apache.org/screenshots/6.1.0/dashboard-1.png"/></td>
    <td><img src="http://skywalking.apache.org/screenshots/6.1.0/dashboard-2.png"/></td>
  </tr>
  <tr>
      <td width="50%" align="center"><b>Topology Map</b></td>
      <td width="50%" align="center"><b>Trace</b></td>
  </tr>
  <tr>
     <td><img src="http://skywalking.apache.org/screenshots/6.1.0/topology.png"/></td>
     <td><img src="http://skywalking.apache.org/screenshots/6.1.0/trace.png"/></td>
  </tr>
</table>

# Compiling project
Follow this [document](docs/en/guides/How-to-build.md).

# Contact Us
* Submit an [issue](https://github.com/apache/skywalking/issues)
* Mail list: **dev@skywalking.apache.org**. Mail to `dev-subscribe@skywalking.apache.org`, follow the reply to subscribe the mail list.
* Join `#skywalking` channel at [Apache Slack](https://join.slack.com/t/the-asf/shared_invite/enQtNzc2ODE3MjI1MDk1LTAyZGJmNTg1NWZhNmVmOWZjMjA2MGUyOGY4MjE5ZGUwOTQxY2Q3MDBmNTM5YTllNGU4M2QyMzQ4M2U4ZjQ5YmY). If the linke is not working, find the latest one at [Apache INFRA WIKI](https://cwiki.apache.org/confluence/display/INFRA/Slack+Guest+Invites).
* QQ Group: 392443393(2000/2000, not available), 901167865(available)

# Who Uses SkyWalking?
Hundreds of companies and organizations use SkyWalking for research, production, and commercial product, including
1. Alibaba Cloud
1. China Eastern Airlines
1. China Merchants Bank
1. DaoCloud
1. GOME
1. guazi.com
1. Huawei
1. ke.com
1. lizhi.fm
1. NetEase
1. Peking University
1. tetrate.io
1. WeBank
1. Xiaomi
1. Yonghui Superstores Co., Ltd
1. zhaopin.com

The [PoweredBy](docs/powered-by.md) page includes more users of the project.
Users are encouraged to add themselves to there.

# Landscapes

<p align="center">
<br/><br/>
<img src="https://landscape.cncf.io/images/left-logo.svg" width="150"/>&nbsp;&nbsp;<img src="https://landscape.cncf.io/images/right-logo.svg" width="200"/>
<br/><br/>
SkyWalking enriches the <a href="https://landscape.cncf.io/landscape=observability-and-analysis&license=apache-license-2-0">CNCF CLOUD NATIVE Landscape.

</p>

<p align="center">
<a href="https://openapm.io"><img src="https://openapm.io/static/media/openapm_logo.svg" width="100"/></a>
  <br/>Our project enriches the <a href="https://openapm.io">OpenAPM Landscape!</a>
</p>

# License
[Apache 2.0 License.](/LICENSE)
