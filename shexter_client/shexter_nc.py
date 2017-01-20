#!/usr/bin/env python

import curses
import curses.textpad

def main(stdscr, args=None):
    stdscr = curses.initscr()
    #stdscr.clear()

    curses.addstr("greetings")
    stdscr.refresh()


try:
    curses.wrapper(main)
except KeyboardInterrupt:
    pass
