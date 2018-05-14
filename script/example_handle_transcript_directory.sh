#!/bin/bash
#
# A bash script to receive the directory of a newly published
# transcript and audio file.
#
# The first and only argument will always be the absolute path
# to the new directory.
#
# The path to a script needs to be given in:
# jigasi/jigasi-home/sip-communicator.properties

abs_dir_path=$1
echo "$abs_dir_path" >> dirs.txt