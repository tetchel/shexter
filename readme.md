#Shexter - Shell Texter

Send and read texts from your Android phone using your Linux or Windows command line.

[Get client here](https://raw.githubusercontent.com/tetchel/Shexter/master/shexter_client/shexter.py)

[Get apk here](https://github.com/tetchel/Shexter/raw/master/shexter/app/build/outputs/apk/shexter.apk)

##Client Setup

**Dependencies:** Python: Version 3 for Windows Installer, 2.7 or 3 for Windows Client, 2.7 or 3 for Linux client.

In order to start using Shexter you must edit the client .py file to use your subnet IP address, which can be found by opening the Shexter app on your phone. The IP should stay the same until you reboot your router, but if the client starts freezing or is otherwise failing check if it has changed. Obviously I will change this in the future to be more user friendly.

Then, on Linux, cd to the directory with the Shexter files and run the installer to have Shexter accessible from anywhere. 

The Windows installer does not yet work.

Note that, for now, Shexter ignores MMS messages altogether. This can be a problem when communicating with users who send MMS and don't realize it.

##App Setup

You must [enable installation from unknown sources](http://www.androidcentral.com/allow-app-installs-unknown-sources) in order to be able to install the apk.

Requires Contacts and SMS permissions. The app does not yet request permissions, so you must enable them yourself in Settings > Apps > Shexter > Permissions

You may want to check your Security settings for your SMS message limit setting, which can prevent Shexter from sending frequent messages.