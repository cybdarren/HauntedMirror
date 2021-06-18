# HauntedMirror
## Background
Halloween Haunted Mirror for an Android tablet using CameraX for motion detection and openCV.

I've seen the example HauntedMirrors that use a PC or tablet inside a picture frame with reflective film. These are all quite neat but mostly use a video on a loop to display a ghostly apparition on the display. There are also a few that seem to trigger from a PIR motion sensor connected to an external Arduino that then commuicates with the PC/tablet using bluetooth or USB.

I wanted to add a feature that only triggered the video when the mirror detects motion in front of it. The natural method seemed to be to use the front facing camera and a little bit of image processing code for detection. There was a question mark over whether this would work at night or behind the half silvered mirror but time will tell. I also thought that kids coming around with flashlights should provide ample motion to trigger it.

## Overview
The code creates a CameraX preview window and an ImageAnalyzer. The previewView is in fact a tiny 1x1 pixel window on the screen as you must have it to convince Android to work properly. 
The ImageAnalyzer looks for motion in successive images and when it is above a threshold value it triggers playback of a video.
The videos I used come from AtmosFX so are removed from the project, you will have to get your own video and put it in the src/main/res/raw directory and edit the code to reflect the correct name.
There is also a configuration mode, just tap the screen, with a slider that sets the motion threshold value as well as showing the detected motion map.

## Building the code
The code was developed in Android Studio v4.2.1. Download the code from the github repository and open the project. It may complain about missing dependencies (in particular openCV) so follow the next steps.
Before you can build it you will need to setup the openCV dependency. I used the OpenCV for Android SDK v4.5.2. Download this from [openCV](https://opencv.org/releases). Then unzip it to somewhere convenient.

Once downloaded you need to add openCV to your project. In Android Studio go to File->New->Import Module. Navigate to the OpenCV-android-sdk/sdk/ directory (there should be a build.gradle file there). It will want to name the module :sdk, change this to :openCV and click finish. The import should complete and you will now have openCV added to the project. 
Just for reference in the build.gradle(:app) file there is a dependency on the openCV sdk, make sure that something like the following is in your file and that the openCV module can be found correctly.
```language
dependencies {

    implementation 'androidx.appcompat:appcompat:1.3.0'
    implementation 'com.google.android.material:material:1.3.0'
    implementation project(path: ':openCV')
```


You will need a video to play. As I mentioned I used one from [AtmosFX](https://atmosfx.com/collections/halloween). You can use any of the standard formats supported by Android, mp4 works well. It needs placing in the HauntedMirror/app/src/main/res/raw folder. Provided it is not too big it will bundled with the application. The following code in FullscreenActivity.java needs editing to reflect the correct name of the resource.
```language
        Uri uriPath = Uri.parse("android.resource://com.dwenn.hauntedmirror/" + R.raw.pg1);
        videoView.setVideoPath(uriPath.toString());
```
The R.raw.pg1 will be the logical name of the mp4 file that you added to the resource folder.

With all of the dependencies and code in place you should be able to build the project and download it to your tablet. I am not going to cover how to do this since there are plenty of resources on the web, also you will need a little bit of knowledge to put the tablet into Developer mode anyway so that you can download the code.
Whilst I mention developer mode on the tablet, don't forget to turn off the backlight dimming and suspend functions. Otherwise your haunted mirror will go to sleep after a few minutes and not recognise people in front of it. One other thing I did was to use a USB power pack for additional battery life. The tablet thinks it is plugged into a wall socket and you can tell it never to go to sleep.

## Constructing the mirror

