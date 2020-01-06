# RegistryInstanceOfApplicationNotFoundException

## Format 
RegistryInstanceOfApplicationNotFoundException<br/>
expected: Instances of Service(APPLICATION)<br/>
actual: NOT FOUND

## Cause
The `RegistryApplicationNotFoundException` caused by one of application code that you write in the expected data file 
cannot found in the actual data file.


## Check Points
1. Check the application code is the value of  `agent.service_name` that you configured.<br/>
e.g.,
the application that you write in the expected data file:  
```
registryItems:   {
  "test_application":  {
  }
}
```
the application of  `agent.service_name` that you configured: `-Dskywalking.agent.service_name=another_application`

2. Check the agent of someone project in the test case if it works.