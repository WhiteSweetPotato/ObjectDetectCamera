package com.example.cameraex

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.util.*
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.lang.System.exit
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity(), SensorEventListener {

    private val tagName = MainActivity::class.java.simpleName

    private var cameraDevice: CameraDevice? = null
    private var mPreviewBuilder: CaptureRequest.Builder? = null
    private var mPreviewSession: CameraCaptureSession? = null
    private var manager: CameraManager? = null

    //카메라 설정에 관한 멤버 변수
    private var mPreviewSize: Size? = null
    private var map: StreamConfigurationMap? = null

    //권한 멤버 변수
    private val requestCode: Int = 200
    private val permissionArray: Array<String> =
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    // 두 번 터치 변수
    private var touchCount:Int = 0 // 터치 누적 횟수
    private var DELAY:Long = 230 // handler delay, 230 -> 0.23

    // 물체 Box onoff 변수
    private var boxOnOff:Int = 0

    // 센서 lateinit -> 나중에 초기화
    // 아무것도 대입안하고 사용하려고 하면 강제종료.
    lateinit var sensorManager: SensorManager


    // 카메라 surfaceView 설정 시작
    private val mSurfaceTextureListener = object : TextureView.SurfaceTextureListener {

        //TextureView 생성될시 Available 메소드가 호출된다.
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            // cameraManager 생성
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture?,
            width: Int,
            height: Int
        ) {
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?) = false
    }

    //카메라 연결 상태 콜백
    private val mStateCallBack = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            //CameraDevice 객체 생성
            cameraDevice = camera

            //CaptureRequest.Builder 객체와 CaptureSession 객체 생성하여 미래보기 화면을 실행
            startPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {}
        override fun onError(camera: CameraDevice, error: Int) {}
    }

    // onCreate 시작
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //권한 체크하기
        if (checkPermission()) {
            initLayout()
        } else {
            ActivityCompat.requestPermissions(this, permissionArray, requestCode)
        }
//        ib_camera.setOnClickListener {
//            takePicture()
//        }

//        if (OpenCVLoader.initDebug()) println("LOADED : success")
//        else println("LOADED : error")

        if (OpenCVLoader.initDebug()) {
            println("MainActivity: Opencv is loaded")
        }
        else {
            println("MainActivity: Opencv falide to load")
        }
    }

