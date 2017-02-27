import os
import sys
from configparser import ConfigParser
from socket import inet_aton

''' This file deals with reading and writing settings. Call configure() to get the ip address.'''

APP_NAME = "shexter"
SETTINGS_FILE_NAME = APP_NAME + '.ini'
SETTING_SECTION_NAME = 'Settings'
SETTING_IP = 'IP Address'
SETTING_PORT = 'Port'


def _write_config_file(fullpath, ip_addr):
    #  configure the new settings and then write it into the file
    configfile = open(fullpath, 'w')
    # configfile = open(user_config_dir(APP_NAME), 'w')
    config = ConfigParser()
    config.add_section(SETTING_SECTION_NAME)
    config.set(SETTING_SECTION_NAME, SETTING_IP, ip_addr)
    # config.set(SETTING_SECTION_NAME, SETTING_PORT, str(DEFAULT_PORT))
    config.write(configfile)
    configfile.close()


# Deletes the settings file and creates a new one, requiring an IP address from the user.
# Returns the IP address.
def _new_settings_file(config_file_path):
    # remove settings if it exists
    if os.path.isfile(config_file_path):
        os.remove(config_file_path)

    new_ip_addr = ''
    validip = False
    # prompt user for an IP address until they give you one.
    while not validip:
        try:
            new_ip_addr = input('Enter your IP Address from the Shexter app in dotted decimal '
                                '(eg. 192.168.1.1): ')
        except (EOFError, KeyboardInterrupt):
            # user gave up
            print()
            quit()

        try:
            # validate ip
            inet_aton(new_ip_addr)
            print('Setting your phone\'s IP to ' + new_ip_addr)
            validip = True
        except OSError:
            print('Invalid IP Address. Try again. (CTRL + C to give up)')

    _write_config_file(config_file_path, new_ip_addr)
    return new_ip_addr


# Assembles and returns the absolute path to the settings file.
def _create_config_file():
    # platform-dependent: Check an environment variable for user config directory
    platf = sys.platform
    # This env var is used for anything not windows or osx -
    # ie, linux, cygwin, as a backup in case there's an error or user is using something strange.
    env_var = 'XDG_CONFIG_HOME'
    home_dir = os.getenv("HOME")
    if home_dir:
        default_path = home_dir + '/.config'
    else:
        default_path = ''

    if platf.startswith('win'):
        env_var = 'LOCALAPPDATA'
        # TODO default for windows?
    else:
        if not (platf.startswith('linux') or platf.startswith('cyg')):
            print('"' + platf + '" is not a recognized platform. If you can, set the ' +
                  'environment variable ' + env_var + '. If you can\'t set it, ' +
                  'use a supported platform.')

    if platf.startswith('darwin'):
        # os x does not have a corresponding env var
        print('macos is not supported at this time')

    config_path = os.getenv(env_var, default_path)
    if not config_path:
        print('Unable to get config directory. Please set the environment variable ' + env_var + '.')

    config_path = os.path.join(config_path, APP_NAME)
    if not os.path.exists(config_path):
        try:
            os.makedirs(config_path)
        except PermissionError:
            # either user has directory open, or doesn't have w/x permission (on own config dir?)
            print('Could not access ' + config_path + ', please check the directory\'s permissions, '
                                                      'and close any program using it.')
            pass

    return os.path.join(config_path, SETTINGS_FILE_NAME)

glob_config_file_path = None


# Try and load existing config file. Create a new config file if needed.
# Will create a new settings file if (edit_mode),
# or if there's a problem with the settings file (or there isn't one)
# Returns the new ip address.
def configure(edit_mode):
    config_file_path = _create_config_file()
    global glob_config_file_path
    glob_config_file_path = config_file_path

    config = ConfigParser()
    config.read(config_file_path)

    new_settings_file_required = False
    ip_addr = ''
    try:
        ip_addr = config[SETTING_SECTION_NAME][SETTING_IP]
        if edit_mode:
            print('Your settings file is ' + config_file_path)
            confirm = input('Your current IP is ' + ip_addr + '\nWould you like to change it? y/N: ')
            if confirm is 'y' or confirm is 'Y':
                new_settings_file_required = True
            else:
                print('Configuring cancelled.')
    except KeyError:
        print('Error parsing ' + config_file_path + '. Making a new one.')
        new_settings_file_required = True
    except OSError:
        print('Bad IP ' + ip_addr + ' found in ' + config_file_path + '. Making a new one.')
        new_settings_file_required = True

    if new_settings_file_required:
        ip_addr = _new_settings_file(config_file_path)

    return ip_addr
