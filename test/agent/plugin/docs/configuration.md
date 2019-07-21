# Configuration file

```yaml
type: [ tomcat | java ]
entryService: the entry service of test scenario
framework: the test framework
version: the version of testing framework
startScript: start project script
dependencies:
  mysql:
    image: xxxx
    host: xxxx
    environments:
    - testA=testB
    - testB=testB
  testB:
    image: xxxx
    host: xxxx
    environments:
    - testB=testC
    - testC=testD
```

## type

type:  tomcat   java

## entryService

## framework

## version

## startScript (optional)
相对于这个测试用例的目录

## dependencies (optional)

### image

### host

### environment