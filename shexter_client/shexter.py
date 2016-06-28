#right now the whole script runs each invocation. 
#instead it should run in a loop or something (maybe -p --persistant flag)

import sys
import socket
import errno
import argparse
import configparser

config = configparser.ConfigParser()
config.read('settings.ini')
PORT = int(config['Settings']['Port'])
ip_addr = config['Settings']['SlaveIP']

def connect():
        #print("Preparing to connect...")
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(60)
        try:
                sock.connect((ip_addr, PORT))
                #print("Connect succeeded!")
        except OSError as e:
                errorcode = e.errno
                if errorcode == errno.ECONNREFUSED:
                        print('Connection refused: '
                                + 'Likely Shexter is not running on your phone.')
                        quit()
                elif errorcode == errno.ETIMEDOUT:
                        print('Connection timeout: Likely bad IP address.')     
                        quit()
                else:
                        raise e

        return sock;

HEADER_LEN = 32
BUFFSIZE = 8192
def receive_all(sock) :
        data = b''
        #receive the header to determine how long the message will be
        #TODO handle empty headers when server has problems
        header = int(sock.recv(HEADER_LEN).decode())
        recvd_len = 0
        while recvd_len < header:
                recvd = sock.recv(BUFFSIZE)
                data += recvd   
                recvd_len += len(recvd)
        #TODO handle fancy characters better (maybe server-side)
        #try:
        decoded = data.decode('utf-8', 'strict')
        #decoded = data.decode('ascii', 'ignore')
        #except UnicodeDecodeError as e:
                #print("Trying UTF-8")
                #decoded = recvd.decode('unicode_escape')

        return decoded

 #Override useless python 2 input for pseudo backwards compatibility

try:
        input = raw_input
except NameError:
        pass

# ----- Main script ----- #

DEFAULT_READ_COUNT = 30

#Arg parser
parser = argparse.ArgumentParser(description='Send and read texts using your ' + 
        'Android phone from the command line.')
parser.add_argument('command', type=str,
        help='Possible commands: Send [Contact Name], Read [Contact Name], Unread. ' +
        'Not case sensitive.')
parser.add_argument('contact_name', type=str, nargs='*', 
        help='Specify contact for SEND and READ commands.')
parser.add_argument('-c', '--count', default=DEFAULT_READ_COUNT, type=int,
        help='Specify how many messages to retrieve with the READ command.')
parser.add_argument('-m', '--multi', default=False, action='store_const',const=True,
        help='Keep entering new messages to SEND until cancel signal is given. ' + 
        'Useful for sending multiple texts in succession.')
#TODO -n --number flag, allowing sending/reading for numbers instead of contacts.

args = parser.parse_args()
#print(args)

command = args.command.lower()

#Command names
COMMAND_SEND = "send"
COMMAND_READ = "read"
COMMAND_UNRE = "unread"

contact_name = ''

if(command == COMMAND_SEND or command == COMMAND_READ):
        #require a contact name
        contact_name_len = len(args.contact_name)
        if(contact_name_len == 0):
                print('You must specify a Contact Name for Send and Read commands.')
                quit()

        for name in args.contact_name:
                contact_name += name + ' '

#remove extra whitespace
contact_name = contact_name.strip()

#Build server request
to_send = command + '\n' + contact_name + '\n'

READ_COUNT_LIMIT = 5000
#For read commands, include the number of messages requested
if(command == COMMAND_READ):
        if(args.count > READ_COUNT_LIMIT):
                print('Retrieving the maximum number of messages: ' + str(READ_COUNT_LIMIT))
                args.count = READ_COUNT_LIMIT

        to_send += str(args.count) + '\n'
elif(args.count != DEFAULT_READ_COUNT):
        print('Ignoring -c flag: only valid for READ command.')

if(args.multi and command != COMMAND_SEND):
        print('Ignoring -m flag: only valid for SEND command.')

# ----- Contact the server ----- #
if(command == COMMAND_SEND):
        first_send = True
        #send at least one message, but keep looping if -m was given
        while(first_send or args.multi):
                first_send = False
                #get msg, end with double newline
                print('Enter message (Press Enter twice to send, CTRL + C to cancel): ')
                try:
                        msg_input = ""
                        #allows newline at start of message, just in case you want that
                        first = True
                        while True:
                                line = input()
                                if line.strip() == "" and not first:
                                        break
                                first = False
                                msg_input += "%s\n" % line

                        #if not empty
                        if(len(msg_input.strip()) != 0):
                                #add the message to to_send
                                to_send_full = to_send + msg_input + "\n"

                                sock = connect()
                                sock.send(to_send_full.encode())

                                print(receive_all(sock))
                                sock.close()
                        else:
                                print("Not sent: message body was empty.")

                #exception occurs when sigint is sent
                except (EOFError, KeyboardInterrupt):
                        print('\nSend cancelled.')
                        quit()
 
elif(command == COMMAND_READ):
        sock = connect()
        sock.send(to_send.encode())
 
        print(receive_all(sock));

        sock.close()
elif(command == COMMAND_UNRE):
        print('Sorry- Unread not implemented yet. Coming soon!')
else:
        print('Command \"{}\" not recognized.\n'.format(command))
        parser.print_help()
