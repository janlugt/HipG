#!/bin/bash
#
# Executes a HipG program on a cluster using srun.
# Usage is printed when run with no parameters.
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
function usage() {
	echo "Usage: $0 [ -port <port> ] [ -pool <pool> ] [ -host <host> ] [ -heap <heap> ] [ -out <out> ] [ -slots <slots> ] <processors> <program>"
	echo "  <port> = port on which the server executes, default $DEFAULTPORT"
        echo "  <pool> = a unique name for this execution, default based on current date"
        echo "  <host> = host running a server, by default the localhost where this script is run"
        echo "  <processors> = number of processors to reserve"
	exit 1
}

NPROC=
POOLNAME=
PORT=
HOST=
HEAP=
SLOTS=
OUT=
last=
for x in $*; do
	if [ "$last" == "-port" ]; then
		PORT=$x
		last=
	elif [ "$last" == "-pool" ]; then
		POOLNAME=$x
		last=
	elif [ "$last" == "-host" ]; then
		HOST=$x
		last=
	elif [ "$last" == "-heap" ]; then
		HEAP=$x
		last=
	elif [ "$last" == "-out" ]; then
		OUT=$x
		last=
	elif [ "$last" == "-slots" ]; then
		SLOTS=$x
		last=
	elif ( [ $x == "-port" ] || [ $x == "-pool" ] || [ $x == "-host" ] || [ $x == "-heap" ] || [ $x == "-out" ] || [ $x == "-slots" ] ); then
		last=$x
	elif [ -z "$NPROC" ]; then
		NPROC=$x
	else 
		ARGS="$ARGS $x"
	fi
done
if [ -z "$NPROC" ]; then
	echo "No NPROC specified"
	usage
	exit 1
fi
if [ -z "$ARGS" ]; then
	echo "No app specified"
	usage
	exit 1
fi
if [ -z "$PORT" ]; then
	PORT=$DEFAULTPORT
fi
if [ -z "$POOLNAME" ]; then
	POOLNAME=pool-$(echo $ARGS | cut -d ' ' -f 1)-$(date +%Y%m%d%H%M%S)-$(date +%N)
fi
if [ -z "$HOST" ]; then
	HOST=$(hostname)
fi
if [ -z "$HEAP" ]; then
	HEAP=32g
fi

# Execute
HOSTNAME=$HOST:$PORT
echo "Connecting to server $HOSTNAME" > /dev/stderr

# set java binary
JAVA_HOME="/cm/shared/apps/java/jre1.7.0_09"

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

# (Ratio of tenured-to-young generation size)
OPT="$OPT -XX:NewRatio=24"
# (Min/Max size of the young generation (finer tuning than NewRatio))
#OPT="$OPT -XX:NewSize=100m -XX:MaxNewSize=100m"


# ---------------------------------------------
# Select and configure garbage collector
# ---------------------------------------------

#(Enable generational GC -> less collections, but up to 10% slower,
# will use the ConcMarkAndSweep collector)
#OPT="$OPT -Xincgc"
# (Use serial collector)
#OPT="$OPT -XX:+UseSerialGC"
# (Use throughtput collector (=parallel scavenge collector) in which 
#  multiple threads do minor collections and major collections are 
#  serial; requirements: big enough young generation, multicore)
#OPT="$OPT -XX:+UseParallelGC"
#  option: number of threads participating in minor collections
#OPT="$OPT -XX:ParallelGCThreads=2"
# (Use concurrent low pause collector; good typically when tenured
#  generation is large)
OPT="$OPT -XX:+UseConcMarkSweepGC"
#   option: incremental mode (major collections broken into smaller
#     pieces of work, scheduled inbetween minor collections)
OPT="$OPT -XX:+CMSIncrementalMode"
#   option: duty cycle in the incremental model adjusted automatically
OPT="$OPT -XX:+CMSIncrementalPacing"
#   option: use the specified number of threads for parallel 
#     marking and sweeping, respectively
#OPT="$OPT CMSConcurrentMTEnabled $OPT ParallelCMSThreads=2"
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
# allocate large objects directly in the tenured generation
OPT="$OPT -XX:PretenureSizeThreshold=5k"

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
# OPT="$OPT -XX:+UseCompressedOops"
# (Aggressive experimental options!)
# OPT="$OPT -XX:+AggressiveOpts"
# (Speeds up locking uncontended monitors)
# OPT="$OPT -XX:+UseBiasedLocking"
OPT="$OPT -XX:+UseFastAccessorMethods"
# OPT="$OPT -XX:+UseParNewGC"
OPT="$OPT -Dcom.sun.sdp.conf=sdp.conf -Djava.net.preferIPv4Stack=true"

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

# set srun options
if [ -z "$SLOTS" ]; then
	SLOTS=1
fi
POOLSIZE=$(echo "$SLOTS*$NPROC" | bc)
SRUNOPT="-v --ntasks-per-node=$SLOTS --nodes=$NPROC"
if [ -n "$OUT" ]; then
	SRUNOPT="$SRUNOPT -o $OUT"
fi

JAVACOMMAND="$JAVA_HOME/bin/java $OPT $CONFIG\
 -Djava.home=$JAVA_HOME\
 -classpath $CLASSPATH -Dlog4j.configuration=$LOGPROPS\
 -Xmx$HEAP -Xms$HEAP\
 -Dibis.server.address=$HOSTNAME\
 -Dibis.pool.name=$POOLNAME\
 -Dhipg.poolSize=$POOLSIZE\
  $RUN_JAVAOPTS\
  $ARGS" 

# Command to run in parallel
COMMAND="srun $SRUNOPT $JAVACOMMAND"
echo "Running $COMMAND"
srun $SRUNOPT $JAVACOMMAND

