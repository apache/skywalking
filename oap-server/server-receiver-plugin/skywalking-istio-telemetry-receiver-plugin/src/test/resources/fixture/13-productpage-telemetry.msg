instances {
  value {
    int64_value: 904
  }
  dimensions {
    key: "responseTime"
    value {
      timestamp_value {
        value {
          seconds: 1537415800
          nanos: 917907619
        }
      }
    }
  }
  dimensions {
    key: "requestPath"
    value {
      string_value: "/istio.mixer.v1.Mixer/Report"
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
      string_value: "productpage-v1"
    }
  }
  dimensions {
    key: "destinationUID"
    value {
      string_value: "kubernetes://istio-telemetry-796dbc5d46-vx5dl.istio-system"
    }
  }
  dimensions {
    key: "requestTime"
    value {
      timestamp_value {
        value {
          seconds: 1537415800
          nanos: 914305682
        }
      }
    }
  }
  dimensions {
    key: "destinationService"
    value {
      string_value: "istio-telemetry"
    }
  }
  dimensions {
    key: "requestMethod"
    value {
      string_value: "POST"
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
      string_value: "kubernetes://productpage-v1-8584c875d8-fq8wh.default"
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
dedup_id: "9219286855927306078"
