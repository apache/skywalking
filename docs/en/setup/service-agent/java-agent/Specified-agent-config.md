# Locate agent config file by system property

## Supported version

5.0.0-RC+

## What is Locate agent config file by system property ？
In Default. The agent will try to locate `agent.config`, which should be in the `/config` dictionary of agent package.
If User sets the specified agent config file through system properties, The agent will try to load file from there.
By the way, This function has no conflict with [Setting Override](Setting-override.md)

## Override priority
The specified agent config > The default agent config
 
## How to use

The content formats of the specified config must be same as the default config. 


**Using `System.Properties(-D)` to set the specified config path**
 
 ```
 -Dskywalking_config=/path/to/agent.config
 ```
 `/path/to/agent.config`  is the absolute path of the specified config file
 


  
