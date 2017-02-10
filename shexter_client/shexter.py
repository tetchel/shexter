#!/usr/bin/env python3

import sys
import os
import socket
import errno
import argparse
from shutil import get_terminal_size
from configparser import ConfigParser

APP_NAME = 'Shexter'
AUTHOR_NAME = 'tetchel'

SETTINGS_FILE_NAME = APP_NAME.lower() + '.ini'
SETTING_SECTION_NAME = 'Settings'
SETTING_IP = 'IP Address'
SETTING_PORT = 'Port'

settings_fullpath_global=SETTINGS_FILE_NAME

global tty_width
def set_tty_width(width):
   global tty_width 
   tty_width = str(width) 
set_tty_width(get_terminal_size()[0])

#  Deletes the settings file and creates a new one, requiring an IP address from the user.
def new_settings_file(settings_fullpath) :
    # remove settings if it exists
    try:
        with open(settings_fullpath, 'r') as fin:
            old_settings = fin.read()
            if(old_settings):
                print('Your old settings:\n' + old_settings)
        os.remove(settings_fullpath)
    except OSError as e:
        # above will throw exception if settings didn't exist, which is fine
        pass

    new_ip_addr = ''
    validip = False
    # prompt user for an IP address until they give you one.
    while(not validip):
        try:
            new_ip_addr = input('Enter your IP Address from the Shexter app in dotted decimal '
                '(eg. 192.168.1.1): ')
        except (EOFError, KeyboardInterrupt):
            # user gave up
            print()
            quit()

        try:
            # validate ip
            socket.inet_aton(new_ip_addr)
            print('Setting your phone\'s IP to ' + new_ip_addr)
            validip = True
        except OSError:
            print('Invalid IP Address. Try again. (CTRL + C to give up)')

    #  configure the new settings and then write it into the file
    configfile = open(settings_fullpath, 'w')
    #configfile = open(user_config_dir(APP_NAME), 'w')
    config = ConfigParser()
    config.add_section(SETTING_SECTION_NAME)
    config.set(SETTING_SECTION_NAME, SETTING_IP, new_ip_addr)
    #config.set(SETTING_SECTION_NAME, SETTING_PORT, str(DEFAULT_PORT))
    config.write(configfile)
    configfile.close()
    return new_ip_addr

def get_config_dir() :
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
        #TODO default for windows?
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

    config_path = os.path.join(config_path, APP_NAME.lower())
    return config_path

# Try and load existing config file. Create a new config file if needed.
def configure() :
    cfgdir = get_config_dir()
    if not os.path.exists(cfgdir):
        try:
            os.makedirs(cfgdir)
        except PermissionError:
            # either user has directory open, or doesn't have w/x permission (on own config dir?)
            print('Could not access ' + cfgdir + ', please check the directory\'s permissions.')
            pass

    settings_fullpath = os.path.join(cfgdir, SETTINGS_FILE_NAME)

    config = ConfigParser()
    config.read(settings_fullpath)
    try:
        ip_addr = config[SETTING_SECTION_NAME][SETTING_IP]
        # validate IP
        socket.inet_aton(ip_addr)
    except KeyError:
        print('Error parsing ' + settings_fullpath + '. Making a new one.')
        ip_addr = new_settings_file(settings_fullpath)
    except OSError:
        print('Bad IP ' + ip_addr + ' found in ' + settings_fullpath + '. Making a new one.')
        ip_addr = new_settings_file(settings_fullpath)

    # Save the config file location so it can be output in case of connect failure
    global settings_fullpath_global
    settings_fullpath_global=settings_fullpath

    return ip_addr

##### Arguments and Settings #####

DEFAULT_READ_COUNT = 20
# Build help/usage, and the parser to determine options
def get_argparser():
    #description='Send and read texts using your ' + 'Android phone from the command line.'

    parser = argparse.ArgumentParser(prog='', usage='command [contact_name] [options]')
    parser.add_argument('command', type=str,
            help='Possible commands: Send $ContactName, Read $ContactName, Unread, Contacts,' +
            'SetPref $ContactName. Not case sensitive.')
    parser.add_argument('contact_name', type=str, nargs='*',
            help='Specify contact for SEND and READ commands.')
    parser.add_argument('-c', '--count', default=DEFAULT_READ_COUNT, type=int,
            help='Specify how many messages to retrieve with the READ command. ' +
            str(DEFAULT_READ_COUNT) + ' by default.')
    parser.add_argument('-m', '--multi', default=False, action='store_const',const=True,
            help='Keep entering new messages to SEND until cancel signal is given. ' +
            'Useful for sending multiple texts in succession.')
    parser.add_argument('-s', '--send', default=None, type=str,
            help='Allows sending messages as a one-liner. Put your message after the flag. ' +
            'Must be in quotes')
    parser.add_argument('-n', '--number', default=None, type=str,
            help='Specify a phone number instead of a contact name for applicable commands.')

    return parser

