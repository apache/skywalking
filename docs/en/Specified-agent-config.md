# The Specified Agent Config File 

## Supported version

5.0.0-RC+

## What is the specified agent config file ?
In Default. The agent will try to locate `agent.config`, which should be in the `/config` dictionary of agent package. <br>
If User set the specified agent config file, The agent will try to load the specified agent config file.<br>
By the way, This function has no conflict with [Setting Override](Setting-override.md)

## Override priority
The specified agent config > The default agent config
 
## How to use
> The content formats of the specified config must be same as the default config. 


##### Using `System.Properties(-D)` to set the specified config path
 
 ```
 -Dsw.specified_config_path=/path/to/agent.config
 ```
 `/path/to/agent.config`  is the absolute path of the specified config file
 


  
