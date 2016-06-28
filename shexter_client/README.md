#Shexter-Client

Client written in Python to be invoked from the command line to send and read texts using the Shexter Android app.

## Settings

Settings are stored and read from `settings.ini` in the `shexter_client` directory.

Only read is supported currently. Write will be added shortly, e.g. create SlaveIP setting from Shexter app if no Setting exists.

Settings will be moved in the future to `XDG_CONFIG_HOME/shexter/settings.ini`

[Settings]
Port: 5678
SlaveIP: 192.168.0.1 # this is the subnet IP address from the Shexter app
