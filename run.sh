#!/bin/sh

LIB_DIR=~/hyperic-sigar-1.6.4/sigar-bin/lib
TMP_DIR=target/swarm_tmp

if [ ! -d "$LIB_DIR" ]; then
  echo "Expected native libraries at $LIB_DIR. Exiting ..."
  exit
fi

if [ -d "$TMP_DIR" ]; then
  rm -rf $TMP_DIR
fi

echo "(Re)creating WildFly Swarm temp directory $TMP_DIR"	  
mkdir $TMP_DIR
      
java $JAVA_OPTS -Xms1g -Xmx1g -Djava.io.tmpdir=$TMP_DIR -Djava.library.path=$LIB_DIR -jar target/proc-mon-1.0-SNAPSHOT.jar "$@"


