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
        self.hidden = False

    def refresh(self):
        if not self.hidden:
            self.pad.noutrefresh(self.current_line,0, self.y1,self.x1, self.y2,self.x2)

    def screen_height(self):
        return self.y2 - self.y1 + 1

    # scrolly stuff
    def scroll_up(self):
        if self.current_line > self.top():
            self.current_line -= 1

    def scroll_down(self):
        if self.current_line < self.bottom():
            self.current_line += 1

    def top(self):
        return 0

    def bottom(self):
        return self.height - self.screen_height()

    def cursor_to_bottom(self):
        for i in range(0,self.height):
            try:
                self.pad.addch(ord('\n'))
            except curses.error:
                pass

    def infinite_scroll(self):
        self.pad.scrollok(True)
        self.cursor_to_bottom()
        self.current_line = self.bottom()

    # shorthand 
    def addstr(self, a1, a2=None, a3=None, a4=None):
        if a2==None:
            try:
                self.pad.addstr(a1)
            except curses.error:
                pass
        elif a3==None:
            try:
                self.pad.addstr(a1,a2)
            except curses.error:
                pass
        elif a4==None:
            try:
                self.pad.addstr(a1,a2,a3)
            except curses.error:
                pass
        else:
            try:
                self.pad.addstr(a1,a2,a3,a4)
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
    # init
    recents = ScrollyPad(100, max_x(), 0,0, 3,max_x())
    
    # set text
    recents.addstr(0, 0, "3* Test Man")
    recents.addstr(1, 0, "2* First Last")
    recents.addstr(2, 0, "2* John Doe")
    recents.addstr(3, 0, " * Joe Doe")
    recents.addstr(4, 0, " * Mr. Not Visible Without Scroll")
    recents.addstr(5, 0, " * Sir")
    recents.addstr(6, 0, " * Buzz Lightyear")

    return recents

# generates a conversation header holdering the conversation name
#TODO auto width of "="; maybe a max width and a min number of ='s on the left side (like 2 or 4?)
def get_convo_header(name):
    return "==================" + name + "=================="

# pad which holds the current conversation
# uses a newline at the end of each message so that each message is fully displayed
# TODO get convo data slapped into here
def create_convo():
    # init
    convo = ScrollyPad(1000,max_x(), recents.screen_height()+1,0, max_y()-message.screen_height(),max_x()) #TODO +1 is for the decorations. Maybe integrate the decorations into convo 
    convo.infinite_scroll()

    # set text
    convo.addstr("T: sup foo\n", curses.color_pair(2))
    convo.addstr("N: nm bruh, jst chiln\n")
    convo.addstr("T: Bruh bruh asdlfkjasdfl;kjasdfjklahsdflkjahsdflkjhasdflkjhasdflkjhasdflkjhasdflkjhasdflkjhasdflkjhasdflkjahsdflkjahsdflkjahsdflkjahsdflkajhsdf end of long thing (hey it wrapped!!)\n", curses.color_pair(2))
    convo.addstr("N: This should block your wrapped message, T!! :P\n")
    convo.addstr("T: Ha! It didn't block my wrapped message because you learned that addstr keeps track of your cursor position!!\n", curses.color_pair(2))
    convo.addstr("N: I wish I never figured that out :'(\n")
    convo.addstr("T: Too late! My keyboard spam is already in our convo!\n", curses.color_pair(2))

    return convo

# create a pad ta static method doesn't know its class or instanceo hold a typed message
# TODO pad should grow as message is typed (*cough* noutrefesh doupdate *cough*)
def create_message():
    # init
    message = ScrollyPad(100, max_x(), max_y()-1,0, max_y()-1,max_x()) 
    message.infinite_scroll()

    # set text
    message.addstr("N: ", curses.A_BOLD)

    return message

# ----------------MAlN-------------------

# constants
msg_height = 0

# init curses
stdscr = curses.initscr()
#curses.noecho()
curses.cbreak()
stdscr.keypad(1)
#atexit.register(close_curses)
set_colors()

# create pads
recents = create_recents()
message = create_message()
convo = create_convo()
stdscr.addstr(recents.screen_height(), 0,get_convo_header("Time"), curses.color_pair(2)) 

# set up curses interface (ie tests on stdscr) stdscr.addstr(7, 0, "A hectic hello to Curses!!!", curses.A_STANDOUT)

# refresh screens
stdscr.noutrefresh()
recents.refresh()
convo.refresh()
message.refresh()
curses.doupdate()

stdscr.getch()

close_curses()
