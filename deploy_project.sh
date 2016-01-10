#!/bin/bash

#  assumes we are in the project home directory
lein uberjar
ret=$?
if [ $ret -ne 0 ]; then
  echo "Failed to build project"
  exit $ret
fi

#  write out jar file versions and names to VERSIONS.txt file and save the 
#  application name to an env var
app_name=`ls -1 target/*.jar  | cut -d "/" -f 2 | tee VERSIONS.txt | grep -v original | tail -n 1 | cut -d "-" -f 1`

gsutil cp target/*.jar gs://build-artifacts-public-eu/${app_name}
ret=$?
if [ $ret -ne 0 ]; then
  echo "Failed to cp jar files to gstorage"
  exit $ret
fi

gsutil cp VERSIONS.txt gs://build-artifacts-public-eu/${app_name}
ret=$?
if [ $ret -ne 0 ]; then
  echo "Failed to cp VERSIONS.txt to gstorage"
  exit $ret
fi

curl -XPOST https://circleci.com/api/v1/project/22acacia/demo-config/tree/master?circle-token=$CIRCLE_TOKEN
