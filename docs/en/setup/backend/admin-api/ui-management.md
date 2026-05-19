# UI Management API

REST surface for dashboard templates, hosted on the admin-server REST port
(default `17128`). The official
[Horizon UI](https://github.com/apache/skywalking-horizon-ui) consumes these
endpoints to populate the dashboard library, and operators hit them
directly with `curl` or `swctl` to seed / customize / disable templates.

The sidebar menu is **not** served from OAP — Horizon UI ships its own
menu config in its bundle and uses `listServices(layer:...)` on the
metadata query surface for dynamic "layer has services" gating.

## Hosting

Routes mount on admin-server's REST register only. There is no public-REST
mirror — write operations are operator-only and gated behind the admin
gateway, same posture as `runtime-rule` and `dsl-debugging`. Admin-server
ships **enabled by default**; set `SW_UI_MANAGEMENT=` empty to close the
UI surface without closing the rest of admin-server.

**Template writes (`POST` / `PUT` / `POST /disable`) are enabled by default.**
The REST endpoints accept writes unconditionally when the module is
loaded; the admin host's gateway / IP allow-list is the auth boundary.
Operators who want to lock writes down disable the entire module
(`SW_UI_MANAGEMENT=`).

```yaml
ui-management:
  selector: ${SW_UI_MANAGEMENT:default}
  default:
```

## Endpoints

### `GET /ui-management/templates`

List all templates. Optional `includingDisabled=true` returns soft-disabled
templates too; default skips them.

```bash
curl -s "http://oap:17128/ui-management/templates" | jq
```

Response: JSON array of template objects:

```json
[
  {
    "id": "8b2f0eb1-...",
    "configuration": "{\"name\":\"General-Service\",...}",
    "disabled": false
  }
]
```

### `GET /ui-management/templates/{id}`

Fetch a single template by ID. Returns `404 not_found` when the ID is
unknown.

```bash
curl -s "http://oap:17128/ui-management/templates/8b2f0eb1-..." | jq
```

### `POST /ui-management/templates`

Add a new template. Body must include `configuration` (a JSON-encoded
template definition); the server allocates the ID.

```bash
curl -s -X POST "http://oap:17128/ui-management/templates" \
  -H 'Content-Type: application/json' \
  -d '{"configuration":"{\"name\":\"my-dashboard\",...}"}'
```

Response body is a `TemplateChangeStatus`:

```json
{
  "id": "<server-generated UUID>",
  "status": true,
  "message": null
}
```

HTTP `200 OK` on success; `409 Conflict` when the service layer rejects the
write (e.g., name collision) — the body still carries the same shape.

### `PUT /ui-management/templates`

Update an existing template. Body must include both `id` and the new
`configuration`.

```bash
curl -s -X PUT "http://oap:17128/ui-management/templates" \
  -H 'Content-Type: application/json' \
  -d '{"id":"<existing-uuid>","configuration":"{\"name\":\"...\",...}"}'
```

### `POST /ui-management/templates/{id}/disable`

Soft-disable a template. Idempotent — disabling an already-disabled
template returns success. The template's row is preserved; only the
`disabled` field flips.

```bash
curl -s -X POST "http://oap:17128/ui-management/templates/<uuid>/disable"
```

## Configuration

| Key | Default | Purpose |
|---|---|---|
| `SW_UI_MANAGEMENT` | `default` | Selector. Set empty to disable the UI management surface. |
| `SW_ADMIN_SERVER` | `default` | Underlying admin host. Must be on for `ui-management` to reach its handlers. |

## Security

UI Management writes (`POST`, `PUT`, `POST /disable`) are equivalent to
mutating the OAP backend's dashboard library — operationally sensitive.
Gateway-protect the admin port per the
[admin-server security notice](readme.md#-security-notice) and never
expose it to the public internet.
