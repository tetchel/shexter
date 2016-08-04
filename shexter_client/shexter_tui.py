import curses
import atexit

#-----------Scrolly Pad-----------
class ScrollyPad:

    def __init__(self, h, w, start_y, start_x, end_y, end_x):
        self.height = h
        self.width = w
        self.y1 = start_y
        self.x1 = start_x
        self.y2 = end_y
        self.x2 = end_x
        self.current_line = 0
        self.pad = curses.newpad(self.height,self.width)

    def grow_from_bottom(self):
        for i in range(0,self.height):
            self.pad.addch(ord('\n'))

    # shorthands #
    def infinite_scroll(self):
        self.pad.scrollok(True)

    def addstr(self, string):
        try:
            self.pad.addstr(string)
        except curses.error:
            pass

    def addstr(self, string, attr):
        try:
            self.pad.addstr(string,attr)
        except curses.error:
            pass

    def addstr(self, y, x, string):
        try:
            self.pad.addstr(y,x,string)
        except curses.error:
            pass

    def addstr(self, y, x, string, attr):
        try:
            self.pad.addstr(y,x,string,attr)
        except curses.error:
            pass


#-----------Functions-------------

# gracefully close curses
def close_curses():
    curses.echo()
    curses.nocbreak()
    stdscr.keypad(0)
    curses.endwin()

# set the colors
def set_colors():
    curses.start_color()
    curses.use_default_colors()
    curses.init_pair(1, 3, -1)
    curses.init_pair(2, 6, -1)

def max_x():
    y,x = stdscr.getmaxyx()
    return x

def max_y():
    y,x = stdscr.getmaxyx()
    return y

# pad which holds contacts in order of most recent communication
# uses 
# TODO get recent convo data slapped into here
def create_recents():
    recents = curses.newpad(100, max_x())
    try:
        recents.addstr(0, 0, "3* Test Man")
        recents.addstr(1, 0, "2* First Last")
        recents.addstr(2, 0, "2* John Doe")
        recents.addstr(3, 0, " * Joe Doe")
        recents.addstr(4, 0, " * Mr. Not Visible Without Scroll")
        recents.addstr(5, 0, " * Sir")
        recents.addstr(6, 0, " * Buzz Lightyear")
    except curses.error:
        pass

    return recents

# generates a conversation header holdering the conversation name
#TODO auto width of "="; maybe a max width and a min number of ='s on the left side (like 2 or 4?)
def get_convo_header(name):
    return "==================" + name + "=================="

# pad which holds the current conversation
# uses a newline at the end of each message so that each message is fully displayed
# TODO get convo date slapped into here
# TODO put end of convo at bottom of screen (not maybe return a value to allow this from this function, for the refresh)
def create_convo():
    convo = curses.newpad(max_y()-recents_height-msg_height-1, max_x())
    convo.scrollok(True)
    try:
        convo.addstr(0,0, "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n")
        convo.addstr("T: sup foo\n", curses.color_pair(2))
        convo.addstr("N: nm bruh, jst chiln\n")
        convo.addstr("T: Bruh bruh asdlfkjasdfl;kjasdfjklahsdflkjahsdflkjhasdflkjhasdflkjhasdflkjhasdflkjhasdflkjhasdflkjhasdflkjahsdflkjahsdflkjahsdflkjahsdflkajhsdf end of long thing (hey it wrapped!!)\n", curses.color_pair(2))
        convo.addstr("N: This should block your wrapped message, T!! :P\n")
        convo.addstr("T: Ha! It didn't block my wrapped message because you learned that addstr keeps track of your cursor position!!\n", curses.color_pair(2))
        convo.addstr("N: I wish I never figured that out :'(\n")
        convo.addstr("T: Too late! My keyboard spam is already in our convo!\n", curses.color_pair(2))

    except curses.error:
        pass

    return convo

# create a pad ta static method doesn't know its class or instanceo hold a typed message
# TODO pad should grow as message is typed (*cough* noutrefesh doupdate *cough*)
def create_message():
    message = curses.newpad(100, max_x())
    try:
        message.addstr(0,0, "N: ", curses.A_BOLD)
    except curses.error:
        pass

    return message

# ----------------MAlN-------------------

# constants
recents_height = 3
msg_height = 0

# init curses
stdscr = curses.initscr()
#curses.noecho()
curses.cbreak()
stdscr.keypad(1)
atexit.register(close_curses)
set_colors()

# set up decorations
stdscr.addstr(recents_height+1, 0,get_convo_header("Time"), curses.color_pair(2)) 

# set up recents pad
recents = create_recents()

# set up conversation pad
convo = create_convo()

# set up messaging pad
message = create_message()

# set up curses interface (ie tests on stdscr)
try:
    stdscr.addstr(6, 0, str(max_y()) + " by " +  str(max_x()), curses.A_BOLD)
    stdscr.addstr(7, 0, "A hectic hello to Curses!!!", curses.A_STANDOUT)
    stdscr.addstr(8, 0, "sup from me", curses.color_pair(1))
    stdscr.addstr(9, 0, "sup from them", curses.color_pair(2))
except curses.error:
    pass

# refresh screens
stdscr.noutrefresh()
recents.noutrefresh(0,0, 0,0, recents_height,max_x())
convo.noutrefresh(0,0, recents_height+2,0, max_y()-msg_height-1,max_x())
message.noutrefresh(0,0, max_y()-1-msg_height,0, max_y()-1,max_x())
curses.doupdate()

stdscr.getch()
