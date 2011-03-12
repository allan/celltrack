#!/bin/sh
#set -e

cd $HOME/dev/android/celltrack-gh
dtach_socket=/tmp/dtach.socket
node_re="\[n\]ode"
log=/var/log/celltrack.log

COLOR_BLUE='[0;34m'         COLOR_GREEN='[0;32m'
COLOR_LIGHT_GREEN='[1;32m'  COLOR_CYAN='[0;36m'
COLOR_LIGHT_CYAN='[1;36m'   COLOR_RED='[0;31m'
COLOR_LIGHT_RED='\033[1;31m'  COLOR_PURPLE='[0;35m'
COLOR_LIGHT_PURPLE='[1;35m' COLOR_BROWN='[0;33m'
COLOR_LIGHT_GRAY='[0;37m'   COLOR_YELLOW_ON_BLACK='[1;33;44m'
COLOR_YELLOW='[1;33m'       COLOR_GRAY='[0;30m' COLOR_NC='[0m'

ok()    { echo "$COLOR_GREEN✔  $* $COLOR_NC"; }
msg()   { echo "$COLOR_GRAY➺  $* $COLOR_NC"; }
fatal() { echo "$COLOR_RED✘  $* $COLOR_NC"; }
is_running() { ps ax | grep -q $node_re; }

case $1
in start)
    test -e $dtach_socket
    socket_exists=$?
    is_running
    node_running=$?

    case $socket_exists$node_running
    in 00) fatal node already running
    ;; 01) fatal removing stale socket, then start again
           rm -f $dtach_socket && exec $0 start
    ;; 10) fatal node running without dtach,
           msg will shutdown and try again
           exec $0 restart
    ;; 11) msg "pull from master repo"
           git pull origin master
           dtach -n $dtach_socket \
             sh -c '/home/allan/local/node/bin/node .|tee -a $log 2>$log'
           sleep 0.5
           is_running &&
             ok node running ||
             fatal start failed &&
             exit 1
    esac
;; stop)
    is_running || { fatal node not running && exit 1; }
    killall node
    i=0
    while is_running
    do printf .
       i=`expr $i + 1`
       sleep 0.5
       test $i -gt 15 && {
         printf killing
         killall -9 node >/dev/null 2>&1
       }
    done
    test -e $dtach_socket && fatal $dtach_socket still exists || ok node stopped
;; restart) $0 stop; $0 start    # restart
;; *) printf "usage: `basename $0` start|stop|restart\n" && exit 1
      #awk '/^in|^;; +[^*]+\)/{sub(/\)/, "", $2); printf "  "$2"\t" }
      #     /^in|^;;/{sub(/.*#/,"",$0); print $0}
      #    ' $0
esac
