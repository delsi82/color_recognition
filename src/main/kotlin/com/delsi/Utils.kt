package com.delsi

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.SizeTPointer
import org.bytedeco.javacpp.Spinnaker_C.*


object Utils {

    private val MAX_BUFF_LEN = 256

    /**
     * Check if 'err' is 'SPINNAKER_ERR_SUCCESS'.
     * If it is do nothing otherwise print error description and exit.
     *
     * @param err     error value.
     * @param message additional message to print.
     */
    fun exitOnError(err: _spinError, message: String) {
        if (printOnError(err, message)) {
            println("Aborting.")
            System.exit(err.value)
        }
    }

    /**
     * Check if 'err' is 'SPINNAKER_ERR_SUCCESS'.
     * If it is do nothing otherwise print error information.
     *
     * @param err     error value.
     * @param message additional message to print.
     * @return 'false' if err is SPINNAKER_ERR_SUCCESS, or 'true' for any other 'err' value.
     */
    fun printOnError(err: _spinError, message: String): Boolean =
        if (err.value != _spinError.SPINNAKER_ERR_SUCCESS.value) {
            println(message)
            println("Error " + err.value + " " + findErrorNameByValue(err.value) + "\n")
            true
        } else {
            false
        }


    /**
     * This function prints the device information of the camera from the transport
     * layer; please see NodeMapInfo_C example for more in-depth comments on
     * printing device information from the nodemap.
     */
    fun printDeviceInfo(hNodeMap: spinNodeMapHandle): _spinError {
        var err: _spinError
        println("\n*** DEVICE INFORMATION ***\n\n")
        // Retrieve device information category node
        val hDeviceInformation = spinNodeHandle()
        err = spinNodeMapGetNode(hNodeMap, BytePointer("DeviceInformation"), hDeviceInformation)
        printOnError(err, "Unable to retrieve node.")

        // Retrieve number of nodes within device information node
        val numFeatures = SizeTPointer(1)
        if (isAvailableAndReadable(hDeviceInformation, "DeviceInformation")) {
            err = spinCategoryGetNumFeatures(hDeviceInformation, numFeatures)
            printOnError(err, "Unable to retrieve number of nodes.")
        } else {
            printRetrieveNodeFailure("node", "DeviceInformation")
            return _spinError.SPINNAKER_ERR_ACCESS_DENIED
        }

        // Iterate through nodes and print information
        for (i in 0 until numFeatures.get()) {
            val hFeatureNode = spinNodeHandle()
            err = spinCategoryGetFeatureByIndex(hDeviceInformation, i, hFeatureNode)
            printOnError(err, "Unable to retrieve node.")

            // get feature node name
            val featureName = BytePointer(MAX_BUFF_LEN.toLong())
            val lenFeatureName = SizeTPointer(1)
            lenFeatureName.put(MAX_BUFF_LEN.toLong())
            err = spinNodeGetName(hFeatureNode, featureName, lenFeatureName)
            if (printOnError(err, "Error retrieving node name.")) {
                featureName.putString("Unknown name")
            }

            val featureType = intArrayOf(_spinNodeType.UnknownNode.value)
            if (isAvailableAndReadable(hFeatureNode, featureName.string)) {
                err = spinNodeGetType(hFeatureNode, featureType)
                if (printOnError(err, "Unable to retrieve node type.")) {
                    continue
                }
            } else {
                println("$featureName: Node not readable")
                continue
            }
            val featureValue = BytePointer(MAX_BUFF_LEN.toLong())
            val lenFeatureValue = SizeTPointer(1)
            lenFeatureValue.put(MAX_BUFF_LEN.toLong())
            err = spinNodeToString(hFeatureNode, featureValue, lenFeatureValue)
            if (printOnError(err, "spinNodeToString")) {
                featureValue.putString("Unknown value")
            }
            println(featureName.string.trim { it <= ' ' } + ": " + featureValue.string.trim { it <= ' ' } + ".")
        }
        println()
        return err
    }

    /**
     * This function helps to check if a node is available
     */
    fun isAvaiable(hNode: spinNodeHandle): Boolean {
        val pbAvailable = BytePointer(1.toLong())
        pbAvailable.putBool(false)
        val err = spinNodeIsAvailable(hNode, pbAvailable)
        printOnError(err, "Unable to retrieve node availability ($hNode node)")
        return pbAvailable.bool
    }

    /**
     * This function helps to check if a node is available and readable
     */
    fun isAvailableAndReadable(hNode: spinNodeHandle, nodeName: String): Boolean {
        val pbAvailable = BytePointer(1.toLong())
        var err: _spinError
        err = spinNodeIsAvailable(hNode, pbAvailable)
        printOnError(err, "Unable to retrieve node availability ($nodeName node)")

        val pbReadable = BytePointer(1.toLong())
        err = spinNodeIsReadable(hNode, pbReadable)
        printOnError(err, "Unable to retrieve node readability ($nodeName node)")
        return pbReadable.bool && pbAvailable.bool
    }

    /**
     * This function helps to check if a node is readable
     */
    fun isReadable(hNode: spinNodeHandle): Boolean {
        val pbReadable = BytePointer(1.toLong())
        pbReadable.putBool(false)
        val err = spinNodeIsReadable(hNode, pbReadable)
        printOnError(err, "Unable to retrieve node availability ($hNode node)")
        return pbReadable.bool
    }

    /**
     * This function helps to check if a node is writable
     */
    fun isWritable(hNode: spinNodeHandle): Boolean {
        val pbWritable = BytePointer(1.toLong())
        pbWritable.putBool(false)
        val err = spinNodeIsWritable(hNode, pbWritable)
        printOnError(err, "Unable to retrieve node writability($hNode node)")
        return pbWritable.bool
    }

    /**
     * This function handles the error prints when a node or entry is unavailable or
     * not readable/writable on the connected camera
     */
    fun printRetrieveNodeFailure(node: String, name: String) {
        println("Unable to get $node ($name $node retrieval failed).")
        println("The $node may not be available on all camera models...")
        println("Please try a Blackfly S camera.\n")
    }

    /**
     * Proxy for `System.out.printf()`, so it can be imported as static import and simplify porting C code.
     */
    fun printf(format: String, vararg args: Any) {
        System.out.printf(format, *args)
    }

    private fun findErrorNameByValue(value: Int): String {
        for (v in _spinError.values()) {
            if (v.value == value) {
                return v.name
            }
        }
        return "???"
    }

    fun findImageStatusNameByValue(value: Int): String {
        for (v in _spinImageStatus.values()) {
            if (v.value == value) {
                return v.name
            }
        }
        return "???"
    }
}