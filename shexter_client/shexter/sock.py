import errno
import socket
from subprocess import Popen, PIPE, TimeoutExpired
from shexter.config import get_platform
''' This file performs network operations. The entry point is contact_server '''


DEFAULT_PORT = 5678
port = DEFAULT_PORT


def _dirty_windows_hostname_getter(hostname):
    """
    I hate that this is required, but gethostbyname doesn't work on Windows for some hosts (ie. phones).
    Maybe there is a better way, but this works.
    :param hostname: Hostname to try and resolve into an IP.
    :return: The IP address the hostname resolved to.
    """
    nslu_proc = Popen("nslookup " + hostname, stdout=PIPE, shell=True)
    try:
        output, error = nslu_proc.communicate(timeout=15)

        found = False
        ip_addr = ''
        if get_platform().startswith('win'):
            platf_sep = b'\r\n'
        else:
            platf_sep = b'\n'
        for line in output.split(sep=platf_sep):
            line = str(line)
            if found:
                line = line.replace("'", "").replace('\n', "")
                line = line.split("Address:", 1)[1].strip()
                ip_addr = line
                break
            elif hostname in line:
                # we want the line after this one
                found = True

    except TimeoutExpired:
        nslu_proc.kill()
        return None

    print('Resolved Phone IP to ' + ip_addr)
    return ip_addr


# Connect to the phone using the config's IP, and return the socket
def _connect(hostname):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(15)
    ip = None
    try:
        if get_platform().startswith('win'):
            # TODO which of these works on cygwin?
            ip = _dirty_windows_hostname_getter(hostname)
        else:
            ip = socket.gethostbyname(hostname)
        sock.connect((ip, port))
    except OSError as e:
        restart_msg = ('\n\nTry restarting the Shexter app, then run "shexter config" to change the hostname '
                       'to the one displayed on the app.\n'
                       'Also ensure your phone and computer are connected to the same network.')
        errorcode = e.errno
        print('Connection error: Hostname is ' + hostname + ', IP is ' + ip)
        if ip is None:
            print('Hostname is not correct.')
        elif errorcode == errno.ECONNREFUSED:
            print('Connection refused: Likely Shexter is not running on your phone.')
        elif errorcode == errno.ETIMEDOUT:
            print('Connection timeout: Likely your phone is not on the same network as your '
                  'computer or the hostname is not correct.')
        else:
            print('Unexpected error occurred: ')
        print(str(e))
        print(restart_msg)
        return None
    except (EOFError, KeyboardInterrupt):
        print('Connect cancelled')
        return None

    return sock

HEADER_LEN = 32
BUFFSIZE = 8192


# Read all bytes from the given socket, and return the decoded string
# Need to look into this further to support non-ascii
def _receive_all(sock):
    data = b''
    # receive the header to determine how long the message will be
    recvd = 0
    try:
        recvd = sock.recv(HEADER_LEN).decode()
        if not recvd:
            raise ConnectionResetError
    except ConnectionResetError:
        print('Connection forcibly reset; this means the server crashed. Restart the app '
              + 'on your phone and try again.')
        quit()
    except OSError as e:
        if e.errno == errno.ETIMEDOUT:
            print('Connection timeout: Server is frozen. Restart the app on your phone.')
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
    # decoded = data.decode('utf-8', 'strict')
    decoded = data.decode('ascii', 'ignore')
    # remove first newline
    decoded = decoded[1:]

    return decoded


# Helper for sending requests to the server
def contact_server(hostname, to_send):
    # print('sending:\n' + to_send)
    # print("...")
    sock = _connect(hostname)
    # print("Connected!")
    if sock is None:
        return None
    sock.send(to_send.encode())
    response = _receive_all(sock)
    sock.close()

    return response
