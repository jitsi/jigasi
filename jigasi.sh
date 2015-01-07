#!/bin/bash

kernel="$(uname -s)"
if [ $kernel == "Darwin" ] ; then
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
    architecture="macosx"
elif [ $kernel == "Linux" ] ; then
    SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
    architecture="linux"

    machine="$(uname -m)"
    if [ "$machine" == "x86_64" ] || [ "$machine" == "amd64" ] ; then
        architecture=$architecture"-64"
    fi
else
    echo "Unknown system : $kernel"
    exit 1
fi

mainClass="org.jitsi.jigasi.Main"
cp=$(JARS=($SCRIPT_DIR/jigasi.jar $SCRIPT_DIR/lib/*.jar); IFS=:; echo "${JARS[*]}")
libs="$SCRIPT_DIR/lib/native/$architecture"

java -Djava.library.path=$libs -Djava.util.logging.config.file=$SCRIPT_DIR/lib/logging.properties -cp $cp $mainClass $@
