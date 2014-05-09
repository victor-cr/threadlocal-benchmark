#!/bin/bash

JAVA=$1
CS="-Dharness.executor=CUSTOM -Dharness.executor.class=com.oracle.jdk.benchmark.MyThreadExecutorService"
OPTS="-f 5 -wi 5 -i 5"

$JAVA     -jar microbenchmarks.jar ".*original.*" $OPTS -t 1 | tee threads-orig-1.log
$JAVA $CS -jar microbenchmarks.jar ".*custom.*"   $OPTS -t 1 | tee threads-custom-1.log

$JAVA     -jar microbenchmarks.jar ".*original.*" $OPTS -t 12 | tee threads-orig-12.log
$JAVA $CS -jar microbenchmarks.jar ".*custom.*"   $OPTS -t 12 | tee threads-custom-12.log

$JAVA     -jar microbenchmarks.jar ".*original.*" $OPTS -t 24 | tee threads-orig-24.log
$JAVA $CS -jar microbenchmarks.jar ".*custom.*"   $OPTS -t 24 | tee threads-custom-24.log

$JAVA     -jar microbenchmarks.jar ".*original.*" $OPTS -t 48 | tee threads-orig-48.log
$JAVA $CS -jar microbenchmarks.jar ".*custom.*"   $OPTS -t 48 | tee threads-custom-48.log
