#!/bin/sh

#this function will check and print the verbose statement if required
verbosePrint() {
  if [ $1 -eq 1 ]
  then
    echo "$2"
  fi
}

usage() {
          echo
          echo "Usage: $0 [-v] [-i] [-h] -D <path/to/yugabyte/installation/>" 1>&2;
          echo 
          echo "-v - Run in verbose mode"
          echo "-i - Run in interactive mode"
          echo "-h - Print the help for this script"
          echo "-D - The installation directory of YugabyteDB"
          echo
          exit 1;
        }              

VERBOSE=0                                                 
INTERACTIVE=0                                               
INSTALL_DIR=""

while getopts ":vihD:" o; do                                        
  case "$o" in                                             
    v)                                                 
      VERBOSE=1                                           
      ;;                                               
    i)                                                 
      INTERACTIVE=1                                         
      ;;                                               
    h)                                                 
      usage
      ;;                                               
    D)                                                 
      INSTALL_DIR=${OPTARG}                                     
      ;;                                               
    *)                                                 
      usage                                             
      ;;                                               
  esac                                                  
done   

if [ -z $INSTALL_DIR ]
then
  usage
fi

check_dir=$INSTALL_DIR/bin/yb-ctl
if [ ! -f "$check_dir" ]
then
  echo "ERROR: incorrect yugabytedb directory path: $INSTALL_DIR"
  exit 1
fi

SLEEP 1

verbosePrint $VERBOSE "YugabyteDB installation directory is: $INSTALL_DIR"

echo "Choose one of the below options"
echo "1. Demonstrate Uniform Load Balance"
echo "2. Demonstrate Topology Aware Load Balance"
read -p "Please enter your option and then press enter:" choice
echo ""

case $choice in
  1)
    verbosePrint $VERBOSE "starting the uniform_load_balance_run.sh script"
    ./uniform_load_balance_run.sh $VERBOSE $INTERACTIVE $INSTALL_DIR
    ;;
  2) 
    verbosePrint $VERBOSE "starting the topology_aware_load_balance_run.sh script"
    ./topology_aware_load_balance_run.sh $VERBOSE $INTERACTIVE $INSTALL_DIR
    ;;
  *)
    echo "INVALID OPTION"
    exit 1
    ;;
esac

exit 0
