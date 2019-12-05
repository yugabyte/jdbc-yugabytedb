#!/bin/sh

if [ $# -eq 0 ]
  then
    echo "Requires the path to the yugabyte installation as an argument"
    echo "Example:"
    echo "./run.sh ~/yugabyte-2.0.3.0/"
    exit 1
fi

echo "Creating new 3-node, RF-3 cluster (live nodes: 1,2,3)"
${1}/bin/yb-ctl destroy  > yb-ctl.log 2>&1
# Lower some related flags to values below the sleep interval used below. 
${1}/bin/yb-ctl --rf 3 create --tserver_flags="cql_nodelist_refresh_interval_secs=5,follower_unavailable_considered_failed_sec=10" --master_flags="tserver_unresponsive_timeout_ms=5000" >> yb-ctl.log 2>&1
sleep 10

# Start the app to do ops in the background.
java -jar target/jdbc-yugabytedb-example-0.0.1-SNAPSHOT.jar > jdbc-yugabytedb-example.log 2>&1 &
sleep 20

echo "Removing node 1 (new live nodes: 2,3)"
${1}/bin/yb-ctl remove_node 1 > yb-ctl.log 2>&1
sleep 20

echo "Adding node 4 (new live nodes: 2,3,4)"
${1}/bin/yb-ctl add_node >> yb-ctl.log 2>&1
sleep 20

echo "Adding node 5 (new live nodes: 2,3,4,5)"
${1}/bin/yb-ctl add_node >> yb-ctl.log 2>&1
sleep 20

echo "Removing node 3 (new live nodes: 2,4,5)"
${1}/bin/yb-ctl remove_node 3 >> yb-ctl.log 2>&1
sleep 20

echo "Finished cluster operations, tailing app log."
tail -f jdbc-yugabytedb-example.log
