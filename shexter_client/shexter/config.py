import os
import sys
from configparser import ConfigParser
from shutil import get_terminal_size

from shexter.platform import get_platform, Platform
from shexter.sock import find_phones


''' This file deals with reading and writing settings. Call configure() to get the ip address.'''

APP_NAME = 'shexter'
SETTINGS_FILE_NAME = APP_NAME + '.ini'
SETTING_SECTION_NAME = 'Settings'
SETTING_IP = 'IP Address'
SETTING_PORT = 'Port'

# These two variables are not to be modified by other classes
# full path to the config file
glob_config_file_path = None


def _write_config_file(fullpath, connectioninfo):
    """
    Write the settings to the file.
    :param fullpath: Path to the file.
    :param connectioninfo: (IP, Port) to write.
    :return: Nothing.
    """

    configfile = open(fullpath, 'w')
    # configfile = open(user_config_dir(APP_NAME), 'w')
    config = ConfigParser()
    config.add_section(SETTING_SECTION_NAME)
    config.set(SETTING_SECTION_NAME, SETTING_IP, connectioninfo[0])
    config.set(SETTING_SECTION_NAME, SETTING_PORT, connectioninfo[1])
    config.write(configfile)
    configfile.close()


def _get_config_file_path():
    """
    Assembles and returns the absolute path to the settings file.
    :return: Full path to settings file
    """

    # Obtain the platform if it hasn't been done already.
    platf = get_platform()

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


# Return terminal width as a string
def get_tty_width():
    return str(get_terminal_size()[0])


def configure():
    """
    First, tries to ping the phone using the exists
    :return: (IP, Port) that was recorded (either autoconnected or manual).
    """

    # Create the config file and update the path to it, if necessary
    global glob_config_file_path
    config_file_path = glob_config_file_path
    if not config_file_path:
        config_file_path = _get_config_file_path()
        glob_config_file_path = config_file_path

    config = ConfigParser()
    config.read(config_file_path)

    new_settings_file_required = False
    ip_addr = ''
    try:
        ip_addr = config[SETTING_SECTION_NAME][SETTING_IP]
        port = config[SETTING_SECTION_NAME][SETTING_PORT]
        print('Current phone info: ' + ip_addr + ', ' + port)
    except KeyError:
        print('Error parsing ' + config_file_path + '. Making a new one.')
        new_settings_file_required = True

    if new_settings_file_required:
        find_phones()
        quit()

        if os.path.isfile(config_file_path):
            os.remove(config_file_path)
        _write_config_file(config_file_path, autoconnect_info)

    return ip_addr
