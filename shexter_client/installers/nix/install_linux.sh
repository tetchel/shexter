#!/usr/bin/env bash

#short script to 'install' shexter
#copies shexter.py to /opt/, then symlinks the shexter executable to /usr/bin.

if [ -f /usr/bin/shexter ]; then
    echo "Removing existing symlink at /usr/bin/shexter"
    rm /usr/bin/shexter
fi

OPT_DIR='/opt/shexter/'

# $_ should have worked but it did not
mkdir -p $OPT_DIR && 
        cp ../../shexter.py $OPT_DIR && 
        cp ./shexter $OPT_DIR &&
        cp ../../shexter_persistant.py $OPT_DIR

if [ $? -ne 0 ]; then
    echo "Install failed. Make sure your working directory is the original installer location, and that you have the permission to write to "$OPT_DIR
    exit 1
fi

ln -s $OPT_DIR"shexter" /usr/bin/shexter

NOW=`date +"%Y%m%d%H%M%S"`
FDATE1=`date -r $OPT_DIR"shexter.py" +"%Y%m%d%H%M%S"`

if [ -f $OPT_DIR"shexter.py" ]  && [ -f /usr/bin/shexter ] && 
    [ `expr $NOW - $FDATE1` -lt 5 ]; then

    chmod -R a+rx /opt/shexter 

    echo "Success!"
else
    echo "Something went wrong."
fi
