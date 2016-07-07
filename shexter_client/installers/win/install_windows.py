import os
import sys
import shutil
import winreg
from appdirs import user_config_dir

APP_NAME = 'Shexter'
# client .py and dependencies are kept 2 directories above this script, so use path[0]
FILES_DIR = sys.path[0] + '\..\..\\'
LIB_DIR = FILES_DIR + 'lib\\'

INSTALL_DIR = user_config_dir('Shexter', 'tetchel')
BAT_NAME = APP_NAME.lower() + '.bat'
CLIENT_NAME = APP_NAME.lower() + '.py'
DEPENDENCIES = [ 'appdirs.py' ]		# Add new dependencies to the list and the lib directory

# add lib_dir to each dependency so installer can find
DEPENDENCIES = [ LIB_DIR + s for s in DEPENDENCIES ]

print('WARNING: This script does edit your registry, so run only if you trust me '
	'or understand what this script does!')

print('Confirm install shexter into ' + INSTALL_DIR + '? y/N: ')
response = 'y'#input().lower()

if(response != 'y'):
	print('Sorry to hear that!')
	quit()

try:
	#should change this. shouldn't delete settings every update.
	shutil.rmtree(INSTALL_DIR)
except FileNotFoundError:
	pass

#print('Installing in: ' + INSTALL_DIR)

# Copy the files

os.mkdir(INSTALL_DIR)
shutil.copy(FILES_DIR + CLIENT_NAME, INSTALL_DIR)
# use path[0] because .bat is in the same folder as this script
shutil.copy(sys.path[0] + '\\' + BAT_NAME, INSTALL_DIR)
for dep in DEPENDENCIES:
	shutil.copy(dep, INSTALL_DIR)

# Assert the files were copied

client_fullpath = INSTALL_DIR + '\\' + CLIENT_NAME
bat_fullpath = INSTALL_DIR + '\\' + BAT_NAME

if os.path.isfile(client_fullpath):
	print('Copying client script successful.')
else:
	print(client_fullpath + ' was not found. Something went wrong :(')
	quit()

if os.path.isfile(bat_fullpath):
	print('Copying client .bat successful.')
else:
	print(bat_fullpath + ' was not found. Something went wrong :(')
	quit()	

# Edit PATH so you can easily shext from any directory!

print('Adding Shexter to User PATH.')

try:
	pathkey = winreg.OpenKey(winreg.HKEY_CURRENT_USER, 'Environment', 0, winreg.KEY_ALL_ACCESS)
	SUBKEY = 'PATH'
	currpath = winreg.QueryValueEx(pathkey, SUBKEY)[0] 
	if INSTALL_DIR not in currpath:
		winreg.SetValueEx(pathkey, SUBKEY, 0, winreg.REG_SZ, currpath + ';' + INSTALL_DIR)

	winreg.CloseKey(pathkey)
	print('Successfully added ' + APP_NAME + ' to PATH.')
except FileNotFoundError:
	#create the key (will this ever happen?)
	print('user PATH not found, should create it.')

print('Install successful. You should now be able to run shexter from anywhere ' +
		'after opening a new shell.')