package com.example.inceptionv3;

import static com.example.inceptionv3.Constants.MNETSSD_INPUT_LAYER;
import static com.example.inceptionv3.Constants.MNETSSD_OUTPUT_LAYER;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.qualcomm.qti.snpe.FloatTensor;
import com.qualcomm.qti.snpe.SNPE;
import com.qualcomm.qti.snpe.NeuralNetwork;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.provider.MediaStore;
import android.provider.SyncStateContract;
import android.util.Log;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.inceptionv3.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import com.example.inceptionv3.Constants;

//Platform validator class for object creation
import com.qualcomm.qti.platformvalidator.PlatformValidator;
//available runtimes are defined in this class
import com.qualcomm.qti.platformvalidator.PlatformValidatorUtil;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;


    Button btnSelect, btnCapture, btnPredict;
    TextView tvResult;
    ImageView imView;
    Bitmap bitmap;

    private static String TAG = MainActivity.class.getSimpleName();
    private static int MNETSSD_NUM_BOXES = Constants.BITMAP_WIDTH * Constants.BITMAP_WIDTH;
    private final float[] floatOutput = new float[MNETSSD_NUM_BOXES];
    private Map<String, FloatTensor> mOutputs;
    private BitmapToFloatArrayHelper mBitmapToFloatHelper;


    public int originalBitmapW, originalBitmapH;
    private int[] mInputTensorShapeHWC;
    private FloatTensor mInputTensorReused;
    private Map<String, FloatTensor> mInputTensorsMap;
    private NeuralNetwork mNeuralnetwork;
    private Bitmap mScaledBitmap, mOutputBitmap;
    private IBitmapLoader mCallbackBitmapLoader;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d("load Library", "Loading snpe library");

        System.loadLibrary("snpe-android");

        //permissions
        getAppPermissions();



        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        btnCapture = findViewById(R.id.btnCapture);
        btnSelect = findViewById(R.id.btnSelect);
        btnPredict = findViewById(R.id.btnPredict);

        tvResult = findViewById(R.id.tvResult);
        imView = findViewById(R.id.imView);

        Log.d(
                "Platform: ", "//This create platform validator object for GPU runtime class"
        );
        //This create platform validator object for GPU runtime class
        PlatformValidator pv = new PlatformValidator(PlatformValidatorUtil.Runtime.GPU);

        // To check in general runtime is working use isRuntimeAvailable
//        boolean check = pv.isRuntimeAvailable(getApplication());

        // To check SNPE runtime is working use runtimeCheck
//        boolean check1 = pv.runtimeCheck(getApplication());

        //To get core version use libVersion api
//        String str = pv.coreVersion(getApplication());

        //To get core version use coreVersion api
//        String str1 = pv.coreVersion(getApplication());

        //List of available runtimes
