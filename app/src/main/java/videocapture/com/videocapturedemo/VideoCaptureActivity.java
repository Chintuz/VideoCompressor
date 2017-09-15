package videocapture.com.videocapturedemo;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.support.v7.view.ContextThemeWrapper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import videocapture.com.videocapturedemo.file.Config;
import videocapture.com.videocapturedemo.file.FileUtils;
import videocapture.com.videocapturedemo.utils.CameraPreview;
import videocapture.com.videocapturedemo.videocompressor.MediaController;

/**
 * Created by hdfc on 15-09-2017.
 */

public class VideoCaptureActivity extends Activity {
    private Camera mCamera;
    private CameraPreview mPreview;
    private MediaRecorder mediaRecorder;
    private ImageView capture, image;
    private Context myContext;
    private FrameLayout cameraPreview;
    private TextView timerTv, policyDetailsTv;
    private ProgressBar progressBar;

    private boolean cameraFront = false;
    private VideoTimer videoTimer;
    private File tempFile;

    // video file directory
    private static String VIDEO_FILE_DIRECTORY;
    public static String videoFilePathWithoutFormat, videoFileName/*, currentTimeInMillis*/;
    public static File compressedDir;
    private ProgressDialog mProgressDialog;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_capture);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        myContext = this;
        initialize();
        videoTimer = new VideoTimer(60000, 1000);

        // set up a file path to store video
        VIDEO_FILE_DIRECTORY = this.getFilesDir().getAbsolutePath() + Config.VIDEO_COMPRESSOR_APPLICATION_DIR_NAME;

        File mp3Dir = new File(VIDEO_FILE_DIRECTORY);

        if (!mp3Dir.exists()) {
            mp3Dir.mkdirs();
        }

        File tempDir = new File(Environment.getExternalStorageDirectory() + "/Video" + Config.VIDEO_COMPRESSOR_TEMP_DIR);

        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        compressedDir = new File(Environment.getExternalStorageDirectory() + "/Video" + Config.VIDEO_COMPRESSOR_COMPRESSED_VIDEOS_DIR);

        try {
            if (compressedDir.mkdir()) {
                System.out.println("Directory created");
            } else {
                System.out.println("Directory is not created");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

//        mprogressBar = (ProgressBar) findViewById(R.id.circular_progress_bar);

    }


    private int findFrontFacingCamera() {
        int cameraId = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i;
                cameraFront = true;
                break;
            }
        }
        return cameraId;
    }

    private int findBackFacingCamera() {
        int cameraId = -1;
        // Search for the back facing camera
        // get the number of cameras
        int numberOfCameras = Camera.getNumberOfCameras();
        // for every camera check
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i;
                cameraFront = false;
                break;
            }
        }
        return cameraId;
    }

    public void onResume() {
        super.onResume();
        if (!hasCamera(myContext)) {
            Toast toast = Toast.makeText(myContext, "Sorry, your phone does not have a camera!", Toast.LENGTH_LONG);
            toast.show();
            finish();
        }
        if (mCamera == null) {
            // if the front facing camera does not exist
            if (findFrontFacingCamera() < 0) {
                Toast.makeText(this, "No front facing camera found.", Toast.LENGTH_LONG).show();
                finish();
            }
            mCamera = Camera.open(findFrontFacingCamera());
            mCamera.setDisplayOrientation(90);

            mPreview.refreshCamera(mCamera);

        }
    }

    public void initialize() {
        cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
        timerTv = (TextView) findViewById(R.id.timer_tv);
        capture = (ImageView) findViewById(R.id.camera);
        image = (ImageView) findViewById(R.id.image_captured);

        mPreview = new CameraPreview(myContext, mCamera);
        cameraPreview.addView(mPreview);

        capture.setOnClickListener(captrureListener);

    }

    @Override
    protected void onPause() {
        super.onPause();
        // when on Pause, release camera in order to be used from other
        // applications
        releaseCamera();
    }

    private boolean hasCamera(Context context) {
        // check if the device has camera
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return true;
        } else {
            return false;
        }
    }

    boolean recording = false;
    boolean isVideoRecordTimeOut = false;
    ObjectAnimator anim = null;
    View.OnClickListener captrureListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            /*if (recording) {
                // stop recording and release camera
                mediaRecorder.stop(); // stop the recording
                releaseMediaRecorder(); // release the MediaRecorder object
                Toast.makeText(VideoCaptureActivity.this, "Video captured!", Toast.LENGTH_LONG).show();
                recording = false;
            } else {*/
            if (!isVideoRecordTimeOut) {
                if (!recording) {
                    if (!prepareMediaRecorder()) {
                        Toast.makeText(VideoCaptureActivity.this, "Fail in prepareMediaRecorder()!\n - Ended -", Toast.LENGTH_LONG).show();
                        finish();
                    }
                    // work on UiThread for better performance
                    runOnUiThread(new Runnable() {
                        public void run() {
                            // If there are stories, add them to the table

                            try {
                                capture.setImageResource(R.drawable.video_record_2);
                                mediaRecorder.start();
                                videoTimer.start();
                            } catch (final Exception ex) {
                                // Log.i("---","Exception in thread");
                            }
                        }
                    });
                    recording = true;
                }
            } else {
                if (recording) {
                    recording = false;
                    // stop recording and release camera
                    mediaRecorder.stop(); // stop the recording
                    videoTimer.cancel();
//                    anim.cancel();
                    releaseMediaRecorder(); // release the MediaRecorder object
                    Toast.makeText(VideoCaptureActivity.this, "Video captured!", Toast.LENGTH_LONG).show();
                    compressAndProceedVideo();
                }
            }
        }
    };

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset(); // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            mediaRecorder = null;
            mCamera.lock(); // lock camera for later use
        }
    }

    private boolean prepareMediaRecorder() {

        mediaRecorder = new MediaRecorder();

        mCamera.unlock();
        mediaRecorder.setCamera(mCamera);
        mediaRecorder.setOrientationHint(270);

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_LOW));

        videoFileName = "VideoCapture";

        videoFilePathWithoutFormat = VIDEO_FILE_DIRECTORY +
                "VideoCapture".trim();

        mediaRecorder.setOutputFile(videoFilePathWithoutFormat + Config.VIDEO_FILE_FORMAT);

        mediaRecorder.setMaxDuration(600000); // Set max duration 60 sec.
        mediaRecorder.setMaxFileSize(50000000); // Set max file size 50M

        mediaRecorder.setVideoFrameRate(15);

        // changes for video
