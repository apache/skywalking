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
    int64_value: 1346
  }
  dimensions {
    key: "sourceService"
    value {
      string_value: "istio-policy"
    }
  }
  dimensions {
    key: "sourceUID"
    value {
      string_value: "kubernetes://istio-policy-7fbd997765-8p5sr.istio-system"
    }
  }
  dimensions {
    key: "requestPath"
    value {
      string_value: "/istio.mixer.v1.Mixer/Report"
    }
  }
  dimensions {
    key: "requestMethod"
    value {
      string_value: "POST"
    }
  }
  dimensions {
    key: "responseTime"
    value {
      timestamp_value {
        value {
          seconds: 1537415800
          nanos: 887532113
        }
      }
    }
  }
  dimensions {
    key: "apiProtocol"
    value {
      string_value: ""
    }
  }
  dimensions {
    key: "requestScheme"
    value {
      string_value: "http"
    }
  }
  dimensions {
    key: "requestTime"
    value {
      timestamp_value {
        value {
          seconds: 1537415800
          nanos: 882648986
        }
      }
    }
  }
  dimensions {
    key: "responseCode"
    value {
      int64_value: 200
    }
  }
  dimensions {
    key: "destinationService"
    value {
      string_value: "istio-telemetry"
    }
  }
  dimensions {
    key: "destinationUID"
    value {
      string_value: "kubernetes://istio-telemetry-796dbc5d46-bs9v4.istio-system"
    }
  }
  dimensions {
    key: "reporter"
    value {
      string_value: "destination"
    }
  }
  name: "swmetric.instance.istio-system"
}
dedup_id: "9307733128061296805"
