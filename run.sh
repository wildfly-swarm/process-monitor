#!/bin/sh

LIB_DIR=~/hyperic-sigar-1.6.4/sigar-bin/lib

if [ ! -d "$LIB_DIR" ]; then
  echo "Expected native libraries at $LIB_DIR. Exiting ..."
  exit
fi
  
java $JAVA_OPTS -Xms1g -Xmx1g -Djava.library.path=$LIB_DIR -jar target/proc-mon-1.0-SNAPSHOT.jar "$@"


