#!/bin/bash

######## LIVEJOURNAL ########

graph=soc-LiveJournal1

for algorithm in hipg.app.HopDist hipg.app.PageRankWithTermination
do
	slots=1
	for n in 1 2 4 8 16 24 32      	   
	do
		bin/run-bunch.sh -slots ${slots} ${n} ${algorithm} svcii /home/janlugt/graphs/${graph}_svcii_$(($n * $slots)) &> results/${graph}_${algorithm}_${slots}_${n}
	done
	
	slots=2
	for n in 20 24 28 32	   
	do
		bin/run-bunch.sh -slots ${slots} ${n} ${algorithm} svcii /home/janlugt/graphs/${graph}_svcii_$(($n * $slots)) &> results/${graph}_${algorithm}_${slots}_${n}
	done
	
	slots=3
	for n in 32
	do
		bin/run-bunch.sh -slots ${slots} ${n} ${algorithm} svcii /home/janlugt/graphs/${graph}_svcii_$(($n * $slots)) &> results/${graph}_${algorithm}_${slots}_${n}
	done
	
	slots=4
	for n in 32
	do
		bin/run-bunch.sh -slots ${slots} ${n} ${algorithm} svcii /home/janlugt/graphs/${graph}_svcii_$(($n * $slots)) &> results/${graph}_${algorithm}_${slots}_${n}
	done
done
