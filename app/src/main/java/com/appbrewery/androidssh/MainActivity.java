
package com.appbrewery.androidssh;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.appbrewery.androidssh.dialogs.SshConnectFragmentDialog;
import com.appbrewery.androidssh.sshutils.ConnectionStatusListener;
import com.appbrewery.androidssh.sshutils.ExecTaskCallbackHandler;
import com.appbrewery.androidssh.sshutils.SessionController;
import com.jcraft.jsch.SftpProgressMonitor;

/**
 * Main activity. Connect to SSH server and launch command shell.
 *
 */
public class MainActivity extends Activity implements OnClickListener {


    ///// Camera 2 api///

    private static final String TAG2 = "AndroidCameraApi";
     Button takePictureButton;
     TextureView textureView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;


    ////// End Camera Apt





    private static final String TAG = "MainActivity";
    private TextView mConnectStatus;

    private SshEditText mCommandEdit;
    private Button mButton, mEndSessionBtn, mSftpButton, customButton , cameraButton;

    private Handler mHandler;
    private Handler mTvHandler;
    private String mLastLine;

    EditText customFolderName,timerSettingsEdittext;

    String currentPhotoPath;
    Boolean btnCliked = false;

    Boolean dialogBoxCancelled = false;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);



        /* This code together with the one in onDestroy()
         * will make the screen be always on until this Activity gets destroyed. */
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        /// Camera Api

        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        takePictureButton = (Button) findViewById(R.id.btn_takepicture);
        assert takePictureButton != null;
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

        ///// Camera Api



        // Set no title
        mButton = (Button) findViewById(R.id.enterbutton);
        mEndSessionBtn = (Button) findViewById(R.id.endsessionbutton);
        mSftpButton = (Button) findViewById(R.id.sftpbutton);

        customButton = (Button) findViewById(R.id.customizedBtn);

        cameraButton = (Button) findViewById(R.id.cameraBtn);

        customFolderName = (EditText) findViewById(R.id.customFolderName);
        timerSettingsEdittext = (EditText) findViewById(R.id.timerSettingsEdittext);


        mCommandEdit = (SshEditText) findViewById(R.id.command);
        mConnectStatus = (TextView) findViewById(R.id.connectstatus2);
        // set onclicklistener
        mButton.setOnClickListener(this);
        mEndSessionBtn.setOnClickListener(this);
        mSftpButton.setOnClickListener(this);
        customButton.setOnClickListener(this);
        cameraButton.setOnClickListener(this);

        mConnectStatus.setText("NOT CONNECTED");
        //handlers
        mHandler = new Handler();
        mTvHandler = new Handler();

        //text change listener, for getting the current input changes.
        mCommandEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String[] sr = editable.toString().split("\r\n");
                String s = sr[sr.length - 1];
                mLastLine = s;

            }
        });


        mCommandEdit.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        //Log.d(TAG, "editor action " + event);
                        if (isEditTextEmpty(mCommandEdit)) {
                            return false;
                        }

                        // run command
                        else {
                            if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) {
                                return false;
                            }
                            // get the last line of terminal
                            String command = getLastLine();
                            ExecTaskCallbackHandler t = new ExecTaskCallbackHandler() {
                                @Override
                                public void onFail() {
                                    makeToast(R.string.taskfail);
                                }

                                @Override
                                public void onComplete(String completeString) {
                                }
                            };
                            mCommandEdit.AddLastInput(command);
                            SessionController.getSessionController().executeCommand(mHandler, mCommandEdit, t, command);
                            return false;
                        }
                    }
                }
        );
    }

    ///// Timer Task///


    class MyTimerTask extends TimerTask {

        public void run() {

            runOnUiThread(new Runnable() {

                @Override
                public void run() {


                    //// Do every X time///
                    takePicture();








                }
            });


        }
    }


    /// End Time Task ///


    ///// Uploading to server ///


    public void uploadingToServer()
    {

        final SessionController mSessionController;

        mSessionController = SessionController.getSessionController();
        mSessionController.connect();

        // String pathtoImage = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()+"/Camera/asdf.jpg";

      //  final File file = new File(Environment.getExternalStorageDirectory() + "/pic.jpg");
        String pathtoImage = currentPhotoPath;

        File   mRootFile = new File(pathtoImage);
        final ArrayList<File> mFilenames = new ArrayList<File>();

        mFilenames.add(mRootFile);

        final SftpProgressDialog progressDialog = new SftpProgressDialog(MainActivity.this, 0);
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "STOP", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {


                ////// Cancel
                // File[] arr2 = {mFilenames.get(0)};

                String filename=mFilenames.get(0).toString().substring(mFilenames.get(0).toString().lastIndexOf("/")+1);

                mSessionController.deleteFiles(filename,customFolderName.getText().toString());

                Toast.makeText(MainActivity.this,"Image Capture Canceled!",Toast.LENGTH_LONG).show();

                dialogBoxCancelled=true;

                MyTimerTask task = new MyTimerTask();
                task.cancel();
              //  recreate();

                dialog.dismiss();
                ////// Cancel
            }
        });


        if(dialogBoxCancelled==false){
            progressDialog.show();
            File[] arr = {mFilenames.get(0)};
            mSessionController.uploadFiles(arr,progressDialog,customFolderName.getText().toString());
        }








    }


    /////////////////////////////





    ///// Camera Api


    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG2, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };
    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void takePicture() {
        if (null == cameraDevice) {
            Log.e(TAG2, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            //final File file = new File(Environment.getExternalStorageDirectory() + "/pic.jpg");
                File filew = createImageFile();
           final File file = new File(currentPhotoPath);

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);

                        uploadingToServer();


                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this,"Captured!", Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };



            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);



        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG2, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