##### Networking #####
DEFAULT_PORT = 5678

port = DEFAULT_PORT
# Connect to the phone using the config's IP, and return the socket
def connect():
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(240)
    try:
        sock.connect((ip_addr, port))
    except OSError as e:
        TRY_RESTART_MSG = ('\n\nTry restarting the Shexter app and editing ' 
            + settings_fullpath_global
            + ' with the displayed IP, and make sure your phone and computer are connected to the'
            + ' same network.')
        errorcode = e.errno
        if errorcode == errno.ECONNREFUSED:
            print('Connection refused: Likely Shexter is not running on your phone.' 
                + TRY_RESTART_MSG)
            return None
        elif errorcode == errno.ETIMEDOUT:
            print('Connection timeout: Likely your phone is not on the same network as your ' +
                'computer or the IP address ' + ip_addr + ' is not correct.' + TRY_RESTART_MSG)
            return None
        else:
            print('Unexpected error occurred: ')
            print(str(e))
            print(TRY_RESTART_MSG)
            quit()
    except (EOFError, KeyboardInterrupt):
        print('Connect cancelled')
        return None

    return sock;

HEADER_LEN = 32
BUFFSIZE = 8192
# Read all bytes from the given socket, and return the decoded string
# Need to look into this further to support non-ascii
def receive_all(sock) :
    data = b''
    # receive the header to determine how long the message will be
    try:
        recvd = sock.recv(HEADER_LEN).decode()
        if not recvd:
            raise ConnectionResetError
    except ConnectionResetError:
        print('Connection forcibly reset; this means the server crashed. Restart ' + APP_NAME
            + ' on your phone and try again.')
        quit()
    except OSError as e:
        if e.errno == errno.ETIMEDOUT:
            print('Connection timeout: Server is frozen. Please try restarting ' + APP_NAME 
                + ' on your phone.');
            quit()
        else:
            raise

    header = int(recvd)
    recvd_len = 0
    while recvd_len < header:
            recvd = sock.recv(BUFFSIZE)
            data += recvd
            recvd_len += len(recvd)

    # TODO get this working on Windows. Sorry Allan but it breaks Windows read completely
    # if any retrieved message contains emoji
    #decoded = data.decode('utf-8', 'strict')
    decoded = data.decode('ascii', 'ignore')
    # remove first newline
    decoded = decoded[1:]

    return decoded

##### Processing input and building request #####

# Command constants, must match those in the server code.
COMMAND_SEND = "send"
COMMAND_READ = "read"
COMMAND_GETCONTACTS = "contacts"
COMMAND_UNRE = "unread"
# If the user is sending to/reading from a number rather than a contact name
NUMBER_FLAG = "-number"

SETPREF_NEEDED = "NEED-SETPREF"
COMMAND_SETPREF = "setpref"
COMMAND_SETPREF_LIST = COMMAND_SETPREF + "-list"
# TODO ring command - causes phone to ring regardless of volume

##### GET INPUT #####

# Force user to input contact name if one wasn't given in the args.
# Used for SEND, READ, and SETPREF commands.
def get_contact_name(args, required) :
    contact_name = ''
    # build the contact name (can span multiple words)
    for name in args.contact_name:
        contact_name += name + ' '

    contact_name = contact_name.strip()

    while(not contact_name and required):
        print('You must specify a Contact Name for Send and Read and SetPref commands. ' +
            'Enter one now:')
        try:
            contact_name = input('Enter a new contact name (CTRL + C to give up): ').strip()
        except (EOFError, KeyboardInterrupt):
            #gave up
            print()
            return None

    return contact_name

# Used for SEND command. Get user to input the message to send.
def get_message() :
    print('Enter message (Press Enter twice to send, CTRL + C to cancel): ')
    try:
        msg_input = ""
        # allows newline at start of message, just in case you want that
        # TODO allow backspacing of newlines.
        first = True
        while True:
            line = input()
            if line.strip() == "" and not first:
                    break
            first = False
            msg_input += "%s\n" % line

        return msg_input
    # exception occurs when sigint is sent, aka user cancelled
    except (EOFError, KeyboardInterrupt):
        return None

##### REQUEST CODE #####