//    fun makeGray(bitmap: Bitmap) : Bitmap {
//
//        // Create OpenCV mat object and copy content from bitmap
//        val mat = Mat()
//        Utils.bitmapToMat(bitmap, mat)
//
//        // Convert to grayscale
//        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
//
//        // Make a mutable bitmap to copy grayscale image
//        val grayBitmap = bitmap.copy(bitmap.config, true)
//        Utils.matToBitmap(mat, grayBitmap)
//
//        return grayBitmap
//    }

    override fun onResume() {
        // onResume 위에 센서 설정을 해야 한다.
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_NORMAL)
        super.onResume()
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    // <------------------------------------------------------------------------------------------------------------------------>
    // 기타 함수들 시작

    // 카메라 함수 시작
    /**
     * 권한 체크하기
     */
    private fun checkPermission(): Boolean {
        //권한 요청
        return !(ContextCompat.checkSelfPermission(
            this,
            permissionArray[0]
        ) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    permissionArray[1]
                ) != PackageManager.PERMISSION_GRANTED)
    }

    /**
     * 권한 요청에 관한 callback 메소드 구현
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == this.requestCode && grantResults.isNotEmpty()) {
            var permissionGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    //사용자가 권한을 거절했을 시
                    permissionGranted = false
                    break
                }
            }

            //권한을 모두 수락했을 경우
            if (permissionGranted) {
                initLayout()
            } else {
                //권한을 수락하지 않았을 경우
                ActivityCompat.requestPermissions(this, permissionArray, requestCode)
            }
        }
    }

    /**
     * 레이아웃 전개하기
     */
    private fun initLayout() {
        setContentView(R.layout.activity_main)
        preview.surfaceTextureListener = mSurfaceTextureListener
    }

    /**
     * CameraManager 생성
     * 카메라에 관한 정보 얻기
     * openCamera() 메소드 호출 -> CameraDevice 객체 생성
     */
    private fun openCamera(width: Int, height: Int) {
        //카메라 매니저를 생성한다.
        manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager?
        //기본 카메라를 선택한다.
        val cameraId = manager!!.cameraIdList[0]

        //카메라 특성을 가져오기
        val characteristics: CameraCharacteristics =
            manager!!.getCameraCharacteristics(cameraId)
        val level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val fps =
            characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        // Log.d(tagName, "최대 프레임 비율 : ${fps[fps.size - 1]} hardware level : $level")

        //StreamConfigurationMap 객체에는 카메라의 각종 지원 정보가 담겨져있다.
        map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        //미리보기용 textureView 화면 크기를 설정한다. (제공할 수 있는 최대 크기)
        mPreviewSize = map!!.getOutputSizes(SurfaceTexture::class.java)[0]
        val fpsForVideo = map!!.highSpeedVideoFpsRanges

//        Log.e(
//            tagName,
//            "for video ${fpsForVideo[fpsForVideo.size - 1]} preview Size width: ${mPreviewSize!!.width} height : $height"
//        )

        //권한 체크
        if (checkPermission()) {
            //CameraDevice 생
            manager!!.openCamera(cameraId, mStateCallBack, null)
        } else {
            ActivityCompat.requestPermissions(this, permissionArray, requestCode)
        }
    }

    /**
     * Preview 시작
     */
    private fun startPreview() {
        if (cameraDevice == null || !preview.isAvailable || mPreviewSize == null) {
            Log.e(tagName, "startPreview() fail, return")
            return
        }

        val texture = preview.surfaceTexture
        val surface = Surface(texture)

        mPreviewBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        mPreviewBuilder!!.addTarget(surface)

        cameraDevice!!.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    mPreviewSession = session
                    updatePreview()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            },
            null
        )
    }

    /**
     * 업데이트 Preview
     */
    private fun updatePreview() {
        cameraDevice?.let {
            mPreviewBuilder!!.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            val thread = HandlerThread("CameraPreview")
            thread.start()

            val backgroundHandler = Handler(thread.looper)
            mPreviewSession!!.setRepeatingRequest(
                mPreviewBuilder!!.build(),
                null,
                backgroundHandler
            )

        }
    }

    // 파일 이름 중복 제거
    private fun newJpgFileName() : String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss")
        val filename = sdf.format(System.currentTimeMillis())
        return "${filename}.jpg"
    }

    /**
     * 사진 캡처
     */
    private fun takePicture() {
        ImagePixelLog()


        var jpegSizes: Array<Size>? = map?.getOutputSizes(ImageFormat.JPEG)

        var width = 640
        var height = 480

        if (jpegSizes != null && jpegSizes.isNotEmpty()) {
            width = jpegSizes[0].width
            height = jpegSizes[1].height
        }

        val imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
        val outputSurfaces = mutableListOf<Surface>()
        outputSurfaces.add(imageReader.surface)
        outputSurfaces.add(Surface(preview.surfaceTexture))

        val captureBuilder =
            cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureBuilder.addTarget(imageReader.surface)

        //이미지가 캡처되는 순간에 제대로 사진 이미지가 나타나도록 3A를 자동으로 설정한다.
        captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        val rotation = windowManager.defaultDisplay.rotation
        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90)

        val file = File(Environment.getExternalStorageDirectory(),  newJpgFileName())

        // 이미지를 캡처할 때 자동으로 호출된다.
        val readerListener = object : ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(reader: ImageReader?) {
                imageReader?.let {
                    var image: Image? = null
                    image = imageReader.acquireLatestImage()
                    val buffer: ByteBuffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.capacity())
                    buffer.get(bytes)
                    save(bytes)
                }
            }

            private fun save(bytes: ByteArray) {
                val output: OutputStream? = FileOutputStream(file)
                output?.let {
                    it.write(bytes)
                    output.close()
                }
            }
        }

        //이미지를 캡처하는 작업은 메인 스레드가 아닌 스레드 핸들러로 수행한다.
        val thread = HandlerThread("CameraPicture")
        thread.start()
        val backgroundHandler = Handler(thread.looper)

        // imageReader 와 ImageReader.OnImageAvailableListener 객체를 서로 연결시키기 위해 설정한다.
        imageReader.setOnImageAvailableListener(readerListener, backgroundHandler)

        val captureCallBack = object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                Toast.makeText(this@MainActivity, "사진이 캡처되었습니다.", Toast.LENGTH_SHORT).show()
                startPreview()
            }
        }

        //사진 이미지를 캡처하는데 사용하는 CameraCaptureSession 생성한다.
        // 이미 존재하면 기존 세션은 자동으로 종료
        cameraDevice!!.createCaptureSession(
            outputSurfaces,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    session.capture(captureBuilder.build(), captureCallBack, backgroundHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {

                }
            },
            backgroundHandler
        )
    }

    // 카메라 함수 종료 <------------------------------------------------------------------------------------------------------------------------>

    // 화면 한 번 터치시 박스를 그린다.
    // 화면 두 번 터치시 사진 캡처를 한다.
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                touchCount++
                onTouch(circleView, event)
                if (boxOnOff == 0) {
                    circleView.visibility = View.VISIBLE
                    boxOnOff = 1
                }
                else {
                    circleView.visibility = View.INVISIBLE
                    boxOnOff = 0
                }
            }
            MotionEvent.ACTION_UP -> {
                onTouch(circleView, event)
                Handler().postDelayed({
                    if (touchCount > 0)
                        touchCount-- }, DELAY)
            }
            MotionEvent.ACTION_MOVE -> {
                onTouch(circleView, event)
            }
        }

        if (touchCount == 2) {
            takePicture()
            if (touchCount > 0)
                touchCount = 0
        }
        return super.onTouchEvent(event)
    }

    // boxView를 터치한 곳으로 옮기는 함수
    fun onTouch(v: View, event: MotionEvent): Boolean {
        val parentWidth = (v.parent as ViewGroup).width // 부모 View 의 Width
        val parentHeight = (v.parent as ViewGroup).height // 부모 View 의 Height
        v.x = event.x - v.width / 2
        v.y = event.y - v.height / 2
        if (v.x < 0) {
            v.setX(0f)
        } else if (v.x + v.width > parentWidth) {
            v.x = (parentWidth - v.width).toFloat()
        }
        if (v.y < 0) {
            v.setY(0f)
        } else if (v.y + v.height > parentHeight) {
            v.y = (parentHeight - v.height).toFloat()
        }
        return true
    }

    // 자이로스코프를 통해 뷰를 이동한다.
    fun moveObjectToGyroscope(v: View, str : String, value: Float) {
        val density = resources.displayMetrics.density
        var constantNum_vct_x = 100f * density
        var constantNum_vct_y = 80f * density
        var constantNum_vct_z = 100f * density
        var dp_x = pxToDp(v.x)
        var dp_y = pxToDp(v.y)
        var dpMax_x = 360
        var dpMax_y = 700
        var aspect_ratio = dpMax_y/dpMax_x

        when (str) {
            "x" -> {
                v.x = v.x + constantNum_vct_x*value
            }
            "y" -> {
                v.y = v.y - constantNum_vct_y*value
            }
            "z" -> {
                when {
                    dp_x < dpMax_x / 2 && dp_y < dpMax_y -> {
                        v.x = v.x - constantNum_vct_z*value
                        v.y = v.y + constantNum_vct_z*value*aspect_ratio
                    }
                    dp_x >= dpMax_x / 2 && dp_y < dpMax_y -> {
                        v.x = v.x - constantNum_vct_z*value
                        v.y = v.y - constantNum_vct_z*value*aspect_ratio
                    }
                    dp_x < dpMax_x / 2 && dp_y >= dpMax_y -> {
                        v.x = v.x + constantNum_vct_z*value
                        v.y = v.y + constantNum_vct_z*value*aspect_ratio
                    }
                    dp_x >= dpMax_x / 2 && dp_y >= dpMax_y -> {
                        v.x = v.x + constantNum_vct_z*value
                        v.y = v.y - constantNum_vct_z*value*aspect_ratio
                    }
                }
            }
        }
//      objectOutOfLimit(circleView, "x")
//      objectOutOfLimit(circleView, "y")
    }

    fun pxToDp(px : Float) : Float {
        val density = resources.displayMetrics.density
        val value = (px / density).toFloat()
        return value
    }

    // 센서를 다루는 함수
    // 자이로스코프 센서를 사용
    override fun onSensorChanged(event: SensorEvent?) {

        val x = event?.values?.get(0) as Float // y축 기준으로 핸드폰 앞쪽으로 + 뒤로 -
        val y = event?.values?.get(1) as Float // z축 기준으로 핸드폰 반시계 + 시계 -
        val z = event?.values?.get(2) as Float // x축 기준으로 핸드폰 반시계 + 시계 -

        // println(x.toString() + ", " + y.toString() + ", " + z.toString())

        if (boxOnOff == 1) {
            moveObjectToGyroscope(circleView, "x", x);
            moveObjectToGyroscope(circleView, "y", y);
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    // 볼륨 키를 누르면 실행 되는 함수.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                //Toast.makeText(this, "Volume Up Pressed", Toast.LENGTH_SHORT).show()
                exit()
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                //Toast.makeText(this, "Volume Down Pressed", Toast.LENGTH_SHORT).show()
                exit()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // 종료
    fun exit() {
        ActivityCompat.finishAffinity(this)
        System.exit(0)
    }

    // <------------------------------------------------------------------------------------------------------------------------>
    // 20일 이후 추가.

    fun ImagePixelLog() {
        val texture = preview.surfaceTexture
        val surface = Surface(texture)

//        texture.
//        println("imagePixel :" +  preview.getBitmap())
    }




    // 아래는 사용 하지 않은 함수들
    // <------------------------------------------------------------------------------------------------------------------------>
    fun objectOutOfLimit(v: View, str : String) {
        val parentWidth = (v.parent as ViewGroup).width // 부모 View 의 Width
        val parentHeight = (v.parent as ViewGroup).height // 부모 View 의 Height

        when (str) {
            "x" -> {
                if (v.x < 0) {
                    v.setX(0f)
                }
                else if (v.x + v.width > parentWidth) {
                    v.x = (parentWidth - v.width).toFloat()
                }
            }
            "y" -> {
                if (v.y < 0) {
                    v.setY(0f)
                }
                else if (v.y + v.height > parentHeight) {
                    v.y = (parentHeight - v.height).toFloat()
                }
            }
        }
    }

    // 터치시 생성 할 boxView
    fun createBox() : View{
        val boxView = TextView(this)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        boxView.layoutParams = lp
        boxView.background = ContextCompat.getDrawable(this, R.drawable.custom_square)
        boxView.id = ViewCompat.generateViewId()

        boxView.visibility = View.VISIBLE
        return boxView
    }

    fun getDeviceDpi(): String {
        val density = resources.displayMetrics.density
        val result = when {
            density >= 4.0 -> "xxxhdpi"
            density >= 3.0 -> "xxhdpi"
            density >= 2.0 -> "xhdpi"
            density >= 1.5 -> "hdpi"
            density >= 1.0 -> "mdpi"
            else -> "ldpi"
        }
        println("gimgongta log dpi : $result")
        return result
    }

//    // Px과 Dp를 다루는 함수들
//    fun getDevicePx(str: String): Int {
//        val display = this.applicationContext?.resources?.displayMetrics
//        var deviceWidth = display?.widthPixels
//        var deviceHeight = display?.heightPixels
//        deviceWidth = px2dp(deviceWidth!!, this)
//        deviceHeight = px2dp(deviceHeight!!, this)
//
//        Log.d("deviceSize", "${deviceWidth}")
//        Log.d("deviceSize", "${deviceHeight}")
//
//        //var result : Int? = 0
//        when (str) {
//            "deviceWidth" -> return deviceWidth
//            "deviceHeight" -> return deviceHeight
//            else -> return 0
//        }
//    }

//    private fun constraintWidget(constraintLayout: ConstraintLayout, target: Int, standard: Int, startMargin: Float, topMargin: Float) {
//        val constraintSet = ConstraintSet()
//        constraintSet.clone(constraintLayout)
//        constraintSet.connect(target, ConstraintSet.START, standard, ConstraintSet.START, convertDpToPixel(startMargin, this))
//        constraintSet.connect(target, ConstraintSet.TOP, standard, ConstraintSet.TOP, convertDpToPixel(topMargin, this))
//        constraintSet.applyTo(constraintLayout)
//    }
//
//    private fun convertDpToPixel(dp: Float, context: Context): Int {
//        return (dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
//    }

//    private class drawViewBox (context:Context) : View(context) {
//        override fun onDraw(canvas: Canvas?) {
//            super.onDraw(canvas)
//            val paint = Paint()
//            paint.color = Color.CYAN
//            paint.style = Paint.Style.FILL
//            canvas?.drawCircle(100f, 100f, 100f, paint)
//        }
//    }

}


