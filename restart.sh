#!/bin/sh
set -e

COLOR_NC='[0m' # No Color
COLOR_BLUE='[0;34m'
COLOR_GREEN='[0;32m'
COLOR_LIGHT_GREEN='[1;32m'
COLOR_CYAN='[0;36m'
COLOR_LIGHT_CYAN='[1;36m'
COLOR_RED='[0;31m'
COLOR_LIGHT_RED='\033[1;31m'
COLOR_PURPLE='[0;35m'
COLOR_LIGHT_PURPLE='[1;35m'
COLOR_BROWN='[0;33m'
COLOR_YELLOW='[1;33m'
COLOR_GRAY='[0;30m'
COLOR_LIGHT_GRAY='[0;37m'
COLOR_YELLOW_ON_BLACK='[1;33;44m'

ok()    { echo "$COLOR_GREEN✔  $* $COLOR_NC"; }
msg()   { echo "$COLOR_GRAY➺  $* $COLOR_NC"; }
fatal() { echo "$COLOR_RED✘  $* $COLOR_NC"; }

cd /home/allan/dev/celltrack

msg "pull from master repo"
git pull origin master

ps aux |
  grep -q [n]ode &&
  msg "killing node instance" &&
  killall node
sleep 0.2

ps aux |
  grep -q [n]ode &&
  { fatal "node still running.  killing -9"
    killall -9 node; }
sleep 0.1

msg "restarting node instance"
dtach -n /tmp/dtach.socket \
  sh -c '/home/allan/local/node/bin/node .|tee -a /var/log/celltrack.log'
sleep 1
ps aux |
  grep -q [n]ode &&
  ok "finished" &&
  exit

fatal "failed, no node instance running"

