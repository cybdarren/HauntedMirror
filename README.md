HauntedMirror
Halloween HauntedMirror for an Android tablet using CameraX for motion detection and openCV.

I've seen all the example HauntedMirrors that use a PC or tablet inside a picture frame with reflective film. These are all quite neat but use a video on a loop to display a ghostly apparition on the display. 

I wanted to add a feature that only triggered the video when the mirror detects motion in front of it. The natural method seemed to be to use the front facing camera and a little bit of openCV code for detection.

The code creates a CameraX preview window and an ImageAnalyzer. The previewView is in fact a tiny 1x1 pixel window on the screen as you must have it to convince Android to work properly. 
The ImageAnlalyzer looks for motion in successive images and when it is above a threshold value it triggers playback of a video.
The videos I used come from AtmosFX so are removed from the project, you will have to get your own video and put it in the src/main/res/raw directory and edit the code to reflect the correct name.
There is also a configuration mode, just tap the screen, with a slider that sets the motion threshold value as well as showing the detected motion map.

