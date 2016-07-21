#Shexter - Shell Texter

Send and read texts from your Android phone using your Linux or Windows command line.

[Get command-line client here](https://github.com/tetchel/Shexter/raw/master/shexter_client.tar.gz)

[Get apk here](https://github.com/tetchel/Shexter/raw/master/shexter/app/build/outputs/apk/shexter.apk)

##Client Setup

**Dependencies:** Python 3.

To install, extract the client archive, navigate to the installer for your platform, and run the installer (using `python installer_windows.py` or `sudo ./install_linux.sh`) through the command line. If the install is successful, after restarting your terminal, you should be able to run 'shexter' from anywhere, and consult the help for how to use the client.

On first run, you will be prompted for an IP address - make sure your phone and computer are on the same LAN and then enter the IP that appears in the Shexter app.

Note that, for now, Shexter ignores MMS messages altogether. This can be a problem when communicating with users who send MMS and don't realize it.

### Fonts (Optional)

You are probably going to want Unicode font support in your terminal so your Unicode characters do not show as blocks.

**screenshots comming soon**

On Linux:
For `rxvt-unicode`, a popular terminal emulator, you can set the current line in your `.Xresources`:
`URxvt*font: -xos4-terminus-medium-r-normal--14-140-72-72-c-80-iso10646-1, xft:WenQuanYi Micro Hei Mono,style=Regular, xft:Symbola`

The first font is a bitmap font in XLFD format. The other two are Xft. The order depicts the glyph priority if there is overlap.

So this setting would show Terminus for ASCII, WenQuanYi Micro Hei Mono for Chinese, and Symbola for remaining Unicode characters such as emoji.

**To do**: fix support for Noto Emoji, which is the same font that Android uses.

##App Setup

You must [enable installation from unknown sources](http://www.androidcentral.com/allow-app-installs-unknown-sources) in order to be able to install the apk.

Requires Internet, Contacts and SMS permissions. The app requests permissions, but this functionality has not been tested extensively so you may need to enable them yourself in Settings > Apps > Shexter > Permissions

You may want to check your Security settings for your SMS message limit setting, which can prevent Shexter from sending frequent messages.
