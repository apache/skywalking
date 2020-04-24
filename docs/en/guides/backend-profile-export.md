# Exporter tool of profile raw data
When the visualization doesn't work well through the official UI, users could submit the issue to report. This tool helps the users to package the original profile data for helping the community to locate the issue in the user case. NOTICE, this report includes the class name, method name, line number, etc. Before submit this, please make sure this wouldn't become your system vulnerability.

## Export command line Usage
1. Set the storage in `tools/profile-exporter/application.yml` file by following your use case.
1. Prepare data
    - Profile task id: Profile task id
    - Trace id: Wrong profiled trace id
    - Export dir: Directory of the data will export
1. Enter the Skywalking root path
1. Execute shell command
    ```bash
   bash tools/profile-exporter/profile_exporter.sh --taskid={profileTaskId} --traceid={traceId} {exportDir}
   ```
1. The file `{traceId}.tar.gz` will be generated after execution shell.

## Exported data content
1. `basic.yml`: Contains the complete information of the profiled segments in the trace.
1. `snapshot.data`: All monitored thread snapshot data in the current segment. 

## Report profile issue
1. Provide exported data generated from this tool.
1. Provide span operation name, analyze mode(include/exclude children).
1. Issue description. (If there have the UI screenshots, it's better)
