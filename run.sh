#!/bin/sh

LIB_DIR=./libs/ 
WORK_DIR=target/workdir

if [ ! -d "$LIB_DIR" ]; then
  echo "Expected native libraries at $LIB_DIR. Exiting ..."
  exit
fi

if [ -d "$WORK_DIR" ]; then
  rm -rf $WORK_DIR
fi

echo "(Re)creating WildFly Swarm work directory $WORK_DIR"
mkdir $WORK_DIR
      
java $JAVA_OPTS -Xms1g -Xmx1g -Djava.library.path=$LIB_DIR -jar target/process-monitor.jar -w "$WORK_DIR" "$@"


