#!/bin/bash
for x in 1 2 5 10 25 50 100; do
	echo $x `perl foo.pl cap-$x` >> cap.plot
	echo $x `perl foo.pl seda-$x` >> seda.plot
	echo $x `perl foo.pl thread-$x` >> thread.plot
	echo $x `perl foo.pl batch-$x` >> batch.plot
	echo $x `perl foo.pl one-$x` >> one.plot
done
