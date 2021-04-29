# Exporter tool for profile raw data
When visualization doesn't work well on the official UI, users may submit issue reports. This tool helps users package the original profile data to assist the community in locating the issues in the users' cases. NOTE: This report includes the class name, method name, line number, etc. Before making your submission, please make sure that the security of your system wouldn't be compromised.

## Export using command line
1. Set the storage in the `tools/profile-exporter/application.yml` file based on your use case.
1. Prepare the data
    - Profile task ID: Profile task ID
    - Trace ID: Trace ID of the profile error
    - Export dir: Directory exported by the data
1. Enter the Skywalking root path
1. Execute shell command
    ```bash
   bash tools/profile-exporter/profile_exporter.sh --taskid={profileTaskId} --traceid={traceId} {exportDir}
   ```
1. The file `{traceId}.tar.gz` will be generated after executing shell.

## Exported data content
1. `basic.yml`: Contains the complete information of the profiled segments in the trace.
1. `snapshot.data`: All monitored thread snapshot data in the current segment. 

## Report profile issues
1. Provide exported data generated from this tool.
1. Provide the operation name and the mode of analysis (including/excluding child span) for the span.
1. Issue description. (It would be great if you could provide UI screenshots.)
