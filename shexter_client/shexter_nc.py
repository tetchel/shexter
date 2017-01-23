#!/usr/bin/env python3

import curses
import curses.textpad

import shexter

SEND_HEIGHT=3
CONVO_LEN=100

from datetime import datetime
def logprint(strng):
    with open('.shexternc.log', 'a') as logfile:
        print('[' + str(datetime.now()) + '] ' + strng, file=logfile)

def main(stdscr, args=None):
    stdscr.clear()
    curses.noecho()
    curses.cbreak()
    stdscr.keypad(True)

    y, x = stdscr.getmaxyx()
    shexter.set_tty_width(x)
    convopad=curses.newpad(CONVO_LEN, x)
    draw_convo(convopad, y, x)

    inputMode = False
    key = -1
    # Loop re-runs on any key input, mouse click, or resize event
    while True: 
        # redraw the display
        if key == KEY_RESIZE:
            y, x = stdscr.getmaxyx()
            shexter.set_tty_width(x)

        # Send a message
        if not inputMode and (key == ord('i') or key == ord('I') or key == curses.KEY_ENTER):
            inputMode = True
            draw_inputBox(stdscr, y, x)
            draw_convo(stdscr, y - SEND_HEIGHT)
        elif key == 27:
            inputMode = False 
     
        stdscr.refresh()

        # after redraw
        key = stdscr.getch()

        if key == ord('q'):
            break

# Returns a list of messages, split by newlines, with [0] containing the OLDEST message.
def read(contact_name, count):
    command = 'read {} -c {}'.format(contact_name, count)
    command = command.split()
    read_response = shexter.main(False, command)
    read_response = read_response.splitlines()
    read_response.reverse()
    return read_response

def draw_inputBox(scr, y, x):
    # Draw the separator
    sep = '=' * x
    scr.addstr(y - SEND_HEIGHT, 0, sep)
    textwin = curses.newwin(SEND_HEIGHT, x, y - SEND_HEIGHT, 0)
    textwin.leaveok(True)  

    inputBox = curses.textpad.Textbox(textwin)

    draw_convo(scr, y - SEND_HEIGHT)
    curses.setsyx(y - SEND_HEIGHT, 0)
    curses.echo()
    textwin.refresh()

def draw_convo(scr, y, x):
    read_response=read('myself', CONVO_LEN) 

    # Draw from the bottom up - draw the whole convo, only part will be displayed
    i = y - 1
    for line in read_response:
        scr.addstr(i, 0, line)
        i -= 1

    scr.refresh(1, 0, )

try:
    curses.wrapper(main)
except KeyboardInterrupt:
    pass
