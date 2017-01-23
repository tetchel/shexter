#!/usr/bin/env bash

#short script to 'install' shexter
#copies shexter.py to /opt/, then symlinks the shexter executable to /usr/bin.

if [ "$(id -u)" != "0" ]; then
    echo "You must run this installer with root permissions for access to /opt/ and /usr/bin/"
    exit 1
fi

if [ -f /usr/bin/shexter ]; then
    echo "Removing existing symlink at /usr/bin/shexter"
    rm /usr/bin/shexter
fi

OPT_DIR='/opt/shexter/'
LIB_DIR="${OPT_DIR}lib/"

# $_ should have worked but it did not
mkdir -p $LIB_DIR && 
        cp ../../shexter.py $OPT_DIR && 
        cp ../../lib/appdirs.py $LIB_DIR && cp ./shexter $OPT_DIR &&
        cp ../../shexter_persistant.py $OPT_DIR

ln -s $OPT_DIR"shexter" /usr/bin/shexter

if [ -f $OPT_DIR"shexter.py" ]  && [ -f $LIB_DIR"appdirs.py" ] && 
        [ -f /usr/bin/shexter ]; then

    chmod -R a+rx /opt/shexter    
    #chmod a+rx /opt/shexter/shexter
    #chmod a+rx /opt/shexter/shexter.py
    #chmod a+rx /opt/shexter/shexter_persistant.py

    echo "Success!"
else
    echo "Something went wrong."
fi
