package com.weplus.raillandscape

import com.weplus.raillandscape.Utils.findImageStatusNameByValue
import com.weplus.raillandscape.Utils.printf
import org.bytedeco.javacpp.*
import org.bytedeco.javacpp.Spinnaker_C.*
import org.bytedeco.javacpp.Spinnaker_C._spinError
import org.bytedeco.javacpp.Spinnaker_C._spinImageFileFormat
import org.bytedeco.javacpp.Spinnaker_C._spinNodeType
import org.bytedeco.javacpp.Spinnaker_C._spinPixelFormatEnums
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.system.exitProcess
import org.opencv.core.CvType
import java.awt.Toolkit
import java.nio.ByteBuffer
import javax.swing.Spring.height

object SpinnakerReader {

    private const val MAX_BUFF_LEN = 1024

    /**
     * Example entry point; please see Enumeration_C example for more in-depth
     * comments on preparing and cleaning up the system.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
        var errReturn = 0
        var err: _spinError

        // Since this application saves images in the current folder
        // we must ensure that we have permission to write to this folder.
        // If we do not have permission, fail right away.
        if (!File(".").canWrite()) {
            println("Failed to create file in current folder.  Please check permissions.")
            exitProcess(-1)
        }

        // Retrieve singleton reference to system object
        val hSystem = spinSystem()
        err = spinSystemGetInstance(hSystem)
        Utils.exitOnError(err, "Unable to retrieve system instance.")

        // Retrieve list of cameras from the system
        val hCameraList = spinCameraList()
        err = spinCameraListCreateEmpty(hCameraList)
        Utils.exitOnError(err, "Unable to create camera list.")

        err = spinSystemGetCameras(hSystem, hCameraList)
        Utils.exitOnError(err, "Unable to retrieve camera list.")

        // Retrieve number of cameras
        val numCameras = SizeTPointer(1)
        err = spinCameraListGetSize(hCameraList, numCameras)
        Utils.exitOnError(err, "Unable to retrieve number of cameras.")
        println("Number of cameras detected: " + numCameras.get() + "\n")

        // Finish if there are no cameras
        if (numCameras.get() == 0L) {
            // Clear and destroy camera list before releasing system
            err = spinCameraListClear(hCameraList)
            Utils.exitOnError(err, "Unable to clear camera list.")

            err = spinCameraListDestroy(hCameraList)
            Utils.exitOnError(err, "Unable to destroy camera list.")

            // Release system
            err = spinSystemReleaseInstance(hSystem)
            Utils.exitOnError(err, "Unable to release system instance.")

            println("Not enough cameras!")
            exitProcess(-1)
        }

        // Run example on each camera


        // Select camera
        val hCamera = spinCamera()
        err = spinCameraListGet(hCameraList, 0, hCamera)

        if (!Utils.printOnError(err, "Unable to retrieve camera from list.")) {

            //
            // Run example
            //
            val ret = runSingleCamera(hCamera)
            if (ret.value != _spinError.SPINNAKER_ERR_SUCCESS.value) {
                errReturn = -1
            }
            Utils.printOnError(err, "RunSingleCamera")
        }

        // Release camera
        err = spinCameraRelease(hCamera)
        Utils.printOnError(err, "Error releasing camera.")

        // Clear and destroy camera list before releasing system
        err = spinCameraListClear(hCameraList)
        Utils.exitOnError(err, "Unable to clear camera list.")

        err = spinCameraListDestroy(hCameraList)
        Utils.exitOnError(err, "Unable to destroy camera list.")

        // Release system
        err = spinSystemReleaseInstance(hSystem)
        Utils.exitOnError(err, "Unable to release system instance.")

        println("\nDone.")

        exitProcess(errReturn)
    }


    /**
     * This function prints the device information of the camera from the transport
     * layer; please see NodeMapInfo_C example for more in-depth comments on
     * printing device information from the nodemap.
     */
    private fun printDeviceInfo(hNodeMap: spinNodeMapHandle): _spinError {
        var err: _spinError
        println("\n*** DEVICE INFORMATION ***\n\n")
        // Retrieve device inforion category node
        val hDeviceInformation = spinNodeHandle()
        err = spinNodeMapGetNode(hNodeMap, BytePointer("DeviceInformation"), hDeviceInformation)
        Utils.printOnError(err, "Unable to retrieve node.")

        // Retrieve number of nodes within device information node
        val numFeatures = SizeTPointer(1)
        if (Utils.isAvaiable(hDeviceInformation) && Utils.isReadable(hDeviceInformation)) {
            err = spinCategoryGetNumFeatures(hDeviceInformation, numFeatures)
            Utils.printOnError(err, "Unable to retrieve number of nodes.")
        } else {
            Utils.printRetrieveNodeFailure("node", "DeviceInformation")
            return _spinError.SPINNAKER_ERR_ACCESS_DENIED
        }

        // Iterate through nodes and print information
        for (i in 0 until numFeatures.get()) {
            val hFeatureNode = spinNodeHandle()
            err = spinCategoryGetFeatureByIndex(hDeviceInformation, i, hFeatureNode)
            Utils.printOnError(err, "Unable to retrieve node.")

            // get feature node name
            val featureName = BytePointer(MAX_BUFF_LEN.toLong())
            val lenFeatureName = SizeTPointer(1)
            lenFeatureName.put(MAX_BUFF_LEN.toLong())
            err = spinNodeGetName(hFeatureNode, featureName, lenFeatureName)
            if (Utils.printOnError(err, "Error retrieving node name.")) {
                featureName.putString("Unknown name")
            }

            val featureType = intArrayOf(_spinNodeType.UnknownNode.value)
            if (Utils.isAvaiable(hFeatureNode) && Utils.isReadable(hFeatureNode)) {
                err = spinNodeGetType(hFeatureNode, featureType)
                if (Utils.printOnError(err, "Unable to retrieve node type.")) {
                    continue
                }
            } else {
                println((featureName).toString() + ": Node not readable")
                continue
            }

            val featureValue = BytePointer(MAX_BUFF_LEN.toLong())
            val lenFeatureValue = SizeTPointer(1)
            lenFeatureValue.put(MAX_BUFF_LEN.toLong())
            err = spinNodeToString(hFeatureNode, featureValue, lenFeatureValue)
            if (Utils.printOnError(err, "spinNodeToString")) {
                featureValue.putString("Unknown value")
            }
            println(featureName.string.trim { it <= ' ' } + ": " + featureValue.string.trim { it <= ' ' } + ".")
        }
        println()
        return err
    }

