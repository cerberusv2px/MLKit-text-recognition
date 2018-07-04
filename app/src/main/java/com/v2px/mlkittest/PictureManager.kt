package com.v2px.mlkittest

/**
 * Created by krishna on 5/17/18.
 */
import android.app.Activity

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.support.v4.content.FileProvider
import android.util.Log

import java.io.File
import java.io.IOException
import java.util.*


/**
 * Created by krishna on 4/10/18.
 */

class PictureManager(val host: Any) : PictureCallback {
    val FROM_GALLERY = 700
    val FROM_CAMERA = 800
    var cameraImagePath: String? = null
    var context: Context


    val EXTRA_ABSOLUTE_FILE_PATH = "absolute-path"
    private val TAG = "PictureManager"
    lateinit var imagePath: (String) -> Unit

    init {
        if (host is Activity) {
            context = host
        } else if (host is Fragment) {
            context = host.context!!
        } else {
            throw Exception("Host either can be Activity or Fragment")
        }
    }

    override fun startGalleryIntent(context: Context?, getSavedPath: (String) -> Unit) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        when (context) {
            is Activity -> context.startActivityForResult(intent, FROM_CAMERA)
            is Fragment -> context.startActivityForResult(intent, FROM_CAMERA)
        }
    }

    override fun startCameraIntent(context: Context?, getSavedPath: (String) -> Unit) {
        val fileName = createFileName()
        imagePath = getSavedPath
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.resolveActivity(context?.packageManager).let {
            val imageFile = createImageFile(context!!, fileName)
            if (imageFile != null) {
                var uri: Uri
                if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.N)) {
                    uri = Uri.fromFile(imageFile)
                } else {
                    uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", imageFile)

                }
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                intent.putExtra(EXTRA_ABSOLUTE_FILE_PATH, imageFile.absolutePath)
                cameraImagePath = imageFile.absolutePath

            }

        }

        when (host) {
            is Activity -> host.startActivityForResult(intent, FROM_CAMERA)
            is Fragment -> host.startActivityForResult(intent, FROM_CAMERA)
        }


    }


    override fun getImagePathFromUri(context: Context, uri: Uri): String {
        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]

                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }

                // Good job vivo
                if ("5D68-9217".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {

                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)!!)

                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]

                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])

                return getDataColumn(context, contentUri, selection, selectionArgs)
            }// MediaProvider
            // DownloadsProvider
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.getPath()
        }// File
        // MediaStore (and general)

        throw Exception("Image not found exception")
    }

    private fun isDocumentUri(context: Context, uri: Uri): Boolean {
        return DocumentsContract.isDocumentUri(context, uri)
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }


    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    private fun getDataColumn(context: Context?, uri: Uri?, selection: String?,
                              selectionArgs: Array<String>?): String {

        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)

        try {
            cursor = context?.contentResolver?.query(uri!!, projection, selection, selectionArgs, null)
            var path: String? = null
            if (cursor != null && cursor.moveToFirst()) {
                val column_index = cursor.getColumnIndexOrThrow(column)
                path = cursor.getString(column_index)
            }
            return path.let { path } ?: throw Exception("Image Not Found Exception")
        } finally {
            if (cursor != null)
                cursor.close()
        }
    }


    /**
     * Create image file of given name in the app private folder
     *
     * @param context  {[Context]}
     * @param fileName name of file to be created
     * @return the {[File]} instance
     */
    private fun createImageFile(context: Context, fileName: String): File? {
        var image: File? = null
        // Create an image file name
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) // for private folder
        val appDirectory = getDocumentPath(context)
        if (!appDirectory.exists()) {
            if (!appDirectory.mkdir()) return image
        }
        Log.e(TAG, "storageDir: " + storageDir!!.absolutePath)
        try {
            image = File.createTempFile(fileName, ".jpg", appDirectory)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return image
    }

    /**
     * create file name for the given monitoring of the given project.
     *
     * @return {[String]} obtained after concatenation of timestamp with projectId and serverId
     */
    private fun createFileName(): String {
        return Date().time.toString()
    }

    private fun getDocumentPath(context: Context): File {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) // for private folder
        return File(storageDir, context.getString(R.string.app_name))
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        if (resultCode != Activity.RESULT_OK) {
            imagePath("")
            return
        }

        when (requestCode) {
            FROM_GALLERY -> {
                val path = if (host is Fragment) {
                    getImagePathFromUri(host.context!!, data!!.data)
                } else {
                    getImagePathFromUri(host as Activity, data!!.data)
                }

                imagePath(path)
                return
            }
            FROM_CAMERA -> imagePath(cameraImagePath ?: "file is not available")
        }


    }


}



interface PictureCallback {
    fun startGalleryIntent(context: Context?,getSavedPath: (String) -> Unit)
    fun startCameraIntent(context: Context?,getSavedPath:(String)->Unit)
    @Throws(Exception::class)
    fun getImagePathFromUri( context:Context,  uri:Uri):String
}
