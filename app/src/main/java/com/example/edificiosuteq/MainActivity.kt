package com.example.edificiosuteq
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Size
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.CameraSelector
import androidx.camera.core.impl.ImageReaderProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import camerax.AutoFitTextureView
import camerax.AutoFitTextureView


import java.util.concurrent.Executor
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity(), ImageReader.OnImageAvailableListener {

    companion object {
        private const val REQUEST_CAMERA = 111
        private const val REQUEST_GALLERY = 222
    }

    private val labels = mutableListOf<String>("Polideportivo",
        "Facultad de ciencias empresariales",
        "Facultad de ciencias sociales, económicas y financieras",
        "Comedor",
        "Parqueadero",
        "Facultad de ciencias de la salud",
        "Rectorado",
        "Departamento de archivos",
        "Facultad de ciencias de pedagogía",
        "Departamento médico",
        "Departamento de investigación",
        "Biblioteca",
        "Bar",
        "Departamento médico",
        "Auditorio",
        "Departamento de informática",
        "Rotonda"

    )

    private val PROBABILIDAD_VALIDA = 95f
    private val TAMANIO_IMAGEN = 224
    private var permisosNoAprobados = ArrayList<String>()
    private lateinit var txtResult: TextView
    private lateinit var tts: TextToSpeech
    private var prediccionModelo: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val permisosRequeridos = arrayListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )

        txtResult = findViewById(R.id.txtresult)

        abrirCamera()

        permisosNoAprobados = getPermisosNoAprobados(permisosRequeridos)

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale("spa", "MEX")
            }
        }
    }



    private fun getPermisosNoAprobados(listaPermisos: ArrayList<String>): ArrayList<String> {
        val list = ArrayList<String>()
        var habilitado: Boolean

        if (Build.VERSION.SDK_INT >= 23) {
            for (permiso in listaPermisos) {
                if (ContextCompat.checkSelfPermission(this, permiso) != PackageManager.PERMISSION_GRANTED) {
                    list.add(permiso)
                    habilitado = false
                } else {
                    habilitado = true
                }

                if (permiso == android.Manifest.permission.CAMERA) {
                    // btnCamara.isEnabled = habilitado
                } else if (permiso == android.Manifest.permission.MANAGE_EXTERNAL_STORAGE ||
                    permiso == android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) {
                    // btnGaleria.isEnabled = habilitado
                }
            }
        }

        return list
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        for (i in permissions.indices) {
            if (permissions[i] == android.Manifest.permission.MANAGE_EXTERNAL_STORAGE ||
                permissions[i] == android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) {
                // Handle storage permissions
            }
        }
    }

    override fun onImageAvailable(imageReader: ImageReader) {
        // Handle available image here
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val cameraExecutor = Executors.newSingleThreadExecutor()

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(findViewById<PreviewView>(R.id.container).surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                val camera = cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                // Add your code to handle camera capture and processing here

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun Categorizar(imagen: Bitmap) {
        try {
            val localModel = LocalModel.Builder()
                .setAssetFilePath("model.tflite")
                .build()

            val recognizer = TextRecognition.getClient(null);

            val inputImage = InputImage.fromBitmap(imagen, 0)
            val result = recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val resultado = visionText.text
                    if (resultado.isNotBlank()) {
                        prediccionModelo = resultado
                        if (txtResult.text != resultado) {
                            tts.speak(resultado, TextToSpeech.QUEUE_FLUSH, null, null)
                        }
                        txtResult.text = resultado
                    }
                }
                .addOnFailureListener { e ->
                    txtResult.text =  e.message
                }
        } catch (e: Exception) {
            txtResult.text = e.message
        }
    }

    var precicion: FloatArray = [98]
        private set

    var datosModelo: ArrayList<Array<Any>> = ArrayList()
    var rutaEtiquetas: String = ""


    fun PriorizarModelo() {
        val zipped = precicion.zip(labels)
        val sorted = zipped.sortedByDescending { it.first }

        for (i in sorted.indices) {
            precicion[i] = sorted[i].first
            labels[i] = sorted[i].second
        }
    }

    private fun abrirCamera() {
        if (permisosNoAprobados.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permisosNoAprobados.toTypedArray(),
                100
            )
        } else {
            startCamera()
        }
    }

    // Add your camera capture and processing functions here
}
