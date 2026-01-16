# Compatibility

SkyWalking uses v3 protocols. Agents don't have to keep the identical versions as the OAP backend.

**We recommend always using the latest releases of both OAP server and agents for better performance and advanced
features.**

## SkyWalking Native Agents

### OAP 10.x (Current)

**Agents with specific version requirements:**

| Agent     | Minimum Version |
|-----------|-----------------|
| Java      | 8.x, 9.x        |
| Go        | 0.1+            |
| Rover     | 0.5+            |
| Satellite | 1.2+            |

**Agents compatible with all versions:**
Python, NodeJS, PHP, Rust, Ruby, Browser, LUA, Kong

### Legacy Versions (8.x - 9.x)

For users on OAP 8.x or 9.x, refer to the table below. Note: these versions are no longer actively maintained.

| OAP Server Version | Java      | Python | NodeJS | Go   | Rover | Satellite | Ruby |
|--------------------|-----------|--------|--------|------|-------|-----------|------|
| 9.5 - 9.7          | 8.x, 9.x  | All    | All    | 0.1+ | 0.5+  | 1.2+      | 0.1+ |
| 9.1 - 9.4          | 8.x       | All    | All    | No   | 0.1+  | 1.0+      | No   |
| 9.0                | 8.x       | All    | All    | No   | No    | 0.4+      | No   |
| 8.4 - 8.9          | 8.x       | All    | All    | No   | No    | No        | No   |
| 8.0 - 8.3          | 8.0 - 8.3 | 0.6-   | 0.3-   | No   | No    | No        | No   |

## Ecosystem Agents

The following agent implementations are part of the SkyWalking ecosystem. Their source codes and distributions are
maintained by their respective communities and don't belong to the Apache Software Foundation.

| OAP Server Version | DotNet    | cpp2sky        |
|--------------------|-----------|----------------|
| 10.x               | 1.0+      | All            |
| 9.x                | 1.0+      | All            |
| 8.4+               | 1.0+      | All            |
| 8.0 - 8.3          | 1.0 - 1.3 | 0.2 or earlier |

Please reach out to their respective communities if you face any compatibility issues.

___
The compatibility information above is for reference. If you encounter an `unimplemented` error, upgrade the OAP backend
to support newer features in the agents.
