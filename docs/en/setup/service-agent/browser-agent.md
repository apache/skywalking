## Browser Monitoring
[Apache SkyWalking Client JS](https://github.com/apache/skywalking-client-js) is a client-side JavaScript exception and tracing library.

It has these features:
- Provides metrics and error collection to SkyWalking backend.
- Lightweight. No browser plugin required. A simple JavaScript library.
- Browser serves as a starting point for the entire distributed tracing system.

See Client JS [official doc](https://github.com/apache/skywalking-client-js#quick-start) for more information.

Note: Make sure [`receiver-browser`](../backend/backend-receivers.md) is enabled. It is **ON** by default since version 8.2.0.
