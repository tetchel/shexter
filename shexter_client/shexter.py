#!/usr/bin/env python
# right now the whole script runs each invocation. 
# instead it should run in a loop by default, and have a flag to run in this mode -d --discrete

import sys
import os
import socket
import errno
import argparse
import configparser
from re import match
from shutil import get_terminal_size
from appdirs import user_config_dir

APP_NAME = 'Shexter'
AUTHOR_NAME = 'tetchel'

##### Functions #####
DEFAULT_PORT = 5678

port = DEFAULT_PORT
ip_addr = ''
def connect():
    print("Connecting...")
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(60)
    try:
        sock.connect((ip_addr, port))
        print("Connect succeeded!\n")
    except OSError as e:
        TRY_RESTART_MSG = ('\n\nTry restarting the Shexter app and editing ' + settings_fullpath
            + '\nwith the displayed IP, and make sure your phone and computer are connected to the'
            + ' same network.')
        errorcode = e.errno
        if errorcode == errno.ECONNREFUSED:
            print('Connection refused: Likely Shexter is not running on your phone.' + TRY_RESTART_MSG)
            quit()
        elif errorcode == errno.ETIMEDOUT:
            print('Connection timeout: Likely your phone is not on the same network as your ' +
                'computer or the IP address ' + ip_addr + ' is not correct.' + TRY_RESTART_MSG)
            quit()
        else:
            print('Unexpected error occurred: ')
            print(str(e))
            print(TRY_RESTART_MSG)
            quit()

    return sock;

HEADER_LEN = 32
BUFFSIZE = 8192
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
            print('Connection timeout: Server is frozen. Please try restarting Shexter on your phone.');
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

SETTINGS_FILE_NAME = APP_NAME.lower() + '.ini'
SETTING_SECTION_NAME = 'Settings'
SETTING_IP = 'IP Address'
SETTING_PORT = 'Port'

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
    config = configparser.ConfigParser()
    config.add_section(SETTING_SECTION_NAME)
    config.set(SETTING_SECTION_NAME, SETTING_IP, new_ip_addr)
    config.set(SETTING_SECTION_NAME, SETTING_PORT, str(DEFAULT_PORT))
    config.write(configfile)
    configfile.close()
    return new_ip_addr

def get_contact_name(args, required) :
    contact_name = ''
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
            quit()   

    return contact_name

SETPREF_NEEDED = "NEED-SETPREF"
COMMAND_SETPREF = "setpref"
COMMAND_SETPREF_LIST = COMMAND_SETPREF + "-list"
# Returns whether or not it needs to be re-called due to a setpref

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
    # exception occurs when sigint is sent
    except (EOFError, KeyboardInterrupt):
        return None

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
    return preferred

def contact_server(to_send, expectSetPrefPossible=True) :    
    sock = connect()
    sock.send(to_send.encode())
    response = receive_all(sock)
    sock.close()

    if(expectSetPrefPossible and response.startswith(SETPREF_NEEDED)):
        selected = handle_setpref_response(response)
        to_send = COMMAND_SETPREF + '\n' + contact_name + '\n' + str(selected) + ('\n' + 
                str(args.count) + '\n' + str(tty_w) + '\n')

        sock = connect()
        sock.send(to_send.encode())
        response = receive_all(sock);

    print(response)

# @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ #
# @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ #
# @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ #


##### Config Setup #####

cfgdir = user_config_dir(APP_NAME.lower(), AUTHOR_NAME)
if not os.path.exists(cfgdir):
    try:
        os.makedirs(cfgdir)
    except PermissionError:
        # means user has directory open in another program, therefore it exists, so that's ok
        pass

settings_fullpath = os.path.join(cfgdir, SETTINGS_FILE_NAME)

config = configparser.ConfigParser()
config.read(settings_fullpath)
try:
    # port = int(config[SETTING_SECTION_NAME[SETTING_PORT])     # server only uses 5678 for now.
    ip_addr = config[SETTING_SECTION_NAME][SETTING_IP]
    # validate IP
    socket.inet_aton(ip_addr)
except KeyError:
    print('Error parsing ' + settings_fullpath + '. Making a new one.')
    ip_addr = new_settings_file(settings_fullpath)
