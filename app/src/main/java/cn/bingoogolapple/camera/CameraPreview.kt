package cn.bingoogolapple.camera

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaActionSound
import android.media.MediaRecorder
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.support.v4.math.MathUtils
import android.util.Log
import android.view.*
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


/**
 * 作者:王浩
 * 创建时间:2018/9/28
 * 描述:
 */
class CameraPreview(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Camera.PreviewCallback {
    private var mCamera: Camera? = null
    private var mOutputMediaFileUri: Uri? = null
    private var mOutputMediaFileType: String? = null
    private var mMediaRecorder: MediaRecorder? = null
    private var oldDist = 1f
    private var touchFocusing = false

    init {
        holder.addCallback(this)
    }

    fun getCamera(): Camera? {
        return mCamera
    }

    private fun log(parameters: Camera.Parameters) {
        val log = StringBuilder()
        log.append("\n相机信息如下：")
        log.append("\n    支持的预览尺寸有：\n    ")
        for (previewSize in parameters.supportedPreviewSizes) {
            log.append("${previewSize.width}x${previewSize.height}").append("、")
        }
        log.append("\n支持的拍照尺寸有：\n    ")
        for (pictureSize in parameters.supportedPictureSizes) {
            log.append("${pictureSize.width}x${pictureSize.height}").append("、")
        }
        log.append("\n支持的录像尺寸有：\n    ")
        for (videoPictureSize in parameters.supportedVideoSizes) {
            log.append("${videoPictureSize.width}x${videoPictureSize.height}").append("、")
        }
        log.append("\n支持的对焦模式有：\n    ")
        for (focusMode in parameters.supportedFocusModes) {
            log.append(focusMode).append("、")
        }
        log.append("\n支持的白平衡有：\n    ")
        for (whiteBalance in parameters.supportedWhiteBalance) {
            log.append(whiteBalance).append("、")
        }
        parameters.supportedSceneModes?.let {
            log.append("\n支持的场景模式有：\n    ")
            for (sceneMode in it) {
                log.append(sceneMode).append("、")
            }
        }
        log.append("\n支持的闪光灯模式有：\n    ")
        for (flashMode in parameters.supportedFlashModes) {
            log.append(flashMode).append("、")
        }
        log.append("\n曝光补偿范围为：\n    ${parameters.minExposureCompensation}~${parameters.maxExposureCompensation}")
        Log.d(TAG, log.toString())
    }

    private fun adjustDisplayRatio(rotation: Int) {
        mCamera?.let {
            val parent = parent as ViewGroup
            val rect = Rect()
            parent.getLocalVisibleRect(rect)
            val width = rect.width()
            val height = rect.height()
            val previewSize = it.parameters.previewSize
            val previewWidth: Int
            val previewHeight: Int
            if (rotation == 90 || rotation == 270) {
                previewWidth = previewSize.height
                previewHeight = previewSize.width
            } else {
                previewWidth = previewSize.width
                previewHeight = previewSize.height
            }

            if (width * previewHeight > height * previewWidth) {
                val scaledChildWidth = previewWidth * height / previewHeight
                layout((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height)
            } else {
                val scaledChildHeight = previewHeight * width / previewWidth
                layout(0, (height - scaledChildHeight) / 2, width, (height + scaledChildHeight) / 2)
            }
        }
    }

    private fun openCamera() {
        if (mCamera != null) {
            return
        }

        try {
            mCamera = Camera.open()
        } catch (e: Exception) {
            Log.e(TAG, "相机资源被占用：" + e.message)
        }
    }

    public fun startPreview() {
        openCamera()
        try {
            mCamera?.apply {
                // 告知将预览帧数据交给谁
                setPreviewDisplay(holder)
                // 每当有预览帧生成时就会回调 onPreviewFrame 方法
                setPreviewCallback(this@CameraPreview)
                // 开始预览
                startPreview()
                log(parameters)
            }
        } catch (e: IOException) {
            Log.e(TAG, "开始预览失败：" + e.message)
        }
    }

    public fun stopPreview() {
        holder.removeCallback(this)
        // 相机是共享资源，使用完后需要释放相机资源
        mCamera?.apply {
            setPreviewCallback(null)
            stopPreview()
            release()
        }
        mCamera = null
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        try {
            Log.i(TAG, "模拟处理预览帧数据")
            Thread.sleep(500)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        startPreview()
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
        stopPreview()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        mCamera?.apply {
            val rotation = getDisplayOrientation()
            val newParameters = parameters
            // 设置预览帧数据，以及拍摄照片的方向
            newParameters.setRotation(rotation)
            parameters = newParameters
            // 指定预览的旋转角度
            setDisplayOrientation(getDisplayOrientation())
            // 实时调整预览纵横比
            adjustDisplayRatio(rotation)
        }
    }

    private fun getDisplayOrientation(): Int {
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val rotation = display.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        val camInfo = Camera.CameraInfo()
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, camInfo)
        return (camInfo.orientation - degrees + 360) % 360
    }

    private fun getAppName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).applicationInfo.loadLabel(context.packageManager).toString()
        } catch (e: Exception) {
            // 利用系统api getPackageName()得到的包名，这个异常根本不可能发生
            ""
        }
    }

    private fun getOutputMediaFile(type: Int): File? {
        val mediaStorageDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), getAppName())
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "创建媒体文件目录失败，请检查存储权限")
                return null
            }
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val mediaFile: File
        when (type) {
            MEDIA_TYPE_IMAGE -> {
                mediaFile = File(mediaStorageDir.path + File.separator + "IMG_" + timeStamp + ".jpg")
                mOutputMediaFileType = "image/*"
            }
            MEDIA_TYPE_VIDEO -> {
                mediaFile = File(mediaStorageDir.path + File.separator + "VID_" + timeStamp + ".mp4")
                mOutputMediaFileType = "video/*"
            }
            else -> return null
        }
        mOutputMediaFileUri = Uri.fromFile(mediaFile)
        return mediaFile
    }

    fun getOutputMediaFileUri(): Uri? {
        return mOutputMediaFileUri
    }

    fun getOutputMediaFileType(): String? {
        return mOutputMediaFileType
    }

    fun takePicture(previewIv: ImageView) {
        mCamera?.apply {
            takePicture(Camera.ShutterCallback {
                Log.d(TAG, "按下了快门，播放声音")
                MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
            }, Camera.PictureCallback { data, camera ->
                Log.d(TAG, "原始数据，不知道为什么返回的 data 一直为空")
            }, Camera.PictureCallback { data, camera ->
                Log.d(TAG, "jpeg 数据。主线程回调的 " + data.size)
                // TODO 子线程保存图片文件
                val pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE)
                if (pictureFile == null) {
                    Log.d(TAG, "创建媒体文件失败，请检查存储权限")
                    return@PictureCallback
                }
                try {
                    FileOutputStream(pictureFile).use {
                        it.write(data)
                    }

                    previewIv.setImageURI(mOutputMediaFileUri)

                    camera.startPreview()
                } catch (e: Exception) {
                    Log.d(TAG, "保存图片失败 " + e.message)
                }
            })
        }
    }

    fun startRecording(): Boolean {
        if (prepareVideoRecorder()) {
            mMediaRecorder?.start()
            return true
        } else {
            releaseMediaRecorder()
        }
        return false
    }

    fun stopRecording(previewIv: ImageView) {
        mMediaRecorder?.stop()
        mOutputMediaFileUri?.apply {
            val thumbnail = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND)
            previewIv.setImageBitmap(thumbnail)
        }
        releaseMediaRecorder()
    }

    fun isRecording(): Boolean {
        return mMediaRecorder != null
    }

    private fun prepareVideoRecorder(): Boolean {
        openCamera()
        mMediaRecorder = MediaRecorder()

        mCamera?.unlock()
        mMediaRecorder?.apply {

            setCamera(mCamera)

            setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            setVideoSource(MediaRecorder.VideoSource.CAMERA)
            setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH))

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val prefVideoSize = prefs.getString("video_size", "")
            if (prefVideoSize.isNotBlank()) {
                val split = prefVideoSize.split("x".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                setVideoSize(Integer.parseInt(split[0]), Integer.parseInt(split[1]))
            }
            setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString())
            setPreviewDisplay(holder.surface)

            try {
                // 视频的旋转并不是编码层面的旋转，视频帧数据并没有发生旋转，而只是在视频中增加了参数，希望播放器按照指定的旋转角度旋转后播放，所以具体效果因播放器而异
                setOrientationHint(getDisplayOrientation())
                prepare()
            } catch (e: IllegalStateException) {
                Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.message)
                releaseMediaRecorder()
                return false
            } catch (e: IOException) {
                Log.d(TAG, "IOException preparing MediaRecorder: " + e.message)
                releaseMediaRecorder()
                return false
            }
        }
        return true
    }

    private fun releaseMediaRecorder() {
        mMediaRecorder?.apply {
            reset()
            release()
            mMediaRecorder = null
        }
        mCamera?.apply { lock() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mCamera?.let {
            if (event.pointerCount == 1) {
                if (event.action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_UP) {
                    handleFocusMetering(event, it)
                }
            } else {
                when (event.action and MotionEvent.ACTION_MASK) {
                    MotionEvent.ACTION_POINTER_DOWN -> oldDist = getFingerSpacing(event)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_MOVE -> {
                        val newDist = getFingerSpacing(event)
                        if (newDist > oldDist) {
                            handleZoom(true, it)
                        } else if (newDist < oldDist) {
                            handleZoom(false, it)
                        }
                        oldDist = newDist
                    }
                }
            }
        }
        return true
    }

    private fun handleFocusMetering(event: MotionEvent, camera: Camera) {
        if (touchFocusing) {
            return
        }
        touchFocusing = true

        var isNeedUpdate = false
        val newParams = camera.parameters
        val currentFocusMode = newParams.focusMode
        Log.i(TAG, "老的对焦模式为$currentFocusMode")
        if (newParams.maxNumFocusAreas > 0) {
            Log.i(TAG, "支持触摸对焦")
            isNeedUpdate = true
            val focusAreas = arrayListOf<Camera.Area>()
            val focusRect = calculateTapArea(event.x, event.y, 1f, width, height)
            focusAreas.add(Camera.Area(focusRect, 800))
            newParams.focusAreas = focusAreas
            newParams.focusMode = Camera.Parameters.FOCUS_MODE_MACRO
        } else {
            Log.i(TAG, "不支持触摸对焦")
        }

        if (newParams.maxNumMeteringAreas > 0) {
            Log.i(TAG, "支持触摸测光")
            isNeedUpdate = true
            val meteringAreas = arrayListOf<Camera.Area>()
            val meteringRect = calculateTapArea(event.x, event.y, 1.5f, width, height)
            meteringAreas.add(Camera.Area(meteringRect, 800))
            newParams.meteringAreas = meteringAreas
        } else {
            Log.i(TAG, "不支持触摸测光")
        }

        if (isNeedUpdate) {
            camera.cancelAutoFocus()
            camera.parameters = newParams
            camera.autoFocus { success, camera ->
                if (success) {
                    Log.i(TAG, "对焦成功")
                } else {
                    Log.i(TAG, "对焦失败")
                }
                touchFocusing = false
                val recoverParams = camera.parameters
                recoverParams.focusMode = currentFocusMode
                camera.parameters = recoverParams
                Log.i(TAG, "还原对焦模式")
            }
        } else {
            touchFocusing = false
        }
    }

    private fun handleZoom(isZoomIn: Boolean, camera: Camera) {
        val params = camera.parameters
        if (params.isZoomSupported) {
            var zoom = params.zoom
            if (isZoomIn && zoom < params.maxZoom) {
                Log.i(TAG, "放大")
                zoom++
            } else if (!isZoomIn && zoom > 0) {
                Log.i(TAG, "缩小")
                zoom--
            } else {
                Log.i(TAG, "既不放大也不缩小")
            }
            params.zoom = zoom
            camera.parameters = params
        } else {
            Log.i(TAG, "不支持缩放")
        }
    }

    companion object {
        private val TAG = CameraPreview::class.java.simpleName
        private const val MEDIA_TYPE_IMAGE = 1
        private const val MEDIA_TYPE_VIDEO = 2

        private fun calculateTapArea(x: Float, y: Float, coefficient: Float, width: Int, height: Int): Rect {
            val focusAreaSize = 300f
            val areaSize = (focusAreaSize * coefficient).toInt()
            val centerX = (x / width * 2000 - 1000).toInt()
            val centerY = (y / height * 2000 - 1000).toInt()

            val halfAreaSize = areaSize / 2
            val rectF = RectF(MathUtils.clamp(centerX - halfAreaSize, -1000, 1000).toFloat(),
                    MathUtils.clamp(centerY - halfAreaSize, -1000, 1000).toFloat(),
                    MathUtils.clamp(centerX + halfAreaSize, -1000, 1000).toFloat(),
                    MathUtils.clamp(centerY + halfAreaSize, -1000, 1000).toFloat())
            return Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom))
        }

        private fun getFingerSpacing(event: MotionEvent): Float {
            val x = event.getX(0) - event.getX(1)
            val y = event.getY(0) - event.getY(1)
            return Math.sqrt((x * x + y * y).toDouble()).toFloat()
        }
    }
}