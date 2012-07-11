
 HipG
======

Welcome to Hipg.

Install
=======
Nothing. Unless you want to rebuild, in which case unpack the sources and run
	$ant

Usage
=====

The HipG library is calles hipg-<version>.jar and the 
library with example applications hipg-examples-<version>.jar.

Running the server (necessary):

	$ ./bin/server.sh

Running an application locally:

	$ ./bin/run-local.sh <threads> <pool name> <program>
	
or on a cluster with prun (in which case edit the HOSTNAME in the script to 
point to the machine that runs the server).

	$ ./bin/run.sh <processors> <pool> <program>

Example usage
=============

The visitor application can be run like by P threads on a graph in the SVC-II
format:

./bin/run-local.sh <P> mypool hipg.test.Visitor svcii /path/to/graph-seg<P>.dir <P>  

for the impatient, just execute

./bin/run-local.sh 1 mypool hipg.test.Visitor svcii binary18-seg1.dir 1
./bin/run-local.sh 2 mypool hipg.test.Visitor svcii binary18-seg2.dir 2


Have fun with HipG!!

-- Ela Krepska, ekr@cs.vu.nl

12 Sep 2010