//        mediaRecorder.setVideoFrameRate(12);
//        mediaRecorder.setVideoEncodingBitRate(56);
//        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP);

//        mediaRecorder.setAudioEncodingBitRate(24);
//        mediaRecorder.setAudioChannels(1);
//        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);


//        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
//        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        /*mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoSize(640,480);
        mediaRecorder.setVideoFrameRate(12);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);*/


//        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
//        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
//        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//        mediaRecorder.setVideoSize(640,480);
//        mediaRecorder.setVideoFrameRate(12);
//        mediaRecorder.setVideoEncodingBitRate(56);
//        mediaRecorder.setAudioEncodingBitRate(24);
//        mediaRecorder.setAudioChannels(1);
//        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
//        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);


        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private void releaseCamera() {
        // stop and release camera
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private class VideoTimer extends CountDownTimer {

        public VideoTimer(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            int sec = 60 - Integer.parseInt(String.valueOf(millisUntilFinished / 1000));
            if (String.valueOf(sec).length() == 1) {
                timerTv.setText("00:0" + sec + "");
            } else if (String.valueOf(sec).length() == 2) {
                timerTv.setText("00:" + sec + "");
            }
            if (sec == 30) {
                isVideoRecordTimeOut = true;
            }
        }

        @Override
        public void onFinish() {
            if (recording) {
                recording = false;
                if (mediaRecorder != null) {
                    mediaRecorder.stop();
                    releaseCamera();
                    Toast.makeText(VideoCaptureActivity.this, "Video Recorded", Toast.LENGTH_LONG).show();
                    compressAndProceedVideo();
                }
            }
        }
    }

    private void compressAndProceedVideo() {
        int fileSize = (int) new File(videoFilePathWithoutFormat + Config.VIDEO_FILE_FORMAT).length() / 1024;
        if (fileSize > 700) {
            MediaController.isCompressed = true;
            tempFile = FileUtils.saveTempFile(videoFileName + Config.VIDEO_COMPRESSOR_TEMP_FILE_EXTENSION + Config.VIDEO_FILE_FORMAT, VideoCaptureActivity.this,
                    Uri.fromFile(new File(videoFilePathWithoutFormat + Config.VIDEO_FILE_FORMAT)));

            new VideoCompressor().execute();

        } else {
            MediaController.isCompressed = false;

            try {
                copyDirectory(new File(videoFilePathWithoutFormat + Config.VIDEO_FILE_FORMAT), new File(compressedDir, videoFileName + Config.VIDEO_FILE_FORMAT));
            } catch (IOException e) {
                e.printStackTrace();
            }
            proceedVideo(new File(videoFilePathWithoutFormat + Config.VIDEO_FILE_FORMAT).getAbsolutePath());

        }
    }

    private void proceedVideo(String filePath) {
        // uncomment this line to get string video format
//                    String videoStr = convertVideoToBase64(new File("/sdcard/myvideo.mp4"));
//        Bitmap thumb = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND);
        Bitmap thumb = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(filePath);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long time = Long.valueOf(duration) / 2;
            thumb = retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (IllegalArgumentException ex) {
            // Assume this is a corrupt video file
            thumb = null;
        } catch (RuntimeException ex) {
            // Assume this is a corrupt video file.
            thumb = null;
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                // Ignore failures while cleaning up.
            }
        }
        image.setImageBitmap(thumb);

        startActivity(new Intent(this, VideoCaptureActivity.class));
        finish();

    }

    private void deleteTempFile() {
        if (tempFile != null && tempFile.exists()) {
//            tempFile.delete();
        }
    }

    @Override
    public void onBackPressed() {
//        super.onBackPressed();
//        deleteTempFile();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deleteTempFile();
    }


    private class VideoCompressor extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressDialog("Processing Video..");
            Log.d("Syso", "Start video compression");
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            return MediaController.getInstance(VideoCaptureActivity.this).convertVideo(tempFile.getPath());
        }

        @Override
        protected void onPostExecute(Boolean compressed) {
            super.onPostExecute(compressed);
            dissmissProgressDialog();
            if (compressed) {
                Log.d("Syso", "Compression successfully!");
                MediaController.isCompressed = true;
                Toast.makeText(VideoCaptureActivity.this, "Compression Success", Toast.LENGTH_SHORT).show();
            } else {
                MediaController.isCompressed = false;
                Toast.makeText(VideoCaptureActivity.this, "Compression failed", Toast.LENGTH_SHORT).show();
                try {
                    copyDirectory(new File(videoFilePathWithoutFormat + Config.VIDEO_FILE_FORMAT), new File(compressedDir, videoFileName + Config.VIDEO_FILE_FORMAT));
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
           /* if (MediaController.cacheFile.exists())
                MediaController.cacheFile.delete();*/

            proceedVideo(videoFilePathWithoutFormat + Config.VIDEO_FILE_FORMAT);
        }
    }

    public void copyDirectory(File sourceLocation, File targetLocation) throws IOException {

        if (sourceLocation.isDirectory()) {
            if (!targetLocation.exists() && !targetLocation.mkdirs()) {
                throw new IOException("Cannot create dir " + targetLocation.getAbsolutePath());
            }

            String[] children = sourceLocation.list();
            for (int i = 0; i < children.length; i++) {
                copyDirectory(new File(sourceLocation, children[i]), new File(targetLocation, children[i]));
            }
        } else {

            // make sure the directory we plan to store the recording in exists
            File directory = targetLocation.getParentFile();
            if (directory != null && !directory.exists() && !directory.mkdirs()) {
                throw new IOException("Cannot create dir " + directory.getAbsolutePath());
            }

            InputStream in = new FileInputStream(sourceLocation);
            OutputStream out = new FileOutputStream(targetLocation);

            // Copy the bits from instream to outstream
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        }
    }

    private void showProgressDialog(String msg) {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(new ContextThemeWrapper(VideoCaptureActivity.this, android.R.style.Theme_DeviceDefault_Light_Dialog));//Theme_DeviceDefault_Light_Dialog
        }
        mProgressDialog.setTitle("");
        mProgressDialog.setMessage(msg);
        mProgressDialog.setCancelable(false);
        mProgressDialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

        if (mProgressDialog != null && !mProgressDialog.isShowing())
            mProgressDialog.show();
    }

    private void dissmissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

}