from shexter import main, check_for_unread
from threading import Thread
from time import sleep

# doesn't do anything rn
def poll_unread() :
	while True:
		global unread
		unread = ''
		sleep(5)
		unread = check_for_unread()

#unthread = Thread(target=poll_unread, args='')
#unthread.daemon = True

#unthread.join(5)

while True:
	command = ''
	try:
		command = input('> ')
	except (EOFError, KeyboardInterrupt):
		# exit
		print()
		quit()
	#except:

	if len(command) == 0:
		continue
	elif command.lower() == "q" or command.lower() == "quit":
		quit()

	# TODO screws up send -s for more than one word. need to set nargs='+'
	args = command.split()
	
	main(args)
