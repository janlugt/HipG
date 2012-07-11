#!/bin/bash
#
# Starts the Ibis server.
# Usage:
#  ./bin/server.sh <port>
#
# Author: Ela Krepska
# Copyright: VU University Amsterdam 2010
#

# Execute config
CONFIGSH=$(find . -name config.sh)
if [ -z $CONFIGSH ]; then
	echo "Config not found. Run ./bin/server.sh from the main dir."
	exit 1
fi
. $CONFIGSH
if [ $? != "0" ]; then
	echo "Config failed: $CONFIG"
	exit 1
fi
if [ $# == 0 ]; then
	echo "Port not specified, using default $DEFAULTPORT"
	PORT=$DEFAULTPORT
else
	PORT=$1
fi

# Execute server
java -classpath $CLASSPATH -Dlog4j.configuration=$LOGPROPS $SERVER_JAVAOPTS ibis.ipl.server.Server --port $PORT --events