    //
    // This function acquires and saves 10 images from a device; please see
    // Acquisition_C example for more in-depth comments on the acquisition of
    // images.
    private fun acquireImages(
        hCam: spinCamera,
        hNodeMap: spinNodeMapHandle,
        hNodeMapTLDevice: spinNodeMapHandle
    ): _spinError {
        var err: _spinError

        printf("\n*** IMAGE ACQUISITION ***\n\n")

        // Set acquisition mode to continuous
        val hAcquisitionMode = spinNodeHandle()
        val hAcquisitionModeContinuous = spinNodeHandle()
        val acquisitionModeContinuous = LongPointer(1)
        acquisitionModeContinuous.put(0)


        err = spinNodeMapGetNode(hNodeMap, BytePointer("AcquisitionMode"), hAcquisitionMode)
        if (err.value != _spinError.SPINNAKER_ERR_SUCCESS.value) {
            printf("Unable to set acquisition mode to continuous (node retrieval). Aborting with error %d...\n\n", err)
            return _spinError.SPINNAKER_ERR_ACCESS_DENIED
        }

        if (!Utils.isAvaiable(hAcquisitionMode) || !Utils.isWritable(hAcquisitionMode)) {
            printf("Unable to set acquisition mode to continuous (node retrieval). Aborting with error %d...\n\n", err)
            return _spinError.SPINNAKER_ERR_ACCESS_DENIED
        }

        err = spinEnumerationGetEntryByName(hAcquisitionMode, BytePointer("Continuous"), hAcquisitionModeContinuous)
        if (err.value != _spinError.SPINNAKER_ERR_SUCCESS.value) {
            printf(
                "Unable to set acquisition mode to continuous (entry 'continuous' retrieval). Aborting with error %d...\n\n",
                err
            )
            return _spinError.SPINNAKER_ERR_ACCESS_DENIED
        }

        if (!Utils.isAvaiable(hAcquisitionModeContinuous) || !Utils.isReadable(hAcquisitionModeContinuous)) {
            printf(
                "Unable to set acquisition mode to continuous (entry 'continuous' retrieval). Aborting with error %d...\n\n",
                err
            )
            return _spinError.SPINNAKER_ERR_ACCESS_DENIED
        }

        err = spinEnumerationEntryGetIntValue(hAcquisitionModeContinuous, acquisitionModeContinuous)
        if (err.value != _spinError.SPINNAKER_ERR_SUCCESS.value) {
            printf(
                "Unable to set acquisition mode to continuous (entry int value retrieval). Aborting with error %d...\n\n",
                err
            )
            return _spinError.SPINNAKER_ERR_ACCESS_DENIED
        }

        err = spinEnumerationSetIntValue(hAcquisitionMode, acquisitionModeContinuous.get())
        if (err.value != _spinError.SPINNAKER_ERR_SUCCESS.value) {
            printf(
                "Unable to set acquisition mode to continuous (entry int value setting). Aborting with error %d...\n\n",
                err
            )
            return _spinError.SPINNAKER_ERR_ACCESS_DENIED
        }

        printf("Acquisition mode set to continuous...\n")

        // Begin acquiring images
        err = spinCameraBeginAcquisition(hCam)
        if (err.value != _spinError.SPINNAKER_ERR_SUCCESS.value) {
            printf("Unable to begin image acquisition. Aborting with error %d...\n\n", err)
            return _spinError.SPINNAKER_ERR_ACCESS_DENIED
        }

        printf("Acquiring images...\n")

        // Retrieve device serial number for filename
        val hDeviceSerialNumber = spinNodeHandle()
        val deviceSerialNumber = BytePointer(MAX_BUFF_LEN.toLong())
        val lenDeviceSerialNumber = SizeTPointer(1)
        lenDeviceSerialNumber.put(MAX_BUFF_LEN.toLong())

        err = spinNodeMapGetNode(hNodeMapTLDevice, BytePointer("DeviceSerialNumber"), hDeviceSerialNumber)
        if (Utils.printOnError(err, "")) {
            deviceSerialNumber.putString("")
            lenDeviceSerialNumber.put(0)
        } else {
            if (Utils.isAvaiable(hDeviceSerialNumber) && Utils.isReadable(hDeviceSerialNumber)) {
                err = spinStringGetValue(hDeviceSerialNumber, deviceSerialNumber, lenDeviceSerialNumber)
                if (Utils.printOnError(err, "")) {
                    deviceSerialNumber.putString("")
                    lenDeviceSerialNumber.put(0)
                }
            } else {
                deviceSerialNumber.putString("")
                lenDeviceSerialNumber.put(0)
                Utils.printRetrieveNodeFailure("node", "DeviceSerialNumber")
            }
            println("Device serial number retrieved as " + deviceSerialNumber.string.trim { it <= ' ' } + "...")
        }
        println()

        // Retrieve, convert, and save images
        var imageCnt = 0
        while (true) {

            // Retrieve next received image
            val hResultImage = spinImage()

            err = spinCameraGetNextImage(hCam, hResultImage)
            if (Utils.printOnError(err, "Unable to get next image. Non-fatal error.")) {
                continue
            }

            // Ensure image completion
            val isIncomplete = BytePointer(1.toLong())
            var hasFailed = false

            err = spinImageIsIncomplete(hResultImage, isIncomplete)
            if (Utils.printOnError(err, "Unable to determine image completion. Non-fatal error.")) {
                hasFailed = true
            }

            // Check image for completion
            if (isIncomplete.bool) {
                val imageStatus = IntPointer(1) //_spinImageStatus.IMAGE_NO_ERROR;
                err = spinImageGetStatus(hResultImage, imageStatus)
                if (!Utils.printOnError(
                        err,
                        "Unable to retrieve image status. Non-fatal error. " + findImageStatusNameByValue(imageStatus.get())
                    )
                ) {
                    println(
                        "Image incomplete with image status " + findImageStatusNameByValue(imageStatus.get()) +
                                "..."
                    )
                }
                hasFailed = true
            }

            // Release incomplete or failed image
            if (hasFailed) {
                err = spinImageRelease(hResultImage)
                if (err.value != _spinError.SPINNAKER_ERR_SUCCESS.value) {
                    printf("Unable to release image. Non-fatal error %d...\n\n", err)
                }

                continue
            }

            // Retrieve image width
            val width = SizeTPointer(1)
            err = spinImageGetWidth(hResultImage, width)
            if (Utils.printOnError(err, "spinImageGetWidth()")) {
                println("width  = unknown")
            } else {
                println("width  = " + width.get())
            }

            // Retrieve image height
            val height = SizeTPointer(1)
            err = spinImageGetHeight(hResultImage, height)

            if (Utils.printOnError(err, "spinImageGetHeight()")) {
                println("height = unknown")
            } else {
                println("height = " + height.get())
            }

            // Convert image to mono 8
            val hConvertedImage = spinImage()

            err = spinImageCreateEmpty(hConvertedImage)
            Utils.printOnError(err, "Unable to create image. Non-fatal error.")
            err = spinImageConvert(hResultImage, _spinPixelFormatEnums.PixelFormat_BGR8.value, hConvertedImage)
            Utils.printOnError(err, "\"Unable to convert image. Non-fatal error.")

            // Create unique file name
            val filename = if ((lenDeviceSerialNumber.get() == 0L))
                ("Sequencer-C-$imageCnt.jpg")
            else
                ("Sequencer-C-" + deviceSerialNumber.string.trim { it <= ' ' } + "-" + imageCnt + ".jpg")

            /*
            // Save image
            err = spinImageSave(
                hConvertedImage,
                BytePointer("c:\\savedframe\\$filename"),
                _spinImageFileFormat.JPEG.value
            )

            if (!Utils.printOnError(err, "Unable to save image. Non-fatal error.")) {
                println("Image saved at $filename\n")
            }*/

            val imageData = Pointer()
            spinImageGetData(hConvertedImage,imageData)

            val image = Mat(height.get().toInt(),width.get().toInt(),CvType.CV_8UC3, imageData.asByteBuffer())
            findColor(filename, image, Scalar(17.0, 15.0, 100.0), Scalar(50.0, 56.0, 200.0))

            // Destroy converted image
            err = spinImageDestroy(hConvertedImage)
            if (err.value != _spinError.SPINNAKER_ERR_SUCCESS.value) {
                printf("Unable to destroy image. Non-fatal error %d...\n\n", err)
            }

            // Release image
            err = spinImageRelease(hResultImage)
            if (err.value != _spinError.SPINNAKER_ERR_SUCCESS.value) {
                printf("Unable to release image. Non-fatal error %d...\n\n", err)
            }

            imageCnt++
        }

        // End acquisition
        //err = spinCameraEndAcquisition(hCam)
        //if (err.value != _spinError.SPINNAKER_ERR_SUCCESS.value) {
          //  printf("Unable to end acquisition. Non-fatal error %d...\n\n", err)
        //}

        return _spinError.SPINNAKER_ERR_SUCCESS
    }

