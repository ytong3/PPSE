#!bin/bash

root=`dirname "$0"`
root=`cd "$root"; pwd`

# read slave.conf
slaveFile="$1"
pubKeyFile="$2"
while read line; do
	#echo "Parsed address"
	arr=(${line//:/ })
	#echo "Hostname:${arr[0]}, portnumber:${arr[1]}"
	# start ssh
	echo "#######Start RMIServer at ${arr[0]}:${arr[1]}###########"
	ssh -n "${arr[0]}" "cd $root;bash start_local_slave.sh ${arr[1]} $pubKeyFile"
	#ssh -n "${arr[0]}" "uname -a ; uptime"
	echo ""
done < "$slaveFile"
