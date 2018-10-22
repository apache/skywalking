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
          nanos: 912063918
        }
      }
    }
  }
  dimensions {
    key: "requestPath"
    value {
      string_value: "/reviews/0"
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
      string_value: "kubernetes://reviews-v1-59cbdd7959-g69ll.default"
    }
  }
  dimensions {
    key: "requestTime"
    value {
      timestamp_value {
        value {
          seconds: 1537415799
          nanos: 903831844
        }
      }
    }
  }
  dimensions {
    key: "destinationService"
    value {
      string_value: "reviews-v1"
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
      string_value: "kubernetes://productpage-v1-8584c875d8-fq8wh.default"
    }
  }
  dimensions {
    key: "reporter"
    value {
      string_value: "source"
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
dedup_id: "9219286855927306075"