//            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
//                return;
//            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG2, "openCamera X");
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG2, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG2, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.e(TAG2, "onPause");
        //closeCamera();
        stopBackgroundThread();
    }

    ///// Camera Api
















    /**
     * Displays toast to user.
     *
     * @param text
     */

    private void makeToast(int text) {
        Toast.makeText(this, getResources().getString(text), Toast.LENGTH_SHORT).show();
    }

    /**
     * Start activity to do SFTP transfer. User will choose from list of files
     * to transfer.
     */
    private void startSftpActivity() {
        Intent intent = new Intent(this, FileListActivity.class);
        String[] info = {
                SessionController.getSessionController().getSessionUserInfo().getUser(),
                SessionController.getSessionController().getSessionUserInfo().getHost(),
                SessionController.getSessionController().getSessionUserInfo().getPassword()
        };

        intent.putExtra("UserInfo", info);

        startActivity(intent);
    }

    /**
     * @return
     */
    private String getLastLine() {
        int index = mCommandEdit.getText().toString().lastIndexOf("\n");
        if (index == -1) {
            return mCommandEdit.getText().toString().trim();
        }
        if(mLastLine == null){
            Toast.makeText(this, "no text to process", Toast.LENGTH_LONG);
            return "";
        }
        String[] lines = mLastLine.split(Pattern.quote(mCommandEdit.getPrompt()));
        String lastLine = mLastLine.replace(mCommandEdit.getPrompt().trim(), "");
        Log.d(TAG, "command is " + lastLine + ", prompt is  " + mCommandEdit.getPrompt());
        return lastLine.trim();
    }

    private String getSecondLastLine() {

        String[] lines = mCommandEdit.getText().toString().split("\n");
        if (lines == null || lines.length < 2) return mCommandEdit.getText().toString().trim();

        else {
            int len = lines.length;
            String ln = lines[len - 2];
            return ln.trim();
        }
    }

    /**
     * Checks if the EditText is empty.
     *
     * @param editText
     * @return true if empty
     */
    private boolean isEditTextEmpty(EditText editText) {
        if (editText.getText() == null || editText.getText().toString().equalsIgnoreCase("")) {
            return true;
        }
        return false;
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }


    public void onClick(View v) {
        if (v == mButton) {
           showDialog();

        } else if (v == mSftpButton) {
            if (SessionController.isConnected()) {

                startSftpActivity();


            }
        } else if (v == this.mEndSessionBtn) {
            try {
                if (SessionController.isConnected()) {
                    SessionController.getSessionController().disconnect();
                }
            } catch (Throwable t) { //catch everything!
                Log.e(TAG, "Disconnect exception " + t.getMessage());
            }

        }
        else if (v == customButton) {

            Timer timer = new Timer();

            if (SessionController.isConnected()) {


                if (btnCliked==false)
                {



                    if(customFolderName.getText().toString().length() ==0 && timerSettingsEdittext.getText().toString().length()==0){



                        timerSettingsEdittext.setError("Please Enter Time");
                        customFolderName.setError("Please Enter Folder Name");

                    }
                    else {

                        btnCliked = true;
                        customButton.setText("STOP");

                        ///// Take Picture ///
                        ///// Timer Task///
                        int timerTime = Integer.parseInt(timerSettingsEdittext.getText().toString()) ;

                        int minutesInMiliseconds = timerTime * 60 * 1000;

                        MyTimerTask task = new MyTimerTask();
                        timer.schedule(task,0,minutesInMiliseconds);
                        ///// End Timer Task
                        /////

                    }




                }
                else
                {
                    btnCliked = false;
                    customButton.setText("START");



                    final SessionController mSessionController;

                    mSessionController = SessionController.getSessionController();
                    try {
                        mSessionController.disconnect();
//                        task.cancel();
//                        timer.cancel();
                        recreate();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

//                    // String pathtoImage = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()+"/Camera/asdf.jpg";
//                    String pathtoImage = currentPhotoPath;
//
//                    File   mRootFile = new File(pathtoImage);
//                    final ArrayList<File> mFilenames = new ArrayList<File>();
//
//                    mFilenames.add(mRootFile);
//
//                    final SftpProgressDialog progressDialog = new SftpProgressDialog(MainActivity.this, 0);
//                    progressDialog.setIndeterminate(false);
//                    progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
//
//                    progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "STOP", new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//
//
//                            ////// Cancel
//                           // File[] arr2 = {mFilenames.get(0)};
//
//                            String filename=mFilenames.get(0).toString().substring(mFilenames.get(0).toString().lastIndexOf("/")+1);
//
//                            mSessionController.deleteFiles(filename,customFolderName.getText().toString());
//
//                            Toast.makeText(MainActivity.this,"File Transfer Canceled!",Toast.LENGTH_LONG).show();
//
//                            dialog.dismiss();
//
//                            ////// Cancel
//                        }
//                    });
//
//                    progressDialog.show();
//
//                    File[] arr = {mFilenames.get(0)};
//                    mSessionController.uploadFiles(arr,progressDialog,customFolderName.getText().toString());



                }


            }
            else{


                AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
                builder1.setMessage("Server is Offline, Please Connect to Server!");
                builder1.setCancelable(true);

                builder1.setPositiveButton(
                        "CONNECT",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                showDialog();
                            }
                        });

                builder1.setNegativeButton(
                        "CANCEL",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });

                AlertDialog alert11 = builder1.create();
                alert11.show();




            }
        }
        else if (v == cameraButton) {
            if (SessionController.isConnected()) {

                ////Opens the camera
                final int REQUEST_IMAGE_CAPTURE = 1;

                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {


                    File photoFile = null;
                    try {
                        photoFile = createImageFile();


                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (photoFile != null) {

                        Uri photoURI = FileProvider.getUriForFile(this, "com.appbrewery.androidssh.fileprovider", photoFile);
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

                        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);


                    }
                }

                ////Opens the camera

                //// Unique file name

               // Toast.makeText(this,currentPhotoPath,Toast.LENGTH_LONG).show();



            }
        }

    }



    ///

    void showDialog() {

        try {
            if (SessionController.isConnected()) {
                SessionController.getSessionController().disconnect();
            }
        } catch (Throwable t) { //catch everything!
            Log.e(TAG, "Disconnect exception " + t.getMessage());
        }


        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");

        ft.addToBackStack(null);

        // Create and show the dialog.
        SshConnectFragmentDialog newFragment = SshConnectFragmentDialog.newInstance();
        newFragment.setListener(new ConnectionStatusListener() {
            @Override
            public void onDisconnected() {

                mTvHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mConnectStatus.setText("NOT CONNECTED");
                    }
                });
            }

            @Override
            public void onConnected() {

                mTvHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mConnectStatus.setText("CONNECTED");
                    }
                });
            }
        });

        newFragment.show(ft, "dialog");
    }


    private class SftpProgressDialog extends ProgressDialog implements SftpProgressMonitor {

        /**
         * Size of file to transfer
         */
        private long mSize = 0;
        /**
         * Current progress count
         */
        private long mCount = 0;

        /**
         * Constructor
         *
         * @param context
         * @param theme
         */

        public SftpProgressDialog(Context context, int theme) {
            super(context, theme);
            // TODO Auto-generated constructor stub
        }

        //
        // SftpProgressMonitor methods
        //

        /**
         * Gets the data uploaded since the last count.
         */
        public boolean count(long arg0) {
            mCount += arg0;
            this.setProgress((int) ((float) (mCount) / (float) (mSize) * (float) getMax()));
            return true;
        }

        /**
         * Data upload is ended. Dismiss progress dialog.
         */
        public void end() {
            this.setProgress(this.getMax());
            this.dismiss();

        }

        /**
         * Initializes the SftpProgressMonitor
         */
        public void init(int arg0, String arg1, String arg2, long arg3) {
            mSize = arg3;

        }


    }
}
