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
mkdir -p $OPT_DIR && cp ../../shexter.py $OPT_DIR && 
        cp ../../lib/appdirs.py $OPT_DIR && cp ./shexter $OPT_DIR

ln -s $OPT_DIR"shexter" /usr/bin/shexter

if [ -f $OPT_DIR"shexter.py" ]  && [ -f $OPT_DIR"appdirs.py" ] && 
        [ -f /usr/bin/shexter ]; then
	
    chmod a+x /opt/shexter/shexter
    # TODO The problem with this is that it will print success even if it failed if
    # files existed before. so should also check their last modified time.
    echo "Success!"
else
	echo "Something went wrong."
fi
