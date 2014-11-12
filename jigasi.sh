#!/bin/bash

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

#echo $SCRIPT_DIR

mainClass="org.jitsi.jigasi.Main"
cp=$(JARS=($SCRIPT_DIR/jigasi.jar $SCRIPT_DIR/lib/*.jar); IFS=:; echo "${JARS[*]}")
libs="$SCRIPT_DIR/lib/native/linux"

java -Djava.library.path=$libs -Djava.util.logging.config.file=$SCRIPT_DIR/lib/logging.properties -cp $cp $mainClass $@
