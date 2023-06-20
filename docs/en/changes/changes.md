## 9.6.0

#### Project

* Bump up Guava to 32.0.1 to avoid the lib listed as vulnerable due to CVE-2020-8908. This API is never used.

#### OAP Server

* Add Neo4j component ID(112) language: Python.
* Add Istio ServiceEntry registry to resolve unknown IPs in ALS.
* Improve Kubernetes coordinator to only select ready OAP Pods to build cluster.
* [Breaking change] Remove `matchedCounter` from `HttpUriRecognitionService#feedRawData`.

#### UI


#### Documentation


All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/181?closed=1)
