#!/bin/sh

CP_DIR=deps
CP=.
for j in `ls ${CP_DIR}/*.jar`; do 
  CP=$CP:$j
done

java -cp $CP clojure.main run-dastard.clj
