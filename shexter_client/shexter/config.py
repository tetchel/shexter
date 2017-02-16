import os
import sys
from configparser import ConfigParser

''' This file deals with reading and writing settings. Call configure() to get the ip address.'''

APP_NAME = "shexter"
SETTINGS_FILE_NAME = APP_NAME + '.ini'
SETTING_SECTION_NAME = 'Settings'
SETTING_HOSTNAME = 'hostname'
SETTING_PORT = 'Port'


def _write_config_file(fullpath, ip_addr):
    #  configure the new settings and then write it into the file
    configfile = open(fullpath, 'w')
    # configfile = open(user_config_dir(APP_NAME), 'w')
    config = ConfigParser()
    config.add_section(SETTING_SECTION_NAME)
    config.set(SETTING_SECTION_NAME, SETTING_HOSTNAME, ip_addr)
    # config.set(SETTING_SECTION_NAME, SETTING_PORT, str(DEFAULT_PORT))
    config.write(configfile)
    configfile.close()


# Deletes the settings file and creates a new one, requiring an IP address from the user.
# Returns the IP address.
def _new_settings_file(config_file_path):
    # remove settings if it exists
    if os.path.isfile(config_file_path):
        os.remove(config_file_path)

    new_hostname = ''
    # prompt user for an IP address until they give you one.
    try:
        new_hostname = input('Enter your hostname from the Shexter app (eg. android-1d2e3a4d5b6e7e8f): ')
    except (EOFError, KeyboardInterrupt):
        # user gave up
        print()
        quit()

    print('Setting your phone\'s hostname to ' + new_hostname)

    _write_config_file(config_file_path, new_hostname)
    return new_hostname


def _create_config_file(platf):
    """
    # Assembles and returns the absolute path to the settings file.
    :param platf: Platform as set by get_platform
    :return: Full path to settings file
    """

    # This env var is used for anything not windows right now (OS X should change in future)
    # ie, linux, cygwin, or as a backup in case there's an error or user is using something strange.
    env_var = 'XDG_CONFIG_HOME'
    home_dir = os.getenv("HOME")
    if home_dir:
        default_path = home_dir + '/.config'
    else:
        default_path = ''

    if platf.startswith('win'):
        env_var = 'LOCALAPPDATA'
        # I dont think non-admin user will have write access to this, so hopefully this is never needed...
        default_path = 'C:\\Users\\Default\\AppData\\Local'
    elif platf.startswith('darwin'):
        # os x does not have a corresponding env var
        # could still set XDG_CONFIG_DIR, though (probably)
        print('WARNING: macos is not supported at this time')
    else:
        if not (platf.startswith('linux') or platf.startswith('cyg')):
            print('"' + platf + '" is not a recognized platform. If you can, set the ' +
                  'environment variable ' + env_var + '. If you can\'t set it, ' +
                  'use a supported platform.')

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

# These two variables are not to be modified by other classes
# full path to the config file
glob_config_file_path = None

glob_platform = None


def get_platform():
    """
    :return:    The user's platform: hopefully one of: linux, win, cyg, macos.
                If it's an unknown platform, it is whatever sys.platform returned.
    """
    # persist this data since it won't change during execution
    global glob_platform
    if glob_platform is not None:
        return glob_platform

    platf = sys.platform

    if platf.startswith('win'):
        platform = 'win'
    elif platf.startswith('darwin'):
        # os x does not have a corresponding env var
        # could still set XDG_CONFIG_DIR, though (probably)
        print('WARNING: macos is not supported at this time')
        platform = 'macos'
    else:
        if platf.startswith('linux'):
            platform = 'linux'
        elif platf.startswith('cyg'):
            platform = 'cyg'
        else:
            print('WARNING: Unrecognized (and therefore unsupported) platform ' + platf)
            platform = platf

    return platform


# Try and load existing config file. Create a new config file if needed.
# Will create a new settings file if (edit_mode),
# or if there's a problem with the settings file (or there isn't one)
# Returns the new ip address.
def configure(edit_mode):
    global glob_platform
    if glob_platform is None:
        glob_platform = get_platform()

    config_file_path = _create_config_file(glob_platform)
    global glob_config_file_path
    glob_config_file_path = config_file_path

    config = ConfigParser()
    config.read(config_file_path)

    new_settings_file_required = False
    hostname = ''
    try:
        hostname = config[SETTING_SECTION_NAME][SETTING_HOSTNAME]
        if edit_mode:
            print('Your settings file is ' + config_file_path)
            confirm = input('Your current hostname is ' + hostname + '\nWould you like to change it? y/N: ')
            if confirm.lower() == 'y':
                new_settings_file_required = True
            else:
                print('Configuring cancelled.')
    except KeyError:
        print('Error parsing ' + config_file_path + '. Making a new one.')
        new_settings_file_required = True
    except OSError:
        print('Bad hostname ' + hostname + ' found in ' + config_file_path + '. Making a new one.')
        new_settings_file_required = True

    if new_settings_file_required:
        hostname = _new_settings_file(config_file_path)

    return hostname
