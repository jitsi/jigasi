#!/bin/bash

kernel="$(uname -s)"
if [ $kernel == "Darwin" ] ; then
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
elif [ $kernel == "Linux" ] ; then
    SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
else
    echo "Unknown system : $kernel"
    exit 1
fi

mainClass="org.jitsi.jigasi.Main"
cp=$(JARS=($SCRIPT_DIR/jigasi.jar $SCRIPT_DIR/lib/*.jar); IFS=:; echo "${JARS[*]}")
libs="$SCRIPT_DIR/lib"
logging_config="$SCRIPT_DIR/lib/logging.properties"

# if there is a logging config file in lib folder use it (running from source)
if [ -f $logging_config ]; then
    LOGGING_CONFIG_PARAM="-Djava.util.logging.config.file=$logging_config"
fi

if [ -z "$JIGASI_MAX_MEMORY" ]; then JIGASI_MAX_MEMORY=3072m; fi

LD_LIBRARY_PATH=$libs exec java -Xmx$JIGASI_MAX_MEMORY -Djdk.net.usePlainDatagramSocketImpl=true -Djava.library.path=$libs $LOGGING_CONFIG_PARAM $JAVA_SYS_PROPS -cp $cp $mainClass $@
