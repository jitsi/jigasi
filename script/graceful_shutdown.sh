#!/bin/bash
#
# 1. The script issues shutdown command to jigasi over REST API.
#    If HTTP status code other than 200 is returned then it exits with 1.
# 2. If the code is ok then it checks if jigasi has exited.
# 3. If not then it polls jigasi statistics until conference count drops to 0.
# 4. Gives some time for jigasi to shutdown. If it does not quit after that
#    time then it kills the process. If the process was successfully killed 0 is
#    returned and 1 otherwise.
#
#   Arguments:
#   "-p"(mandatory) the PID of jigasi process
#   "-h"("http://localhost:8788" by default) REST requests host URI part
#   "-t"("25" by default) number of second we we for jigadi to shutdown
#       gracefully after conference count drops to 0
#   "-s"(disabled by default) enable silent mode - no info output
#
#   NOTE: script depends on the tool jq, used to parse json
#

# Initialize arguments
hostUrl="http://localhost:8788"
timeout=25
verbose=1

# Parse arguments
OPTIND=1
while getopts "p:h:t:s" opt; do
    case "$opt" in
    p)
        pid=$OPTARG
        ;;
    h)
        hostUrl=$OPTARG
        ;;
    t)
        timeout=$OPTARG
        ;;
    s)
        verbose=0
        ;;
    esac
done
shift "$((OPTIND-1))"

# Try the pid file, if no pid was provided as an argument.
# for systemd we use different pid file in a subfolder
if [ "$pid" = "" ] ;then
    if [ -f /var/run/jigasi.pid ]; then
        pid=`cat /var/run/jigasi.pid`
    elif [ -f /var/run/jigasi/jigasi/pid ]; then
        pid=`cat /var/run/jigasi/jigasi.pid`
    else
        pid=`ps aux | grep jigasi.jar | grep -v grep | awk '{print $2}'`
    fi
fi

#Check if PID is a number
re='^[0-9]+$'
if ! [[ $pid =~ $re ]] ; then
   echo "error: PID is not a number" >&2; exit 1
fi

# Returns conference count by calling JVB REST statistics API and extracting
# conference count from JSON stats text returned.
function getConferenceCount {
    statsjson=$(curl -s "$hostUrl/about/stats")
#    echo $statsjson
    stats=$(echo $statsjson| jq '. ["conferences"]')
    echo $stats
}

# Prints info messages
function printInfo {
	if [ "$verbose" == "1" ]
	then
		echo "$@"
	fi
}

# Prints errors
function printError {
	echo "$@" 1>&2
}

shutdownStatus=`curl -s -o /dev/null -H "Content-Type: application/json" -d '{ "graceful-shutdown": "true" }' -w "%{http_code}" "$hostUrl/about/shutdown"`
if [ "$shutdownStatus" == "200" ]
then
	printInfo "Graceful shutdown started"
	confCount=`getConferenceCount`
	while [[ $confCount -gt 0 ]] ; do
		printInfo "There are still $confCount conferences"
		sleep 10
		confCount=`getConferenceCount`
	done

	sleep 5

	if ps -p $pid > /dev/null 2>&1
	then
		printInfo "It is still running, lets give it $timeout seconds"
		sleep $timeout
		if ps -p $pid > /dev/null 2>&1
		then
			printError "Jigasi did not exit after $timeout sec - killing $pid"
			kill $pid
		else
			printInfo "Jigasi shutdown OK"
			exit 0
		fi
	else
		printInfo "Jigasi shutdown OK"
		exit 0
	fi
	# check for 3 seconds if we managed to kill
	for I in 1 2 3
	do
		if ps -p $pid > /dev/null 2>&1
		then
			sleep 1
		fi
	done
	if ps -p $pid > /dev/null 2>&1
	then
		printError "Failed to kill $pid"
		printError "Sending force kill to $pid"
		kill -9 $pid
		if ps -p $pid > /dev/null 2>&1
		then
			printError "Failed to force kill $pid"
			exit 1
		fi
	fi
	rm -f /var/run/jigasi.pid
	rm -f /var/run/jigasi/jigasi.pid
	printInfo "Jigasi shutdown OK"
	exit 0
else
	printError "Invalid HTTP status for shutdown request: $shutdownStatus"
	exit 1
fi


