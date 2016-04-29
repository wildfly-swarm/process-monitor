# Measuring WildFly Swarm Startup Times

## Prerequisite

You need the WildFly Swarm examples. Make sure they a refully build.

## Building th testdriver

Build the top level project first:

```
mvn clean package
```


## Execute the tests

```
./run.sh <PATH_TO_EXAMPLES> target/perf.csv
```

This will take some time and if everything goes well,
create the test summary at `target/perf.csv`

## Interpreting the test results

The actual result file is a simple CSV document:

```
File,Name,Measurements,Min,Max,.75
/Users/hbraun/dev/prj/wfs/wildfly-swarm-examples/datasource/datasource-subsystem/target/example-datasource-subsystem-swarm.jar,example-datasource-subsystem-swarm.jar,10,4918.0,6544.0,6307.75
/Users/hbraun/dev/prj/wfs/wildfly-swarm-examples/jpa-jaxrs-cdi/jpa-jaxrs-cdi-war/target/example-jpa-jaxrs-cdi-war-swarm.jar,example-jpa-jaxrs-cdi-war-swarm.jar,10,8422.0,9735.0,9607.75
/Users/hbraun/dev/prj/wfs/wildfly-swarm-examples/jpa-jaxrs-cdi/jpa-jaxrs-cdi-shrinkwrap/target/example-jpa-jaxrs-cdi-shrinkwrap-swarm.jar,example-jpa-jaxrs-cdi-shrinkwrap-swarm.jar,10,8136.0,9403.0,9274.0
/Users/hbraun/dev/prj/wfs/wildfly-swarm-examples/jaxrs/jaxrs-cdi/target/example-jaxrs-cdi-swarm.jar,example-jaxrs-cdi-swarm.jar,10,6480.0,7739.0,7587.0
/Users/hbraun/dev/prj/wfs/wildfly-swarm-examples/servlet/servlet-cdi/target/example-servlet-cdi-swarm.jar,example-servlet-cdi-swarm.jar,10,6043.0,7479.0,7024.75
/Users/hbraun/dev/prj/wfs/wildfly-swarm-examples/messaging/messaging-mdb/target/example-messaging-mdb-swarm.jar,example-messaging-mdb-swarm.jar,10,8503.0,10393.0,9692.25
/Users/hbraun/dev/prj/wfs/wildfly-swarm-examples/jaxrs/jaxrs-war/target/example-jaxrs-war-swarm.jar,example-jaxrs-war-swarm.jar,10,4850.0,5886.0,5777.75
```

If vou put that into a spread however, it should be straightforward to compare performance baselines of 
the different WildFly Swarm releases:

<img src="https://raw.githubusercontent.com/heiko-braun/proc-mon/master/assets/graph.png"/>

Have fun.
