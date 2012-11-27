Currently it's just a simple image rotating and scaling within it's view. 
But you could build up that image dynamically or even apply the same logic to the draw function of a container, but then I'm not sure if clicks would register correctly.

Features:

- follow finger
- fling wheel
- come to standstill at center of a segment.
- pinch to zoom zooms in on cut-off to a maximum of 2.5 times the window-height.
- The wheel is always centered horizontally.
- The wheel is centered vertically if smaller than the screen, otherwise the top is the top of the screen.

Method:

- On finger-down the angle on the wheel is registered
- On finger-move the new angle is determined and the wheel rotated the same amount of degrees.
- On finger-up the angular speed between the last 2 events is determined and given to a runnable
  - the runnable calls itself every 14ms, moves the wheel based on elapsed time and velocity and then decreases the velocity.
  - As the speed drop below a certain value, the speed is adapted to the distance to the closest segment-center so it moves there. 
  - If the distance to the closest segment-center is small enough it stops.
- Touch events are also passed to a ScaleGestureDetector which recognises pinching and saves the new scale. 

The showWheel creates a matrix that first does in order:

- The rotation around the center of the original image.
- The scaling according to the scale factor. 
- The translation to get the scaled image in the right position.
  