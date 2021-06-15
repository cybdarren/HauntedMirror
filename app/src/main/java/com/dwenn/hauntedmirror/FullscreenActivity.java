package com.dwenn.hauntedmirror;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.impl.ImageAnalysisConfig;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.dwenn.hauntedmirror.databinding.ActivityFullscreenBinding;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {
    private static final String TAG = "HauntedMirror";
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;
    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;

    private PreviewView cameraPreview;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private static final String[] CAMERA_PERMISSION = new String[]
            {Manifest.permission.CAMERA};
    private static final int CAMERA_REQUEST_CODE = 10;

    private static ImageView ocvImage;
    private static TextView textView;
    private static VideoView videoView;
    private static SeekBar seekBar;

    private static int MOTION_MIN = 5;
    private static int MOTION_THRESHOLD_DEFAULT = 25;
    private static int motionThreshold = MOTION_THRESHOLD_DEFAULT;

    private static SharedPreferences sharedPref;

    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    private View mControlsView;

    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (AUTO_HIDE) {
                        delayedHide(AUTO_HIDE_DELAY_MILLIS);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    break;
                default:
                    break;
            }
            return false;
        }
    };

    private ActivityFullscreenBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFullscreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        /////////////////////////////////////////////////////////////
        // configure opencv
        OpenCVLoader.initDebug();

        //////////////////////////////////////////////////////////////
        // shared preferences
        sharedPref = getPreferences(Context.MODE_PRIVATE);
        motionThreshold = sharedPref.getInt(getString(R.string.motion_threshold_key), MOTION_THRESHOLD_DEFAULT);

        /////////////////////////////////////////////////////////////
        // set the screen brightness to maximum
        if (!canWrite()) {
            allowWritePermission();
        }
        if (canWrite()) {
            setBrightness(255);
        }

        cameraPreview = findViewById(R.id.cameraPreview);
        ocvImage = findViewById(R.id.ocvImage);

        // configure the video player without media controller
        videoView = findViewById(R.id.videoView);
        // MediaController mediaController = new MediaController(this);
        // mediaController.setAnchorView(videoView);
        Uri uriPath = Uri.parse("android.resource://com.dwenn.hauntedmirror/" + R.raw.pg1);
        videoView.setVideoPath(uriPath.toString());
        // videoView.setMediaController(mediaController);

        /////////////////////////////////////////////////////////////
        // Request camera permissions
        if (hasCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    CAMERA_PERMISSION,
                    CAMERA_REQUEST_CODE
            );
        }

        mVisible = true;
        mControlsView = binding.fullscreenContentControls;
        mContentView = binding.fullscreenContent;

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        seekBar = findViewById(R.id.seekBar);
        seekBar.setMax(50 - MOTION_MIN);
        seekBar.setProgress(motionThreshold - MOTION_MIN);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                motionThreshold = seekBar.getProgress() + MOTION_MIN;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // write to the shared preferences
                motionThreshold = seekBar.getProgress() + MOTION_MIN;
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putInt(getString(R.string.motion_threshold_key), motionThreshold);
                editor.apply();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        binding.dummyButton.setOnTouchListener(mDelayHideTouchListener);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            // bind the lifecycle of cameras to the lifecycle owner
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // select the front camera
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                // create the analyzer

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

                // run the imageAnalysis on a separate thread
                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), new AggregateLumaMotionDetector());

                OrientationEventListener orientationEventListener = new OrientationEventListener(this) {
                    @Override
                    public void onOrientationChanged(int orientation) {

                    }
                };
                orientationEventListener.enable();

                // create the preview
                Preview preview = new Preview.Builder()
                        .build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis, preview);


            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to bind camera use case");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private class AggregateLumaMotionDetector implements ImageAnalysis.Analyzer {
        private Mat dilateKernel = Imgproc.getStructuringElement(Imgproc.CV_SHAPE_RECT, new Size(2.0, 2.0));
        private long lastAnalyzedTimestamp = 0L;
        private Mat previousMat = new Mat();

        @Override
        public void analyze(@NonNull ImageProxy image) {
            boolean motionDetect = false;

            // throttle the detection rate
            long currentTimestamp = System.currentTimeMillis();

            if (currentTimestamp - lastAnalyzedTimestamp >= TimeUnit.MILLISECONDS.toMillis(200)) {
                @SuppressLint("UnsafeOptInUsageError") Image.Plane[] planes = image.getImage().getPlanes();
                ByteBuffer yBuffer = planes[0].getBuffer();
                int ySize = yBuffer.remaining();
                byte[] lumaBuffer = new byte[ySize];
                yBuffer.get(lumaBuffer, 0, ySize);

                // construct an openCV Mat from the luma data
                Mat screenMat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC1);
                screenMat.put(0, 0, lumaBuffer);

                Mat intermediateMat = new Mat();
                // decimate the mat down to 1/64 size
                Imgproc.resize(screenMat, intermediateMat, new Size(0.0, 0.0), 0.03125, 0.03125, Imgproc.INTER_AREA);
                // perform blur
                Imgproc.GaussianBlur(intermediateMat, intermediateMat, new Size(5.0, 5.0), 0.0);
                Mat returnMat = intermediateMat;

                // only compare between active frames
                if (!previousMat.empty()) {
                    // get the difference between the two frames
                    Mat matDelta = new Mat();
                    Core.absdiff(intermediateMat, previousMat, matDelta);
                    Imgproc.threshold(matDelta, matDelta, 10.0, 255.0, Imgproc.THRESH_BINARY);
                    Imgproc.dilate(matDelta, matDelta, dilateKernel);

                    // get the number of moving pixels
                    int nonZero = Core.countNonZero(matDelta);
                    if (nonZero > motionThreshold)
                        motionDetect = true;
                    returnMat = matDelta;
                }

                // save the Mat for the next time
                previousMat = intermediateMat;

                // resize the mat back to full screen
                Imgproc.resize(returnMat, screenMat, new Size(image.getWidth(), image.getHeight()), 0.0, 0.0, Imgproc.INTER_NEAREST);
                Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(screenMat, bitmap);

                // set the diagnostic bitmap
                runOnUiThread(() -> ocvImage.setImageBitmap(bitmap));

                // Trigger the video
                if (motionDetect && !videoView.isPlaying()) {
                    Log.d(TAG, "Video started");
                    videoView.start();
                }

                lastAnalyzedTimestamp = currentTimestamp;
            }

            image.close();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private void show() {
        // Show the system bar
        //noinspection deprecation
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    private boolean canWrite() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.System.canWrite(this);
        } else {
            return true;
        }
    }

    private void allowWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"));
            startActivity(intent);
        }
    }

    private void setBrightness(int value) {
        Settings.System.putInt(this.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
    }
}