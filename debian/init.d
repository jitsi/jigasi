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
if [ -f /etc/jitsi/videobridge/config ]; then
    . /etc/jitsi/videobridge/config
fi

PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin
DAEMON=/usr/share/jigasi/jigasi.sh
DAEMON_DIR=/usr/share/jigasi/
NAME=jigasi
USER=jigasi
PIDFILE=/var/run/jigasi.pid
LOGFILE=/var/log/jitsi/jigasi.log
DESC=jigasi
DAEMON_OPTS=" --host=localhost --domain=$JVB_HOSTNAME --subdomain=callcontrol --secret=$JIGASI_SECRET"

test -x $DAEMON || exit 0

set -e

killParentPid() {
    PPID=$(ps -o pid --no-headers --ppid $1 || true)
    if [ $PPID ]; then
        kill $PPID
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
        --exec /bin/bash -- -c "cd $DAEMON_DIR; exec $DAEMON $DAEMON_OPTS > $LOGFILE 2>&1"

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
