#!/bin/bash

# script that creates an archive in current folder
# containing the heap and thread dump and the current log file

JAVA_HEAPDUMP_PATH="/tmp/java_*.hprof"
STAMP=`date +%Y-%m-%d-%H%M`
PID_PATH="/var/run/jigasi.pid"
JIGASI_USER="jigasi"
JIGASI_UID=`id -u $JIGASI_USER`
RUNNING=""
unset PID

#Find any crashes in /var/crash from our user in the past 20 minutes, if they exist
CRASH_FILES=$(find /var/crash -name '*.crash' -uid $JIGASI_UID -mmin -20 -type f)

# for systemd we use different pid file
if [ ! -f $PID_PATH ]; then
    PID_PATH="/var/run/jigasi/jigasi.pid"
fi

[ -e $PID_PATH ] && PID=$(cat $PID_PATH)
if [ ! -z $PID ]; then
   ps -p $PID | grep -q java
   [ $? -eq 0 ] && RUNNING="true"
fi
if [ ! -z $RUNNING ]; then
    echo "Jigasi pid $PID"
    THREADS_FILE="/tmp/stack-${STAMP}-${PID}.threads"
    HEAP_FILE="/tmp/heap-${STAMP}-${PID}.bin"
    sudo -u $JIGASI_USER jstack ${PID} > ${THREADS_FILE}
    sudo -u $JIGASI_USER jmap -dump:live,format=b,file=${HEAP_FILE} ${PID}
    tar zcvf jigasi-dumps-${STAMP}-${PID}.tgz ${THREADS_FILE} ${HEAP_FILE} ${CRASH_FILES} /var/log/jitsi/jigasi.log /tmp/hs_err_*
    rm ${HEAP_FILE} ${THREADS_FILE}
else
    ls $JAVA_HEAPDUMP_PATH >/dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo "Jigasi not running, but previous heap dump found."
        tar zcvf jigasi-dumps-${STAMP}-crash.tgz $JAVA_HEAPDUMP_PATH ${CRASH_FILES} /var/log/jitsi/jigasi.log /tmp/hs_err_*
        rm ${JAVA_HEAPDUMP_PATH}
    else
        echo "Jigasi not running, no previous dump found. Including logs only."
        tar zcvf jigasi-dumps-${STAMP}-crash.tgz ${CRASH_FILES} /var/log/jitsi/jigasi.log /tmp/hs_err_*
    fi
fi
