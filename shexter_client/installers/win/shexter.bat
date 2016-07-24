@echo off

IF "%1"=="-p" (
	python %~dp0shexter_persistant.py
) ELSE (
	python %~dp0shexter.py %*
)