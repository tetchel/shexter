import os
import sys
from configparser import ConfigParser
from enum import Enum


''' This file deals with reading and writing settings. Call configure() to get the ip address.'''

APP_NAME = 'shexter'
SETTINGS_FILE_NAME = APP_NAME + '.ini'
SETTING_SECTION_NAME = 'Settings'
SETTING_IP = 'IP Address'
SETTING_PORT = 'Port'


def _write_config_file(fullpath, ip_addr):
    """
    Write the settings to the file.
    :param fullpath: Path to the file.
    :param ip_addr: IP address to write.
    :return: Nothing.
    """

    configfile = open(fullpath, 'w')
    # configfile = open(user_config_dir(APP_NAME), 'w')
    config = ConfigParser()
    config.add_section(SETTING_SECTION_NAME)
    config.set(SETTING_SECTION_NAME, SETTING_IP, ip_addr)
    # config.set(SETTING_SECTION_NAME, SETTING_PORT, str(DEFAULT_PORT))
    config.write(configfile)
    configfile.close()


def _new_settings_file(config_file_path):
    """
    Deletes the settings file and creates a new one, requiring an IP address from the user.
    :param config_file_path: Path to create the settings file at.
    :return: Returns the new IP address.
    """

    # remove settings if it exists
    if os.path.isfile(config_file_path):
        os.remove(config_file_path)

    new_ip_addr = ''
    # prompt user for an IP address until they give you one.
    try:
        new_ip_addr = input('Enter your IP address from the Shexter app (eg. 192.168.1.101): ')
    except (EOFError, KeyboardInterrupt):
        # user gave up
        print()
        quit()

    print('Setting your phone\'s IP address to ' + new_ip_addr)

    _write_config_file(config_file_path, new_ip_addr)
    return new_ip_addr


def _create_config_file(platf):
    """
    Assembles and returns the absolute path to the settings file.
    :param platf: Platform as set by get_platform
    :return: Full path to settings file
    """

    if platf == Platform.WIN:
        config_path = os.environ['LOCALAPPDATA']
    else:
        if not (platf == Platform.LINUX or platf == Platform.CYGWIN):
            print('Your platform is not supported, but you can still use '
                  'Shexter by setting the HOME environment variable.')

        config_path = os.path.join(os.environ['HOME'], '.config')

    if not config_path:
        sys.exit('Could not get environment variable for config creation.')

    config_path = os.path.join(config_path, APP_NAME)
    if not os.path.exists(config_path):
        try:
            os.makedirs(config_path)
            print('Creating a new folder at ' + config_path)
        except PermissionError:
            # either user has directory open, or doesn't have w/x permission (on own config dir?)
            sys.exit('Could not access ' + config_path + ', please check the directory\'s permissions, '
                                                         'and close any program using it.')

    return os.path.join(config_path, SETTINGS_FILE_NAME)

# These two variables are not to be modified by other classes
# full path to the config file
glob_config_file_path = None

# Platform - an instance of the Platform enum
glob_platform = None


class Platform(Enum):
    WIN = 1
    LINUX = 2
    MACOS = 3
    CYGWIN = 4
    OTHER = 5


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
        platform = Platform.WIN
    elif platf.startswith('darwin'):
        print('WARNING: macos is not supported at this time')
        platform = Platform.MACOS
    else:
        if platf.startswith('linux'):
            platform = Platform.LINUX
        elif platf.startswith('cyg'):
            platform = Platform.CYGWIN
        else:
            print('WARNING: Unrecognized (and therefore unsupported) platform ' + platf)
            platform = Platform.OTHER

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
    ip_addr = ''
    try:
        ip_addr = config[SETTING_SECTION_NAME][SETTING_IP]
        if edit_mode:
            print('Your settings file is ' + config_file_path)
            confirm = input('Your current IP address is ' + ip_addr + '\nWould you like to change it? y/N: ')
            if confirm.lower() == 'y':
                new_settings_file_required = True
            else:
                print('Configuring cancelled.')
    except KeyError:
        print('Error parsing ' + config_file_path + '. Making a new one.')
        new_settings_file_required = True
    except OSError:
        print('Bad hostname ' + ip_addr + ' found in ' + config_file_path + '. Making a new one.')
        new_settings_file_required = True

    if new_settings_file_required:
        ip_addr = _new_settings_file(config_file_path)

    return ip_addr
