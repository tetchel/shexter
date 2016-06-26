#right now the whole script runs each invocation. 
#instead it should run in a loop or something so I can maintain network connection.

import sys
import socket
import errno

def connect():
	#hardcoded for now
	ip_addr = "192.168.1.101"
	port	= 5678

	#print("Preparing to connect...")
	sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	sock.settimeout(120)
	try:
		sock.connect((ip_addr, port))
		#print("Connect succeeded!")
	except OSError as e:
		errorcode = e.errno
		if errorcode == errno.ECONNREFUSED:
			print("Connection refused: "
				+ "Likely Shexter is not running on your phone.")
			quit()
		elif errorcode == errno.ETIMEDOUT:
			print("Connection timeout: Likely bad IP address.")	
			quit()
		else:
			raise e

	return sock;

def receiveAll() :
	recvd = sock.recv(8192)
	#TODO handle unicode characters better
	#try:
	decoded = recvd.decode('ascii', 'ignore')
	#except UnicodeDecodeError as e:
		#print("Trying UTF-8")
		#decoded = recvd.decode('unicode_escape')

	return decoded;

def log(msg) :
	f = open('shexterlog.txt', 'a')
	f.write(msg)

def displayHelp() :
	print("Recognized commands are:\n" + 
		"Send [contact_name]\nRead [contact_name]\nReadUnread")


# ----- Main script ----- #

if len(sys.argv) < 2:
	print("Shexter: You must specify a command")
	quit()
#there are other cases for not enough args

command = sys.argv[1].lower()

#contact first, last name are argv 2,3, so see exists first & last or just one name
if len(sys.argv) > 3:
	to_send = command + "\n" + sys.argv[2] + " " + sys.argv[3] + "\n"
elif len(sys.argv) > 2: 
	to_send = command + "\n" + sys.argv[2] + "\n"

if(command == "send"):
	#get msg, end with double newline
	print("Enter message (CTRL + C to cancel): ")
	try:
		msg_input = ""
		while True:
			line = input()
			if line.strip() == "":
				break
			msg_input += "%s\n" % line

		#add the message to to_send
		to_send = to_send + msg_input + "\n"
		
		log("Sending:\n" + to_send);

		sock = connect()
		sock.send(to_send.encode())
		print(receiveAll());
		sock.close()

	except EOFError as e:
		print("\nCancelled.")
		quit()
 
elif(command == "read"):
	sock = connect()
	sock.send(to_send.encode())
 
	print(receiveAll());

	sock.close()
elif(command == "help"):
	displayHelp()
else:
	print("Command \"{}\" not recognized :(".format(command))
	displayHelp()