    /**
     * This function acts very similarly to the RunSingleCamera() functions of other
     * examples, except that the values for the sequences are also calculated here;
     * please see NodeMapInfo example for additional information on the steps in
     * this function.
     */
    private fun runSingleCamera(hCam: spinCamera): _spinError {
        var err: _spinError

        // Retrieve TL device nodemap and print device information
        val hNodeMapTLDevice = spinNodeMapHandle()
        err = spinCameraGetTLDeviceNodeMap(hCam, hNodeMapTLDevice)
        if (!Utils.printOnError(err, "Unable to retrieve TL device nodemap .")) {
            printDeviceInfo(hNodeMapTLDevice)
        }

        // Initialize camera
        err = spinCameraInit(hCam)
        if (Utils.printOnError(err, "Unable to initialize camera.")) {
            return err
        }

        // Retrieve GenICam nodemap
        val hNodeMap = spinNodeMapHandle()
        err = spinCameraGetNodeMap(hCam, hNodeMap)
        if (Utils.printOnError(err, "Unable to retrieve GenICam nodemap.")) {
            return err
        }

        // Acquire images
        if (acquireImages(
                hCam,
                hNodeMap,
                hNodeMapTLDevice
            ).value != _spinError.SPINNAKER_ERR_SUCCESS.value
        ) {
            return _spinError.SPINNAKER_ERR_ACCESS_DENIED
        }

        // Deinitialize camera
        err = spinCameraDeInit(hCam)
        return if (Utils.printOnError(err, "Unable to deinitialize camera.")) {
            err
        } else err

    }

    private fun findColor(name: String, image: Mat, lower: Scalar, upper: Scalar): Mat? {

        var y = 0
        val xe = image.width() / 3
        val ye = image.height() / 3

        var fileName = 0
        while (y < (ye * 3)) {
            var x = 0
            while (x < (xe * 3)) {
                val rect = Rect(x, y, xe, ye)
                val subImage = Mat(image, rect)
                val destination = Mat()
                Core.inRange(subImage, lower, upper, destination)
                val blackPixels = Core.countNonZero(destination)
                if (blackPixels > 0) {
                    if(x != xe) {
                        println("Founded!!!")
                        //Toolkit.getDefaultToolkit().beep()
                        //System.out.flush();
                        Imgcodecs.imwrite("c:\\Elaborati\\$name _ Frame _ $fileName.jpg", subImage)
                    }
                }
                x += xe
                fileName++
            }
            y += ye
        }

        return image
    }


}