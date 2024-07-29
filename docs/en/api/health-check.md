# Check OAP healthiness

This is an http wrapper of the [health checker](../setup/backend/backend-health-check.md),
make sure to set necessary configurations required by the health checker before using this endpoint,
otherwise 404 will be returned.

> GET http://localhost:12800/healthcheck

When the OAP server is healthy, the request returns status 200, otherwise, the request returns status 503.
