package com.example.camerax_qr_deeplink;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.Surface;
import android.widget.Toast;

import com.example.camerax_qr_deeplink.databinding.ActivityCameraBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class CameraActivity extends AppCompatActivity {
    ActivityCameraBinding binding;
    ImageCapture imageCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCameraBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        askPermissions();

        if(allPermissions()) {
            startCamera();
        } else {
            askPermissions();
        }

        binding.takePhoto.setOnClickListener(v -> {
            takePhoto();
        });
    }

    private void takePhoto() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        File file = null;

        try {
            file = File.createTempFile(dateFormat.format(new Date()), ".jpg", getDirectoryName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Toast.makeText(CameraActivity.this, "SAVES OK", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(CameraActivity.this, "SAVES BAD", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private File getDirectoryName() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return dir;
    }

    public void startCamera () {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener((Runnable) () -> {
            try {
                ProcessCameraProvider cameraProvider = (ProcessCameraProvider) cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.pv.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                //.addCameraFilter() //доп. параметры
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(Surface.ROTATION_0)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new QRCodeImageAnalyzer(new QRCodeFoundListener() {
            @Override
            public void onQRCodeFound(String qrCode) {
                Intent intent = new Intent();
                intent.putExtra("QR_INFO", qrCode);
                setResult(RESULT_OK, intent);
                finish();
            }

            @Override
            public void onQRCodeNotFound() {

            }
        }));

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
    }

    String[] PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};

    private ActivityResultLauncher<String[]> requestLauncher =
            //запрос нескольких разрешений
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                //CallBack
                AtomicBoolean permissionGranted = new AtomicBoolean(true);
                permissions.forEach((key, value) -> {
                    if (Arrays.asList(PERMISSIONS).contains(key) && !value){
                        Toast.makeText(this, key+" "+value, Toast.LENGTH_SHORT).show();
                        permissionGranted.set(false);
                    }
                });
                if(!permissionGranted.get()){
                    Toast.makeText(this,
                            "Permission request denied",
                            Toast.LENGTH_SHORT).show();
                } else {
                    startCamera();
                }
            });

    public boolean allPermissions() {
        AtomicBoolean good = new AtomicBoolean();
        Arrays.asList(PERMISSIONS).forEach(it -> {
            if(ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED){
                good.set(false);
            }
        });
        return good.get();
    }

    public void askPermissions(){
        requestLauncher.launch(PERMISSIONS);
    }
}