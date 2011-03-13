#!/bin/sh

program=index.js
node_re="[n]ode.$program"
log=/var/log/celltrack.log
dtach_socket=/tmp/dtach.socket
srcdir=/home/allan/dev/celltrack

COLOR_BLUE='[0;34m'         COLOR_GREEN='[0;32m'
COLOR_LIGHT_GREEN='[1;32m'  COLOR_CYAN='[0;36m'
COLOR_LIGHT_CYAN='[1;36m'   COLOR_RED='[0;31m'
COLOR_LIGHT_RED='\033[1;31m'  COLOR_PURPLE='[0;35m'
COLOR_LIGHT_PURPLE='[1;35m' COLOR_BROWN='[0;33m'
COLOR_LIGHT_GRAY='[0;37m'   COLOR_YELLOW_ON_BLACK='[1;33;44m'
COLOR_YELLOW='[1;33m'       COLOR_GRAY='[0;30m' COLOR_NC='[0m'

ok()    { echo "$COLOR_GREEN✔ $* $COLOR_NC"; }
msg()   { echo "$COLOR_GRAY➔ $* $COLOR_NC"; }
warn()  { echo "$COLOR_YELLOW➜ $* $COLOR_NC"; }
fatal() { echo "$COLOR_RED✘ $* $COLOR_NC"; exit 1; }
is_running() { ps -ef | grep -q $node_re; }

case $1
in start)
  test -e $dtach_socket
  socket_exists=$?
  is_running
  node_running=$?

  case $socket_exists$node_running
  in 00) fatal node already running
  ;; 01) warn removing stale socket, then start again
         rm -f $dtach_socket && exec $0 start
  ;; 10) warn node running without dtach
         msg will shutdown and try again
         exec $0 restart
  ;; 11) msg pull from master repo
         cd $srcdir || fatal couldn\'t chdir
         git pull origin master
         dtach -n $dtach_socket \
           sh -c "/home/allan/local/node/bin/node $program|tee -a $log"
         is_running &&
         ok node running ||
         fatal start failed
  esac
;; stop)
  my_pid=`ps -ef|awk '/'$node_re'/{print $2}'`
  if test "$my_pid"
  then i=0
    kill $my_pid || fatal couldn\'t kill pid $my_pid
    while is_running
    do printf .
      sleep 1
      i=`expr $i + 1`
      test $i -gt 5 && kill -9 $my_pid
    done
    ok node stopped
  else warn node not running
  fi
;; restart) $0 stop && $0 start
;; *) printf "usage: `basename $0` start|stop|restart\n" && exit 1
esac
