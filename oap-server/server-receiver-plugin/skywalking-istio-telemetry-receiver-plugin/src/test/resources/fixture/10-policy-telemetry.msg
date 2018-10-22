instances {
  value {
    int64_value: 1321
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
      string_value: "kubernetes://istio-policy-7fbd997765-dt82j.istio-system"
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
          nanos: 892667623
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
          nanos: 888995363
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
dedup_id: "9307733128061296806"
