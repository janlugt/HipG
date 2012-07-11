#!/bin/bash
#
# Kills all the threads using hipg.
# Only applicable to local processes (run-local).
#
# Author: Ela Krepska
# Copyright: VU University Amsterdam 2010
#

processes=$(jps -l | grep hipg)

if [ -z "$processes" ]; then
	echo "nothing to kill"
	exit 0
fi

pids=$(echo "$processes" | grep -i hipg | cut -d ' ' -f 1)

echo "killing: "
echo "$processes"
echo "last chance to stop"
sleep 1

for pid in $pids; do
	kill -9 $pid
done

echo "killed"
