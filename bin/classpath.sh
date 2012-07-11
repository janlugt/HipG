#!/bin/bash
#
# Creates Java classpath from all the jarfiles found
# in specified directories.
# Not meant to be directly used by HipG users.
# 
# Usage:
#   ./classpath <dir1> <dir2>
#
# Author: Ela Krepska
# Copyright VU University Amsterdam 2010
#

# Usage
if [ $# -lt 1 ]; then
	echo "Usage: $0 <dir1> <dir2> ... "
	exit 1
fi
find=/usr/bin/find

# Resulting classpath
CP=

# Jar-files from library.
list_jars () {
    JARFILES=$($find $1 -name '*.jar' -follow | sort 2>/dev/null)
    for i in ${JARFILES} ; do
	if [ -z "$CP" ] ; then
	    CP="$i"
	else
	    CP="$CP:$i"
	fi
    done
}

CP=.
for x in $*
do
	list_jars $x
done
#CP=$(cygpath -w -p $CP)

echo $CP
