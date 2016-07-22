#!/bin/sh

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

# $_ should have worked but it did not
mkdir -p $OPT_DIR && 
        cp ../../shexter.py $OPT_DIR && 
        cp ../../lib/appdirs.py $OPT_DIR && cp ./shexter $OPT_DIR &&
        cp ../../shexter_persistant.py $OPT_DIR

ln -s $OPT_DIR"shexter" /usr/bin/shexter

# get dates of files to see if they were updated
NOW=`date +"%Y%m%d%H%M%S"`
FDATE1=`date -r $OPT_DIR"shexter.py" +"%Y%m%d%H%M%S"`
FDATE2=`date -r $OPT_DIR"appdirs.py" +"%Y%m%d%H%M%S"`

if [ -f $OPT_DIR"shexter.py" ]  && [ -f $OPT_DIR"appdirs.py" ] && 
        [ -f /usr/bin/shexter ] && [ `expr $NOW - $FDATE1` -lt 5 ] &&
        [ `expr $NOW - $FDATE2` -lt 5 ] ; then
	
    chmod a+rx /opt/shexter/shexter
    chmod a+rx /opt/shexter/shexter.py
    chmod a+rx /opt/shexter/shexter_persistant.py

    echo "Success!"
else
	echo "Something went wrong."
fi
