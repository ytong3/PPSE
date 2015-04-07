#!/usr/local/bin/bash

# first argument is the port_number
# second argument is the public key file

USAGE="start_local_slave port_num public_key_file"

if [[ $# -ne 2 ]];then 
	echo $USAGE
	exit
fi

echo "Starting rmiresigstry at port $1"
cd bin
rmiregistry $1 &
export rmiregistry_PID="$!"
echo "rmiregistry backgrounded process $!"
cd ..


echo "Register the local RMI server at the register"
java -cp ./bin/:jscience.jar utk.security.PPSE.slave.RMIServer $1 $2 1>>test.log 2>>error.log &
export RMIServer_PID=$!
echo "Java RMIServer backgrounded process $!"
