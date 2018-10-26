# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

instances {
  value {
    int64_value: 0
  }
  dimensions {
    key: "responseTime"
    value {
      timestamp_value {
        value {
          seconds: 1537415799
          nanos: 915154754
        }
      }
    }
  }
  dimensions {
    key: "requestPath"
    value {
      string_value: "/productpage"
    }
  }
  dimensions {
    key: "responseCode"
    value {
      int64_value: 200
    }
  }
  dimensions {
    key: "sourceService"
    value {
      string_value: "istio-ingressgateway"
    }
  }
  dimensions {
    key: "destinationUID"
    value {
      string_value: "kubernetes://productpage-v1-8584c875d8-fq8wh.default"
    }
  }
  dimensions {
    key: "requestTime"
    value {
      timestamp_value {
        value {
          seconds: 1537415799
          nanos: 884254913
        }
      }
    }
  }
  dimensions {
    key: "destinationService"
    value {
      string_value: "productpage-v1"
    }
  }
  dimensions {
    key: "requestMethod"
    value {
      string_value: "GET"
    }
  }
  dimensions {
    key: "apiProtocol"
    value {
      string_value: ""
    }
  }
  dimensions {
    key: "sourceUID"
    value {
      string_value: "kubernetes://istio-ingressgateway-6f58fdc8d7-m29dq.istio-system"
    }
  }
  dimensions {
    key: "reporter"
    value {
      string_value: "destination"
    }
  }
  dimensions {
    key: "requestScheme"
    value {
      string_value: "http"
    }
  }
  name: "swmetric.instance.istio-system"
}
dedup_id: "9219286855927306076"
