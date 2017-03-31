import errno
import socket
from subprocess import Popen, PIPE
from select import select
from sys import stdout
from ipaddress import IPv4Network, IPv4Address

from shexter.platform import get_platform, Platform

''' This file performs network operations. '''


PORT_MIN = 23456
PORT_MAX = 23457
DISCOVER_REQUEST = 'shexter-discover'
ENCODING = 'utf-8'


def _get_broadcast_addrs():
    """
    My least favourite function - Calls ipconfig (windows) or ifconfig (nix)
    and parses the output to get the broadcast address for each available interface.
    Alternatively, use netifaces, but it does not seem to work reliably, so for now we are using external programs.
    :return: List of broadcast addresses the host can use.
    """

    if get_platform() == Platform.WIN:
        with Popen('ipconfig', stdout=PIPE) as subproc:
            output, errors = subproc.communicate()

        # List to hold IP, Mask pairings (will have to calculate broadcast address later)
        output = output.decode('utf8')
        lines = output.splitlines()
        broadcast_addresses = []
        for index, line in enumerate(lines):
            # Parse out the IPv4 address
            if 'IPv4' in line:
                inet_addr = line.split(': ', 1)[1]
                # Netmask is the line after the address
                mask = lines[index+1].split(': ', 1)[1]

                # print(inet_addr + ' ' + mask)
                # Convert the address and mask combination to broadcast address
                network = IPv4Network((inet_addr, mask), False)
                broadcast_addresses.append(str(network.broadcast_address))
    else:
        with Popen('ifconfig', stdout=PIPE) as subproc:
            output, errors = subproc.communicate()

        output = output.decode('utf8')
        broadcast_addresses = []
        for line in output.splitlines():
            if 'Bcast' in line:
                bcast = line.split('Bcast:', 1)[1]
                # now contains everything after Bcast. Truncate at the first space to get the bcast address.
                bcast = bcast.split(' ', 1)[0]
                broadcast_addresses.append(bcast)

    print('Broadcast addresses: ' + str(broadcast_addresses))
    return broadcast_addresses


def find_phones():
    """
    This function broadcasts on the LAN to the Shexter ports, and looks for a reply from a phone.
    This does not work on Windows due to Windows not properly broadcasting on all interfaces.
    :return: (IP, Port) tuple representing the phone the user selects.
    """
    udpsock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udpsock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

    # IP, Port tuple representing the phone
    phone = None
    rejected_hosts = []

    print('Searching for phones, can take a few seconds...')

    for port in range(PORT_MIN, PORT_MAX+1):
        count = 0

        # Search more on the earlier ports which are much more likely to be the right one
        if port == PORT_MIN:
            tries = 6
        else:
            tries = 3

        print('Searching on port ' + str(port), end="")
        while not phone and count < tries:
            count += 1
            print('.', end='')
            stdout.flush()
            # Send on ALL the interfaces (required by Windows!)
            for broadcast_addr in _get_broadcast_addrs():
                discover_bytes = bytes(DISCOVER_REQUEST + '\n' + str(port), ENCODING)
                udpsock.bind(('', port))
                udpsock.sendto(discover_bytes, (broadcast_addr, port))

                ready = select([udpsock], [], [udpsock], 1)
                for readysock in ready[0]:
                    # Buffsize must match ConnectionInitThread.BUFFSIZE
                    data, other_host = udpsock.recvfrom(256)
                    data = data.decode(ENCODING)
                    if not data.startswith('shexter-confirm'):
                        print('received ugly response: ' + data)
                        continue

                    # Skip over rejected hosts
                    if not other_host[0] in rejected_hosts:
                        print('Got a response from ' + str(other_host))
                        # Print out the phone info received, and get the user to confirm
                        print('Phone info: ' + data)
                        confirm = input('Is this your phone? y/N: ')
                        if confirm.lower() == 'y':
                            phone = other_host
                        else:
                            rejected_hosts.append(other_host[0])
                if ready[2]:
                    print('There was an error selecting')

        print()

    if not phone:
        print('Couldn\'t find your phone.')

    return phone


def _connect_tcp(connectinfo):
    """
    Connect to the phone using the given IP, port pairing
    :return: The created TCP socket.
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(120)
    try:
        sock.connect(connectinfo)
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
                  'computer or the connection info ' + connectinfo + ' is not correct.' + restart_msg)
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

    # TODO get this working on Windows
    # if any retrieved message contains emoji
    decoded = data.decode(ENCODING, 'strict')
    # decoded = data.decode('ascii', 'ignore')
    # remove first newline
    decoded = decoded[1:]

    return decoded


# Helper for sending requests to the server
def contact_server(connectinfo, to_send):
    # print('sending:\n' + to_send)
    # print("...")
    sock = _connect_tcp(connectinfo)
    # print("Connected!")
    if sock is None:
        return None
    sock.send(bytes(to_send, ENCODING))
    response = _receive_all(sock)[1]
    sock.close()

    return response
