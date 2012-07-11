#!/bin/bash
#
# Executes a HipG program locally. It assumes the
# server executes also locally. The program exits when all
# threads exit. Usage is printed when run with no params.
#
# Author: Ela Krepska
# Copyright: VU University Amsterdam 2010
#

# Execute config
CONFIGSH=$(find . -name config.sh)
if [ -z "$CONFIGSH" ]; then
	echo "Config not found. Run ./bin/server.sh from the main dir."
	exit 1
fi
. $CONFIGSH
if [ $? != "0" ]; then
	echo "Config failed: $CONFIG"
	exit 1
fi

# Validate arguments
if [ "$#" -lt 2 ]; then
	echo "Usage: $0 [ -port <port> ] [ -pool <pool> ] <threads> <program>"
        echo "  <threads> = number of threads to execute concurrently"
	echo "  <port> = port on which the server executes, default $DEFAULTPORT"
        echo "  <pool> = a unique name for this execution, default based on current date"
	exit 1
fi
NPROC=
POOL=
PORT=
last=
for x in $*; do
	if [ "$last" == "-port" ]; then
		PORT=$x
		last=
	elif [ "$last" == "-pool" ]; then
		POOL=$x
		last=
	elif ( [ $x == "-port" ] || [ $x == "-pool" ] ); then
		last=$x
	elif [ -z "$NPROC" ]; then
		NPROC=$x
	else 
		ARGS="$ARGS $x"
	fi
done
if [ -z $PORT ]; then
	PORT=$DEFAULTPORT
fi
if [ -z $POOL ]; then
	POOL=pool-$(echo $ARGS | cut -d ' ' -f 1)-$(date +%Y%m%d%H%M%S)-$(date +%N)
fi

OPT=

# ---------------------------------------
# Configure garbage collection debugging
# ---------------------------------------

# (Print when minor/major collections occurr)
#OPT="$OPT -XX:+PrintGC"
# (Print more details of collections)
#OPT="$OPT -XX:+PrintGCDetails" 
# (Print timestamp for each collection)
# OPT="$OPT -XX:+PrintGCTimeStamps"
# (Print information about promoting objects to tenured generation)
# OPT="$OPT -XX:+PrintTenuringDistribution"

# ---------------------------------------------
# Configure garbage collection generation sizes
# ---------------------------------------------

# (Ratio of tenured to young generation size)
OPT="$OPT -XX:NewRatio=20"
# (Min/Max size of the young generation (finer tuning than NewRatio))
#OPT="$OPT -XX:NewSize=100m -XX:MaxNewSize=100m"


# ---------------------------------------------
# Select and configure garbage collector
# ---------------------------------------------

# (Enable generational GC -> less collections, but up to 10% slower,
#  will use the ConcMarkAndSweep collector)
# OPT="$OPT -Xincgc"
# (Use serial collector)
OPT="$OPT -XX:+UseSerialGC"
# (Use throughtput collector (=parallel scavenge collector) in which 
#  multiple threads do minor collections and major collections are 
#  serial; requirements: big enough young generation, multicore)
#OPT="$OPT -XX:+UseParallelGC"
#  option: number of threads participating in minor collections
#OPT="$OPT -XX:ParallelGCThreads=2"
# (Use concurrent low pause collector; good typically when tenured
#  generation is large)
#OPT="$OPT -XX:+UseConcMarkSweepGC"
#   option: incremental mode (major collections broken into smaller
#     pieces of work, scheduled inbetween minor collections)
#OPT="$OPT -XX:+CMSIncrementalMode"
#   option: duty cycle in the incremental model adjusted automatically
#OPT="$OPT -XX:+CMSIncrementalPacing"
#   option: explicit duty cycle (initial when pacing enabled) in percentage
#     of time it can run beteen minor collections
#OPT="$OPT -XX:CMSIncrementalDutyCycle=10"
#   option: minimum length (percentage of time between minor collections)
#     of the duty cycle
#OPT="$OPT -XX:CMSIncrementalDutyCycleMin=0"
# OPT="$OPT -XX:+UseCMSInitiatingOccupancyOnly"
# OPT="$OPT -XX:+CMSClassUnloadingEnabled"
# OPT="$OPT -XX:+CMSScavengeBeforeRemark"
# OPT="$OPT -XX:CMSInitiatingOccupancyFraction=68"

# ----------------------------
# Configure other JVM options
# ----------------------------

# (Server(="big") application)
OPT="$OPT -server"
# (Use large pages)
# OPT="$OPT -XX:+UseLargePages" 
# OPT="$OPT -XX:LargePageSizeInBytes=256m"
# (Trace class loading and unloading)
# OPT="$OPT -XX:+TraceClassLoading -XX:+TraceClassUnloading"
# (Compress 64-pointers)
# -XX:+UseCompressedOops
# (Aggressive experimental options!)
# OPT="$OPT -XX:+AggressiveOpts"
# (Speeds up locking uncontended monitors)
# OPT="$OPT -XX:+UseBiasedLocking"
OPT="$OPT -XX:+UseFastAccessorMethods"
# OPT="$OPT -XX:+UseParNewGC"

# ---------------------------------------------
# Ibis options
# ---------------------------------------------

#use ibis-mx
#OPT=$OPT -Dibis.implementation=mx -Djava.library.path=external/:/usr/local/Cluster-Apps/mx/mx-current/lib/"
#use mpi ibis(does not work)
#OPT=$OPT sge-script sge-script dorun-mpich-mx $JAVA_HOME/bin/java $OPT $CONFIG -Dibis.implementation=mpi -Dibis.mpi.libpath=/home/ceriel/Ela/jbrno/external $@" 
#use tcp-ibis on ethernet
#-Dibis.implementation=tcp 
#-Dibis.implementation=tcp -Dibis.ipl.impl.tcp.smartsockets=false
OPT="$OPT -Dibis.io.smallarraybound=32"

#profile with jrat
#OPT=$OPT -javaagent:shiftone-jrat.jar

# Execute threads
HOSTNAME=localhost:$PORT
echo "Connecting to server $HOSTNAME" > /dev/stderr
echo "Executing $NPROC threads of $ARGS"
COMMAND="java\
 -classpath $CLASSPATH $OPT -Dlog4j.configuration=$LOGPROPS\
 -Dibis.server.address=$HOSTNAME\
 -Dibis.pool.name=$POOL $LOCALRUN_JAVAOPTS\
 -Dhipg.poolSize=$NPROC $ARGS" 

echo "$COMMAND" > /dev/stderr

if [ $NPROC == "1" ]; then
	exec $COMMAND
else 
	#set -m
	PIDS=
	for x in $(seq 1 $NPROC); do
		echo "Thread $x .."
		exec $COMMAND &
		PIDS="$PIDS $!"
	done
	num=1
	for pid in $PIDS; do
		wait $pid
		#fg $num 2>/dev/null
		#let num=$num+1
	done
fi

