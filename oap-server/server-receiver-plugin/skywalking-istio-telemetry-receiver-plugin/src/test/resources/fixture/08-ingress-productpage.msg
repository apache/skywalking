instances {
  value {
    int64_value: 0
  }
  dimensions {
    key: "sourceService"
    value {
      string_value: "istio-ingressgateway"
    }
  }
  dimensions {
    key: "sourceUID"
    value {
      string_value: "kubernetes://istio-ingressgateway-6f58fdc8d7-m29dq.istio-system"
    }
  }
  dimensions {
    key: "requestPath"
    value {
      string_value: "/productpage"
    }
  }
  dimensions {
    key: "requestMethod"
    value {
      string_value: "GET"
    }
  }
  dimensions {
    key: "responseTime"
    value {
      timestamp_value {
        value {
          seconds: 1537415799
          nanos: 915765768
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
          seconds: 1537415799
          nanos: 881503925
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
      string_value: "productpage-v1"
    }
  }
  dimensions {
    key: "destinationUID"
    value {
      string_value: "kubernetes://productpage-v1-8584c875d8-fq8wh.default"
    }
  }
  dimensions {
    key: "reporter"
    value {
      string_value: "source"
    }
  }
  name: "swmetric.instance.istio-system"
}
dedup_id: "9307733128061296804"
