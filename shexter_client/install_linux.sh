#!/bin/sh

#short script to 'install' shexter
#copies shexter.py to /opt/, then the shexter executable to /usr/bin.
#needs sudo

if [ "$(id -u)" != "0" ]; then
	echo "You must run this installer as sudo since it writes to /usr/bin and /opt/"
	exit 1
fi

if [ -f /opt/shexter/shexter.py ]; then
	rm /opt/shexter/shexter.py
fi
if [ -f /usr/local/bin/shexter ]; then
	rm /usr/local/bin/shexter
fi

# not necessary with mkdir -p
#if [ ! -d /opt/ ]; then
#	mkdir /opt/
#fi

mkdir -p /opt/shexter && cp ./shexter.py "$_" && cp ./shexter "$_"
ln -s /opt/shexter/shexter /usr/bin/shexter


if [ -f /opt/shexter/shexter.py ] && [ -f /usr/bin/shexter ]; then
	chmod a+x /opt/shexter/shexter
	echo "Success!"
else
	echo "Something went wrong."
fi