/*        PlatformValidatorUtil.Runtime.CPU
        PlatformValidatorUtil.Runtime.GPU
        PlatformValidatorUtil.Runtime.DSP
        PlatformValidatorUtil.Runtime.GPU_FLOAT16
        PlatformValidatorUtil.Runtime.AIP*/

       /* Log.d("Runtime available", String.valueOf(check));
        Log.d("runtime check", String.valueOf(check1));
        Log.d("core version", str1);*/







        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");

                startActivityForResult(intent, 10);
            }


        });


        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, 12);
            }
        });


        btnPredict.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                try {
                    /*final SNPE.NeuralNetworkBuilder builder = new SNPE.NeuralNetworkBuilder(getApplication())
                            .setRuntimeOrder(NeuralNetwork.Runtime.DSP, NeuralNetwork.Runtime.GPU, NeuralNetwork.Runtime.CPU)
                            .setModel(new File("file:///android_asset/inception_v3.dlc"));*/



                    AssetManager assetManager = getApplicationContext().getAssets();
                    String modelFilePath = "inception_v3.dlc";
                    File modelFile = new File(getFilesDir(), modelFilePath);
                    try {
                        InputStream inputStream = assetManager.open(modelFilePath);
                        OutputStream outputStream = new FileOutputStream(modelFile);
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = inputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                        outputStream.close();
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    final SNPE.NeuralNetworkBuilder builder = new SNPE.NeuralNetworkBuilder(getApplication())
                            .setRuntimeOrder(NeuralNetwork.Runtime.CPU)
                            //.setRuntimeOrder(NeuralNetwork.Runtime.DSP, NeuralNetwork.Runtime.GPU, NeuralNetwork.Runtime.CPU)
                            .setModel(modelFile);




                    mNeuralnetwork = builder.build();

                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 299, 299, true);


                    // Run inference on the input bitmap
                    Map<String, FloatTensor> outputTensors = inferenceOnBitmap(scaledBitmap);

                    // Get the output tensor for the predicted class probabilities
                    FloatTensor outputTensor = outputTensors.get("output_tensor_name");

                    // Read the values from the FloatTensor into a float array
                    float[] outputValues = new float[outputTensor.getSize()];
                    outputTensor.read(outputValues, 0, outputValues.length);

                    // Get the predicted class label
                    int predictedClass = outputValues[0] >= 0.5 ? 1 : 0;

                    // Print the predicted class label
                    //Log.d("Prediction", "Predicted class: " + predictedClass);

                    tvResult.setText(predictedClass + "");


                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
        });

    }

    public int getInputTensorWidth() {
        return mInputTensorShapeHWC == null ? 0 : mInputTensorShapeHWC[1];
    }

    public int getInputTensorHeight() {
        return mInputTensorShapeHWC == null ? 0 : mInputTensorShapeHWC[2];
    }



    public Bitmap deeplabV3Inference() {
        try {

            mInputTensorShapeHWC = mNeuralnetwork.getInputTensorsShapes().get(MNETSSD_INPUT_LAYER);
            // allocate the single input tensor
            mInputTensorReused = mNeuralnetwork.createFloatTensor(mInputTensorShapeHWC);
            // add it to the map of inputs, even if it's a single input
            mInputTensorsMap = new HashMap<>();
            mInputTensorsMap.put(MNETSSD_INPUT_LAYER, mInputTensorReused);
            // execute the inference, and get 3 tensors as outputs
            mOutputs = inferenceOnBitmap(mScaledBitmap);
            if (mOutputs != null) {
                MNETSSD_NUM_BOXES = mOutputs.get(MNETSSD_OUTPUT_LAYER).getSize();
                // convert tensors to boxes - Note: Optimized to read-all upfront
                mOutputs.get(MNETSSD_OUTPUT_LAYER).read(floatOutput, 0, MNETSSD_NUM_BOXES);
                //for black/white image
                int w = mScaledBitmap.getWidth();
                int h = mScaledBitmap.getHeight();
                int b = 0xFF;
                int out = 0xFF;

                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        b = b & mScaledBitmap.getPixel(x, y);
                        for (int i = 1; i <= 3 && floatOutput[y * w + x] != 15; i++) {
                            out = out << (8) | b;
                        }
                        mScaledBitmap.setPixel(x, y, floatOutput[y * w + x] != 15 ? out : mScaledBitmap.getPixel(x, y));
                        out = 0xFF;
                        b = 0xFF;
                    }
                }

                mOutputBitmap = Bitmap.createScaledBitmap(mScaledBitmap, originalBitmapW,
                        originalBitmapH, true);
                //Logger.d(TAG, mOutputBitmap.getWidth() + "");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        return mOutputBitmap;
    }
    private Map<String, FloatTensor> inferenceOnBitmap(Bitmap scaledBitmap) {

        Bitmap bitmap1 = deeplabV3Inference();
        final Map<String, FloatTensor> outputs;
        try {
            if (mNeuralnetwork == null || mInputTensorReused == null || scaledBitmap.getWidth() != getInputTensorWidth() || scaledBitmap.getHeight() != getInputTensorHeight()) {
                //Logger.d("SNPEHelper", "No NN loaded, or image size different than tensor size");
                return null;
            }
//Bitmap to RGBA byte array (size: 513*513*3 (RGBA..))
            mBitmapToFloatHelper.bitmapToBuffer(scaledBitmap);
//Pre-processing: Bitmap (513,513,4 ints) -> Float Input Tensor (513,513,3 floats)
            final float[] inputFloatsHW3 = mBitmapToFloatHelper.bufferToNormalFloatsBGR();
            if (mBitmapToFloatHelper.isFloatBufferBlack())
                return null;
            mInputTensorReused.write(inputFloatsHW3, 0, inputFloatsHW3.length, 0, 0);
// execute the inference
            outputs = mNeuralnetwork.execute(mInputTensorsMap);
        } catch (Exception e) {
            e.printStackTrace();
            //Logger.d("SNPEHelper", e.getCause() + "");
            return null;
        }
        return outputs;
    }



    private void getAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, 11);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode ==11 && grantResults.length > 0){
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
                this.getAppPermissions();
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == 10 && data != null){
            Uri uri = data.getData();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                imView.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if(requestCode == 12 && data != null){
            bitmap = (Bitmap) data.getExtras().get("data");
            imView.setImageBitmap(bitmap);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}