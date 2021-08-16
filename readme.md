# Color Recognition

This class reads an images from a flir Camera and check if a type of color is present,this routine divide the frame in 9 parts and uses only three image from left and right and eliminate the center.
When a part contains the colour it put into a folder on PC.
I found the example in : http://softwareservices.flir.com/Spinnaker/latest/examples.html

## Run
Run the main with this settings -Djava.library.path=jnilibs/
```

## Lib

- Spinnaker -> https://www.flir.com/products/spinnaker-sdk/
- OpenCV -> https://opencv.org/

