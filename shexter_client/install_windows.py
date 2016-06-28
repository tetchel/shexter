import os
import shutil
import winreg
from subprocess import call

#PYTHON 3 ONLY RIGHT NOW.

print('WARNING: This script does edit your registry, so run only if you trust me '
	'or understand what this script does!')

INSTALL_DIR = os.getenv('APPDATA') + '\Shexter'
CLIENT_NAME = 'shexter.py'

print('Confirm install shexter into ' + INSTALL_DIR + '? y/N: ')
response = 'y'#input()

if(response != 'y'):
	print('Sorry to hear that!')
	quit()

try:
	shutil.rmtree(INSTALL_DIR)
except FileNotFoundError:
	pass

print('Installing in: ' + INSTALL_DIR)

os.mkdir(INSTALL_DIR)
shutil.copy('.\\' + CLIENT_NAME, INSTALL_DIR)

fullpath = INSTALL_DIR + '\\' + CLIENT_NAME

if os.path.isfile(fullpath):
	print('Copying client script successful.')
else:
	print(CLIENT_NAME + ' was not found in destination. Something went wrong :(')
	quit()

#edit PATH so you can easily shext from any directory!

print('Adding Shexter to User PATH.')

try:
	pathkey = winreg.OpenKey(winreg.HKEY_CURRENT_USER, 'Environment', 0, winreg.KEY_ALL_ACCESS)
	SUBKEY = 'PATH'
	currpath = winreg.QueryValueEx(pathkey, SUBKEY)[0] 
	if CLIENT_NAME not in currpath:
		winreg.SetValueEx(pathkey, SUBKEY, 0, winreg.REG_SZ, currpath + ';' + INSTALL_DIR)

	winreg.CloseKey(pathkey)
	print('Successfully added Shexter to PATH.')
	#HOWEVER, this is seemingly not enough to be able to type 'python shexter.py ...' in any directory.
	#Need some kind of shortcut or batch script which handles that part.
except FileNotFoundError:
	#create the key
	print('user PATH not found, should create it.')