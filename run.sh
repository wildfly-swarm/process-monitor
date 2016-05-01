#!/bin/sh

java -Xms1g -Xmx1g -Djava.library.path=/Users/hbraun/dev/env/hyperic-sigar-1.6.4/sigar-bin/lib/ -jar target/proc-mon-1.0-SNAPSHOT.jar $1 $2


