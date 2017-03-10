import errno
import socket
from select import select

''' This file performs network operations. The entry point is contact_server '''


PORT_MIN = 23456
PORT_MAX = 23461
DISCOVER_REQUEST = 'shexter-discover'
ENCODING = 'utf-8'


def _get_broadcast_addr():
    return '192.168.1.15'
    #return '192.168.1.255'


def find_phones():
    """

    :return: A list of (IP, Port) tuples identifying listening phones.
    """
    udpsock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udpsock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

    broadcast_addr = _get_broadcast_addr()
    print('Broadcast address is: ' + broadcast_addr)
    discover_bytes = bytes(DISCOVER_REQUEST, ENCODING)

    # IP, Port tuple representing the phone
    phone = None
    rejected_hosts = []
    print('Searching for phones...')

    for port in range(PORT_MIN, PORT_MAX+1):
        count = 0
        while not phone and count < 2:
            count += 1
            print('Searching on port ' + str(port))
            udpsock.sendto(discover_bytes, (broadcast_addr, port))

            ready = select([udpsock], [], [], 1)
            if ready[0]:
                # Buffsize must match ConnectionInitThread.BUFFSIZE
                data, other_host = udpsock.recvfrom(256)
                # Skip over rejected hosts
                if not other_host[0] in rejected_hosts:
                    print('Got a response from ' + str(other_host))
                    data = data.decode(ENCODING)
                    # Print out the phone info received, and get the user to confirm
                    print('Phone info: ' + data)
                    confirm = input('Is this your phone? y/N: ')
                    if confirm == 'y' or confirm == 'Y':
                        phone = other_host
                    else:
                        rejected_hosts.append(other_host[0])

    if not phone:
        # TODO allow manual IP config
        print('Couldn\'t find your phone')

    return phone


def _connect_tcp(ip_addr, port):
    """
    Connect to the phone using the given IP
    :param ip_addr:
    :param port:
    :return: The created TCP socket.
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(120)
    try:
        sock.connect((ip_addr, port))
    except OSError as e:
        restart_msg = ('\n\nTry restarting the Shexter app, then run "shexter config" to change the IP address '
                       'to the one displayed on the app.\n'
                       'Also ensure your phone and computer are connected to the same network.')
        errorcode = e.errno
        if errorcode == errno.ECONNREFUSED:
            print('Connection refused: Likely Shexter is not running on your phone.'
                  + restart_msg)
            return None
        elif errorcode == errno.ETIMEDOUT:
            print('Connection timeout: Likely your phone is not on the same network as your '
                  'computer or the IP address ' + ip_addr + ' is not correct.' + restart_msg)
            return None
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
BUFFSIZE = 4096


def _receive_all(sock):
    """
    Read all bytes from the given TCP socket.
    :param sock:
    :return: The decoded string, using ENCODING
    """
    data = b''
    # receive the header to determine how long the message will be
    header = 0
    other_host = ()
    try:
        header = sock.recvfrom(HEADER_LEN)
        header = header[0].decode(ENCODING, 'strict')
        if not header:
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

    header = int(header)
    recvd_len = 0
    while recvd_len < header:
        response = sock.recv(BUFFSIZE)
        data += response
        recvd_len += len(response)

    # TODO get this working on Windows. Sorry Allan but it breaks Windows read completely
    # if any retrieved message contains emoji
    decoded = data.decode(ENCODING, 'strict')
    # decoded = data.decode('ascii', 'ignore')
    # remove first newline
    decoded = decoded[1:]

    return decoded


# Helper for sending requests to the server
def contact_server(ip_addr, to_send):
    # print('sending:\n' + to_send)
    # print("...")
    sock = _connect_tcp(ip_addr)
    # print("Connected!")
    if sock is None:
        return None
    sock.send(bytes(to_send, ENCODING))
    response = _receive_all(sock)[1]
    sock.close()

    return response
