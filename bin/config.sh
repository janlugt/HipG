#!/bin/bash
#
# Creates classpath and properties for execution of 
# a HipG program.
# Not to be called by the user.
#
# Author: Ela Krepska
# Copyright: VU University Amsterdam 2010
#

DIR=$(pwd)

function findInDistro() {
	FILE=$1
	LOC=$(find . -name $FILE)
	if [ -z "$LOC" ]; then
		THISDIR=$(basename $DIR)
		if [ "$THISDIR" == "bin" ]; then
			LOC=$(find .. -name $FILE)
			if [ -z $LOC ]; then
				exit 1
			fi
		fi
	fi
	echo $LOC
}

LIBDIR=$(findInDistro lib)
EXTERNALDIR=$(findInDistro external)
MAKECLASSPATH=$(findInDistro classpath.sh)
CLASSPATH=$($MAKECLASSPATH $LIBDIR $EXTERNALDIR)
LOGPROPS=$(findInDistro log4j.properties)

export CLASSPATH=$CLASSPATH
export LOGPROPS=file:$LOGPROPS
export DEFAULTPORT=7878
export SERVER_JAVAOPTS="-Xmx256m"
export LOCALRUN_JAVAOPTS="-ea -Xmx2500m -Dhipg.messageBufSize=1048576 -Dhipg.synchronizerQueueChunkSize=16384 -Dhipg.synchronizerQueueInitialChunks=1"
export RUN_JAVAOPTS=""