except OSError:
    print('Bad IP ' + ip_addr + ' found in ' + settings_fullpath + '. Making a new one.')
    ip_addr = new_settings_file(settings_fullpath)

##### Arg parser #####

DEFAULT_READ_COUNT = 30

parser = argparse.ArgumentParser(description='Send and read texts using your ' + 
        'Android phone from the command line.')
parser.add_argument('command', type=str,
        help='Possible commands: Send [Contact Name], Read [Contact Name], Unread, Contacts, ' + 
        'SetPref. Not case sensitive.')
parser.add_argument('contact_name', type=str, nargs='*', 
        help='Specify contact for SEND and READ commands.')
parser.add_argument('-c', '--count', default=DEFAULT_READ_COUNT, type=int,
        help='Specify how many messages to retrieve with the READ command.' + 
        str(DEFAULT_READ_COUNT) + ' by default.')
parser.add_argument('-m', '--multi', default=False, action='store_const',const=True,
        help='Keep entering new messages to SEND until cancel signal is given. ' + 
        'Useful for sending multiple texts in succession.')
parser.add_argument('-s', '--send', default=None, type=str,
        help='Allows sending messages as a one-liner. Put your message after the flag.')
#parser.add_argument('-n', '--number', default=False, action='store_const', const=True,
#        help='Specify a phone number instead of a contact name for applicable commands.')

# TODO settings command allow user to make a new settings file from the client py. Code already exists
# in new_settings_file

# TODO -l --last for SEND, print the last (few?) messages received that convo for context.
# could be easily implemented as a read request for n messages.
# Could also set a default --last value in the .ini

# TODO -s --send for specifying the message to send so user can send in one command.

args = parser.parse_args()

command = args.command.lower()

# If the user is doing a regular setpref (not one from read/send), they must start with a list
if(command == COMMAND_SETPREF):
    command = COMMAND_SETPREF_LIST

##### Validate and process args #####

# Command constants, must match those in the server code.
COMMAND_SEND = "send"
COMMAND_READ = "read"
COMMAND_UNRE = "unread"
COMMAND_GETCONTACTS = "contacts"
UNRE_CONTACT_FLAG = "-contact"
# TODO ring command - causes phone to ring regardless of volume

contact_name = ''

# Get the contact name if required

if(command == COMMAND_SEND or command == COMMAND_READ or command == COMMAND_SETPREF_LIST):
    contact_name = get_contact_name(args, True)
else:
    contact_name = get_contact_name(args, False)

# Build server request
to_send = command;
if(command == COMMAND_UNRE):
    # contact_name is optional for UNRE
    if(not contact_name):
        to_send += '\n'
    else:
        to_send += UNRE_CONTACT_FLAG + '\n' + contact_name + '\n'
else:
    to_send += '\n' + contact_name + '\n'

READ_COUNT_LIMIT = 5000
# For read commands, include the number of messages requested
if(command == COMMAND_READ or command == COMMAND_UNRE or command == COMMAND_SETPREF_LIST):
    if(args.count > READ_COUNT_LIMIT and command):
        print('Retrieving the maximum number of messages: ' + str(READ_COUNT_LIMIT))
        args.count = READ_COUNT_LIMIT

    # determine tty width
    tty_size = get_terminal_size()
    tty_w = tty_size[0]

    to_send += str(args.count) + '\n' + str(tty_w) + '\n'

elif(args.count != DEFAULT_READ_COUNT):
    print('Ignoring -c flag: only valid for READ or UNREAD command.') 

if(args.multi and command != COMMAND_SEND):
    print('Ignoring -m flag: only valid for SEND command.')

##### Contact the server #####

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
            print("\nSend cancelled.")
        elif(len(msg.strip()) == 0):            
            print("Not sent: message body was empty.")
        else:
            # add the message to to_send
            to_send += msg + '\n'
            contact_server(to_send, True)

elif(command == COMMAND_READ or command == COMMAND_SETPREF_LIST):
    contact_server(to_send, True)
elif(command == COMMAND_GETCONTACTS):
    contact_server(to_send, False)
else:
    print('Command \"{}\" not recognized.\n'.format(command))
    parser.print_help()