# From list of command-line args, build the request to send.
# Get any missing info from the user (contact name)
def build_request(args) :
    command = args.command.lower()

    # If the user is doing a regular setpref (not one from read/send), they must start with a list
    # to determine which numbers can be chosen from.
    if(command == COMMAND_SETPREF):
        command = COMMAND_SETPREF_LIST

    contact_name = ''

    # Get the contact name if required, from the args or from the user if not provided.
    if(args.number is None and (command == COMMAND_SEND or command == COMMAND_READ or
            command == COMMAND_SETPREF_LIST)):

        contact_name = get_contact_name(args, True)
        if contact_name is None:
            return None, None
    else:
        contact_name = get_contact_name(args, False)

    # Build server request
    to_send = command;
    if(args.number is not None):
        to_send += '\n' + NUMBER_FLAG + '\n' + args.number + '\n'
    else:
        to_send += '\n' + contact_name + '\n'

    READ_COUNT_LIMIT = 5000
    # For read commands, include the number of messages requested
    if(command == COMMAND_READ):
        if(args.count > READ_COUNT_LIMIT and command):
            print('Retrieving the maximum number of messages: ' + str(READ_COUNT_LIMIT))
            args.count = READ_COUNT_LIMIT

        to_send += str(args.count) + '\n'

    if(command == COMMAND_UNRE or command == COMMAND_SETPREF_LIST or command == COMMAND_READ):
        to_send += tty_width + '\n\n'

    if(command != COMMAND_READ and args.count != DEFAULT_READ_COUNT):
        print('Ignoring -c flag: only valid for READ command.')

    if(command != COMMAND_SEND):
        if(args.multi):
            print('Ignoring -m flag: only valid for SEND command.')
        if(args.send is not None):
            print('Ignoring -s flag: only valid for SEND command.')

    return command, to_send

# If the specified contact has multiple numbers, handle the response by picking a number.
# response is the server's response containing the possible phone numbers
# Returns the server's response, which is either the setpref confirmation or the original
# command's response.
def handle_setpref_response(response) :
    response = response[len(SETPREF_NEEDED)+1:]
    numberOfNumbers = len(response.split('\n')) - 1
    if('Current:' in response):
        numberOfNumbers -= 1

    print(response)

    preferred = input('Select a number from the above, 1 to ' + str(numberOfNumbers) + ': ')

    while(not preferred.isdigit or int(preferred) > numberOfNumbers or int(preferred) < 1):
        print('Not a valid selection: must be integer between 1 and ' + str(numberOfNumbers))
        preferred = input('Select a number: ')
    
    preferred = int(preferred) - 1

    contact_name = response.split(' has', 1)[0]
    to_send = COMMAND_SETPREF + '\n' + contact_name + '\n' + str(preferred) + '\n\n'

    # Send the new pref to the phone. The phone will then perform the original request if needed.
    return contact_server(to_send)

# Helper for sending requests to the server
def contact_server(to_send) :
    #print('sending:\n' + to_send)
    #print("...")
    sock = connect()
    #print("Connected!")
    if sock is None:
        return ''
    sock.send(to_send.encode())
    response = receive_all(sock)
    sock.close()

    if(response.startswith(SETPREF_NEEDED)):
        response = handle_setpref_response(response)

    return response

# Perform all necessary operations for the given command.
# This means getting the send message if needed, contacting the server, 
# printing the response if needed.
# Returns the response from the phone.
def do_command(command, to_send, args):
    output = ''
    if(command == COMMAND_SEND):
        msg = ''
        if(args.send is not None):
            msg = args.send + '\n'

        first_send = True
        # send at least one message, but keep looping if -m was given
        while(first_send or args.multi):
            first_send = False
            # get msg if it wasn't given already
            if(msg == ''):
                msg = get_message()
            # see if user input message
            if(msg is None):
                output= 'Send cancelled.'
                break
            elif(len(msg.strip()) == 0):
                output='Not sent: message body was empty.'
            elif(len(msg.split('\n', 1)[0]) == 0):
                output='Not sent: first line cannot be blank (for now).'
            else:
                # add the message to to_send
                to_send_full = to_send + msg + '\n'
                response = contact_server(to_send_full)
                output=response
                msg = ''

    elif(command == COMMAND_READ or command == COMMAND_SETPREF_LIST 
        or command == COMMAND_GETCONTACTS):

        response = contact_server(to_send)
        output=response
    elif(command == COMMAND_UNRE):
        output=check_for_unread()
    elif(command == "help" or command == "h"):
        return ''
    else:
        print('Command \"{}\" not recognized.\nType "help" to see a list of commands.'
            .format(command))

    return output

NO_UNRE_RESPONSE = 'No unread messages.'
def check_for_unread() :
    to_send = COMMAND_UNRE + '\n' + tty_width + '\n\n'
    response = contact_server(to_send, False)
    if(response != NO_UNRE_RESPONSE):
        return response
    else:
        return ''

# Configure IP address once
ip_addr = configure()

# Main function to be called from -p mode. Pass the arguments directly to be parsed here.
def main(output, args_list) :
    parser = get_argparser()
    args = parser.parse_args(args_list)

    command, request = build_request(args)
    if(command is None or request is None):
        quit()

    result = do_command(command, request, args)

    if(output):
        if(result):
            print(result)
        else:
            parser.print_help()

    return result;

# for calling shexter directly
if(sys.argv[0] == __file__):
    main(True, sys.argv[1:])
