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

. /lib/lsb/init-functions

# Include jigasi defaults if available
if [ -f /etc/jitsi/jigasi/config ]; then
    . /etc/jitsi/jigasi/config
fi

PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin
DAEMON=/usr/share/jigasi/jigasi.sh
DAEMON_DIR=/usr/share/jigasi/
NAME=jigasi
USER=jigasi
PIDFILE=/var/run/jigasi.pid
LOGDIR=/var/log/jitsi
LOGFILE=$LOGDIR/jigasi.log
DESC=jigasi
DAEMON_OPTS=" --host=$JIGASI_HOST --domain=$JIGASI_HOSTNAME --logdir=$LOGDIR --configdir=/etc/jitsi --configdirname=jigasi $JIGASI_OPTS"

if [ ! -x $DAEMON ] ;then
  echo "Daemon not executable: $DAEMON"
  exit 1
fi

set -e

stop() {
    if [ -f $PIDFILE ]; then
        PID=$(cat $PIDFILE)
    fi
    echo -n "Stopping $DESC: "
    if [ $PID ]; then
        kill $PID || true
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
    status_of_proc -p $PIDFILE java "$NAME" && exit 0 || exit $?
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
