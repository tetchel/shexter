#!/bin/sh

#short script to 'install' shexter
#copies shexter.py to /opt/, then symlinks the shexter executable to /usr/bin.

if [ "$(id -u)" != "0" ]; then
	echo "You must run this installer with root permissions for access to /opt/ and /usr/bin/"
	exit 1
fi

if [ -f /usr//bin/shexter ]; then
	echo "Removing existing symlink at /usr/bin/shexter"
	rm /usr/bin/shexter
fi

mkdir -p /opt/shexter && cp ./shexter.py "$_" && cp ./shexter "$_"
ln -s /opt/shexter/shexter /usr/bin/shexter

if [ -f /opt/shexter/shexter.py ] && [ -f /usr/bin/shexter ]; then
	chmod a+x /opt/shexter/shexter
	echo "Success!"
else
	echo "Something went wrong."
fi
