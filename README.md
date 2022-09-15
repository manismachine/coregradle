# Coregradle
A library for app based exploitation

# How to add this library

 - Go to AndroidStudio Projects File -> Import Module
 - Navigate to this repo root /Coregradle and select finish
 - Add the following line to app level build.gradle of the target app
 	`implementation project(":Coregradle")`
 - After the import finished go to android manifest in Coregradle and adjust the minSdk, targetSdk as per requirement.
# How to use this library
 -  Go to any activity where the trigger is to be activated and register the workers by calling
	 `WorkerStore.registerWorkers(this)`
 - For Magic Message functionality goto the trigger point where you have access to the message content and the application context and then delegate to the appropriate worker by calling
	 `WorkerStore.delegate(context,magicMessage)`
