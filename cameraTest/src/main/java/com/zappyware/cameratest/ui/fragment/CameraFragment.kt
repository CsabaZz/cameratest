package com.zappyware.cameratest.ui.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.zappyware.cameratest.CameraViewModel
import com.zappyware.cameratest.R
import com.zappyware.cameratest.databinding.FragmentCameraBinding
import com.zappyware.cameratest.ui.AutoFitSurfaceView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue

class CameraFragment: BaseFragment() {

    companion object {

        private const val TAG = "Camera::CameraFragment"

        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        private const val REQUEST_CODE_PERMISSIONS = 32

        private const val IMAGE_BUFFER_SIZE: Int = 3

        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        private val ORIENTATIONS: SparseIntArray = SparseIntArray(4).apply {
            append(Surface.ROTATION_0, 90)
            append(Surface.ROTATION_90, 0)
            append(Surface.ROTATION_180, 270)
            append(Surface.ROTATION_270, 180)
        }

        @JvmStatic
        fun newInstance(): CameraFragment = CameraFragment()
    }

    private val viewModel: CameraViewModel by activityViewModels()

    private lateinit var binding: FragmentCameraBinding

    private lateinit var surfaceView: AutoFitSurfaceView
    private lateinit var cameraId: String
    private lateinit var backgroundHandlerThread: HandlerThread
    private lateinit var backgroundHandler: Handler
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var imageReader: ImageReader
    private lateinit var previewSize: Size

    private val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)

    private val surfaceViewCallback = object : SurfaceHolder.Callback {

        override fun surfaceCreated(holder: SurfaceHolder) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            //
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            //
        }

    }

    private val captureStateCallback = object : CameraCaptureSession.StateCallback() {

        override fun onConfigureFailed(session: CameraCaptureSession) {
            //
        }
        override fun onConfigured(session: CameraCaptureSession) {
            cameraCaptureSession = session
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
        }
    }

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image: Image = reader.acquireLatestImage()
        imageQueue.add(image)
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera

            surfaceView.post {
                surfaceView.setAspectRatio(previewSize.width, previewSize.height)
                surfaceView.post {
                    captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    captureRequestBuilder.addTarget(surfaceView.holder.surface)

                    cameraDevice.createCaptureSession(listOf(surfaceView.holder.surface, imageReader.surface), captureStateCallback, backgroundHandler)
                }
            }
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            //
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            val errorMsg = when(error) {
                ERROR_CAMERA_DEVICE -> "Fatal (device)"
                ERROR_CAMERA_DISABLED -> "Device policy"
                ERROR_CAMERA_IN_USE -> "Camera in use"
                ERROR_CAMERA_SERVICE -> "Fatal (service)"
                ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                else -> "Unknown"
            }
            Log.e(TAG, "Error when trying to connect camera $errorMsg")
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {
            //
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            //
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
            while(imageQueue.isNotEmpty()) {
                val image = imageQueue.take()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    image.format != ImageFormat.DEPTH_JPEG &&
                    image.timestamp != resultTimestamp) continue

                while (imageQueue.size > 0) {
                    imageQueue.take().close()
                }

                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }

                val outputDirectory: File = getOutputDirectory()
                val photoFile = File(outputDirectory, SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg")

                try {
                    FileOutputStream(photoFile).use { it.write(bytes) }
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                }

                viewModel.currentPhotoFile = photoFile
                viewModel.previewFile()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.viewInteractor.observe(this) {
            when(it) {
                CameraViewModel.CameraViewInteractor.TakePicture -> takePhoto()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCameraBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        surfaceView = binding.surfaceView
        surfaceView.holder.addCallback(surfaceViewCallback)

        cameraManager = requireActivity().getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager
    }

    override fun onStart() {
        super.onStart()
        startBackgroundThread()
    }

    override fun onStop() {
        super.onStop()
        stopCamera()
        stopBackgroundThread()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(requireActivity(), "Permissions has not been granted by the user.", Toast.LENGTH_SHORT).show()
                requireActivity().finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireActivity(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = requireActivity().externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.module_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists()) {
            mediaDir
        } else {
            requireActivity().filesDir
        }
    }

    private fun startBackgroundThread() {
        backgroundHandlerThread = HandlerThread("CameraVideoThread")
        backgroundHandlerThread.start()
        backgroundHandler = Handler(backgroundHandlerThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundHandlerThread.quitSafely()
        backgroundHandlerThread.join()
    }

    @SuppressLint("MissingPermission")
    private fun startCamera() {
        val cameraIds: Array<String> = cameraManager.cameraIdList
        cameraId = cameraIds.firstOrNull { id ->
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(id)
            cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_FRONT
        }.orEmpty()

        val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        val streamConfigurationMap : StreamConfigurationMap? = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (streamConfigurationMap != null) {
            previewSize = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(
                ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!
            imageReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.JPEG, 1)
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
        }

        cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
    }

    private fun stopCamera() {
        cameraDevice.close()
    }

    private fun takePhoto() {
        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequestBuilder.addTarget(imageReader.surface)

        val characteristics: CameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, (ORIENTATIONS.get(requireActivity().windowManager.defaultDisplay.rotation) + (characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0) + 270) % 360)
        cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, null)
    }
}
