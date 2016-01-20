#! /bin/sh
#
# INIT script for Jitsi Gateway for SIP
# Version: 1.0  01-Aug-2014  yasen@bluejimp.com
#
### BEGIN INIT INFO
# Provides:          jigasi
# Required-Start:    $local_fs
# Required-Stop:     $local_fs
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: Jitsi Gateway for SIP
# Description:       Gateway for Jitsi Meet to SIP
### END INIT INFO

# Include jigasi & videobridge defaults if available
if [ -f /etc/jitsi/jigasi/config ]; then
    . /etc/jitsi/jigasi/config
fi
# We have the same config in jvb and in jigasi config
# make sure we use the one from jigasi
JIGASI_JAVA_SYS_PROPS=${JAVA_SYS_PROPS}
if [ -f /etc/jitsi/videobridge/config ]; then
    . /etc/jitsi/videobridge/config
fi
JAVA_SYS_PROPS=${JIGASI_JAVA_SYS_PROPS}

PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin
DAEMON=/usr/share/jigasi/jigasi.sh
DAEMON_DIR=/usr/share/jigasi/
NAME=jigasi
USER=jigasi
PIDFILE=/var/run/jigasi.pid
LOGDIR=/var/log/jitsi
LOGFILE=$LOGDIR/jigasi.log
DESC=jigasi
if [ ! $JVB_HOST ]; then
    JVB_HOST=localhost
fi
DAEMON_OPTS=" --host=$JVB_HOST --domain=$JVB_HOSTNAME --subdomain=callcontrol --secret=$JIGASI_SECRET --logdir=$LOGDIR --configdir=/etc/jitsi --configdirname=jigasi $JIGASI_OPTS"

test -x $DAEMON || exit 0

set -e

killParentPid() {
    PARENT_PID=$(ps -o pid --no-headers --ppid $1 || true)
    if [ $PARENT_PID ]; then
        kill $PARENT_PID
    fi
}

stop() {
    if [ -f $PIDFILE ]; then
        PID=$(cat $PIDFILE)
    fi
    echo -n "Stopping $DESC: "
    if [ $PID ]; then
        killParentPid $PID
        rm $PIDFILE || true
        echo "$NAME stopped."
    elif [ $(ps -C jigasi.sh --no-headers -o pid) ]; then
        kill $(ps -o pid --no-headers --ppid $(ps -C jigasi.sh --no-headers -o pid)) || true
        rm $PIDFILE || true
        echo "$NAME stopped."
    else
        echo "$NAME doesn't seem to be running."
    fi
}
start() {
    if [ -f $PIDFILE ]; then
        echo "$DESC seems to be already running, we found pidfile $PIDFILE."
        exit 1
    fi
    echo -n "Starting $DESC: "

    start-stop-daemon --start --quiet --background --chuid $USER --make-pidfile --pidfile $PIDFILE \
        --exec /bin/bash -- -c "JAVA_SYS_PROPS=\"$JAVA_SYS_PROPS\" exec $DAEMON $DAEMON_OPTS > $LOGFILE 2>&1"

    echo "$NAME started."
}

reload() {
    echo 'Not yet implemented.'
}

status() {
    echo 'Not yet implemented.'
}

case "$1" in
  start)
    start
    ;;
  stop)
    stop
    ;;
  restart)
    stop
    start
    ;;
  reload)
    reload
    ;;
  status)
    status
    ;;
  *)
    N=/etc/init.d/$NAME
    echo "Usage: $N {start|stop|restart|reload|status}" >&2
    exit 1
    ;;
esac

exit 0
