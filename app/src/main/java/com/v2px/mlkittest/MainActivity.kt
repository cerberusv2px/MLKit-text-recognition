package com.v2px.mlkittest

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.OnFailureListener
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.cloud.text.FirebaseVisionCloudText
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private var mImageView: ImageView? = null
    private var mButton: Button? = null
    private var mCloudButton: Button? = null
    private var mSelectedImage: Bitmap? = null
    private var mGraphicOverlay: GraphicOverlay? = null
    // Max width (portrait mode)
    private var mImageMaxWidth: Int? = null
    // Max height (portrait mode)
    private var mImageMaxHeight: Int? = null

    // Functions for loading images from app assets.

    // Returns max image width, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    // Calculate the max width in portrait mode. This is done lazily since we need to
    // wait for
    // a UI layout pass to get the right values. So delay it to first time image
    // rendering time.
    private val imageMaxWidth: Int?
        get() {
            if (mImageMaxWidth == null) {
                mImageMaxWidth = mImageView!!.getWidth()
            }

            return mImageMaxWidth
        }

    // Returns max image height, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    // Calculate the max width in portrait mode. This is done lazily since we need to
    // wait for
    // a UI layout pass to get the right values. So delay it to first time image
    // rendering time.
    private val imageMaxHeight: Int?
        get() {
            if (mImageMaxHeight == null) {
                mImageMaxHeight = mImageView!!.getHeight()
            }

            return mImageMaxHeight
        }

    // Gets the targeted width / height.
    private val targetedWidthHeight: Pair<Int, Int>
        get() {
            val targetWidth: Int
            val targetHeight: Int
            val maxWidthForPortraitMode = imageMaxWidth!!
            val maxHeightForPortraitMode = imageMaxHeight!!
            targetWidth = maxWidthForPortraitMode
            targetHeight = maxHeightForPortraitMode
            return Pair(targetWidth, targetHeight)
        }

    private lateinit var pictureManager: PictureManager
    private lateinit var context: Context
    private var imageUri = ""
    private var imagePathList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        context = this@MainActivity
        pictureManager = PictureManager(this)
        mImageView = findViewById(R.id.image_view)

        mButton = findViewById(R.id.button_text)
        mCloudButton = findViewById(R.id.button_cloud_text)

        mGraphicOverlay = findViewById(R.id.graphic_overlay)
        mButton!!.setOnClickListener {
            runTextRecognition()
        }
        mCloudButton!!.setOnClickListener {
            runCloudTextRecognition()
        }

        spinner.setOnClickListener {
            checkCameraPermissionAndProceed()
        }

    }

    private fun checkCameraPermissionAndProceed() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(context as Activity, arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), PICK_FROM_CAMERA)
        } else {
            openCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PICK_FROM_CAMERA) {
            if (!grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()

            }
        }
    }

    private fun openCamera() {
        pictureManager.startCameraIntent(context) { currentImagePath ->
            if (!currentImagePath.isEmpty()) {
                this.imageUri = currentImagePath
                this.imagePathList.add(this.imageUri)
                if (this.imagePathList.isNotEmpty()) {
                    showCameraThumb(imagePathList)
                }
            }
        }
    }

    private fun showCameraThumb(imagePathList: MutableList<String>) {
        println("lis size: ${imagePathList.size}")

        imagePathList.forEach {
            println("image path:$it")
            //val sd = Environment.getExternalStorageDirectory()
            val image = File(it)
            println("Image:$image")
            val bmOptions = BitmapFactory.Options()
            val bitmap = BitmapFactory.decodeFile(image.absolutePath, bmOptions)
            mSelectedImage = bitmap

            if (mSelectedImage != null) {
                // Get the dimensions of the View
                val (targetWidth, maxHeight) = targetedWidthHeight

                // Determine how much to scale down the image
                val scaleFactor = Math.max(
                        mSelectedImage!!.width.toFloat() / targetWidth.toFloat(),
                        mSelectedImage!!.height.toFloat() / maxHeight.toFloat())

                val resizedBitmap = Bitmap.createScaledBitmap(
                        mSelectedImage!!,
                        (mSelectedImage!!.width / scaleFactor).toInt(),
                        (mSelectedImage!!.height / scaleFactor).toInt(),
                        true)

                Glide.with(context).load(resizedBitmap).into(image_view)

                mSelectedImage = resizedBitmap
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        pictureManager.onActivityResult(requestCode, resultCode, data)
    }

    private fun runTextRecognition() {
        val image = FirebaseVisionImage.fromBitmap(mSelectedImage!!)
        val detector = FirebaseVision.getInstance()
                .visionTextDetector
        mButton?.setEnabled(false)
        detector.detectInImage(image)
                .addOnSuccessListener { texts ->
                    mButton?.setEnabled(true)
                    processTextRecognitionResult(texts)
                }
                .addOnFailureListener(
                        object : OnFailureListener {
                            override fun onFailure(e: Exception) {
                                // Task failed with an exception
                                mButton?.setEnabled(true)
                                e.printStackTrace()
                            }
                        })
    }

    private fun processTextRecognitionResult(texts: FirebaseVisionText) {
        val blocks = texts.blocks
        if (blocks.size == 0) {
            showToast("No text found")
            return
        }
        mGraphicOverlay?.clear()
        for (i in blocks.indices) {
            val lines = blocks[i].lines
            for (j in lines.indices) {
                val elements = lines[j].elements
                for (k in elements.indices) {
                    val textGraphic = TextGraphic(mGraphicOverlay, elements[k])
                    mGraphicOverlay?.add(textGraphic)

                }
            }
        }
    }

    private fun runCloudTextRecognition() {
        graphic_overlay.clear()
        /*       val options = FirebaseVisionCloudDetectorOptions.Builder()
                       .setModelType(FirebaseVisionCloudDetectorOptions.LATEST_MODEL)
                       .setMaxResults(15)
                       .build()
               mCloudButton?.setEnabled(false)
               val image = FirebaseVisionImage.fromBitmap(mSelectedImage!!)
               val detector = FirebaseVision.getInstance()
                       .getVisionCloudDocumentTextDetector(options)
               detector.detectInImage(image)
                       .addOnSuccessListener { texts ->
                           mCloudButton?.setEnabled(true)
                           processCloudTextRecognitionResult(texts)
                       }
                       .addOnFailureListener(
                               object : OnFailureListener {
                                   override fun onFailure(e: Exception) {
                                       // Task failed with an exception
                                       mCloudButton?.setEnabled(true)
                                       e.printStackTrace()
                                   }
                               })*/
    }

    private fun processCloudTextRecognitionResult(text: FirebaseVisionCloudText) {
        if (text == null) {
            showToast("No text found")
            return
        }
        mGraphicOverlay?.clear()
        val pages = text.pages
        for (i in pages.indices) {
            val page = pages[i]
            val blocks = page.blocks
            for (j in blocks.indices) {
                val paragraphs = blocks[j].paragraphs
                for (k in paragraphs.indices) {
                    val paragraph = paragraphs[k]
                    val words = paragraph.words
                    for (l in words.indices) {
                        val cloudTextGraphic = CloudTextGraphic(mGraphicOverlay, words[l])
                        mGraphicOverlay?.add(cloudTextGraphic)
                    }
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }


    companion object {
        private val TAG = "MainActivity"

        private const val PICK_FROM_CAMERA = 1
        fun getBitmapFromAsset(context: Context, filePath: String): Bitmap? {
            val assetManager = context.assets

            val `is`: InputStream
            var bitmap: Bitmap? = null
            try {
                `is` = assetManager.open(filePath)
                bitmap = BitmapFactory.decodeStream(`is`)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return bitmap
        }
    }
}
