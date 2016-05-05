package com.creativedev.mobile.googledrivetest;

import android.content.Intent;
import android.content.IntentSender;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.MetadataChangeSet;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DefaultActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final int REQUEST_CODE_RESOLUTION = 1;
    private static final int REQUEST_CODE_CREATOR = 2;
    private static final String TAG = "drive-quickstart";
    private static final String DIR_NAME = "test_dir";
    private File fileToSave;

    private GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_default);

        Button testBtn = (Button)findViewById(R.id.standart_duploadBtn);
        testBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DefaultActivity.this.uploadFile();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
    }

    @Override
    protected void onPause() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onPause();
    }

    private void uploadFile() {
        this.mGoogleApiClient.connect();
        try {
            this.createPdfFile();
        } catch (DocumentException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void createPdfFile() throws DocumentException, FileNotFoundException {
        File pdfFolder = new File(this.getDiaryDirName(), DefaultActivity.DIR_NAME);
        if (!pdfFolder.exists()) {
            pdfFolder.mkdir();
            Log.d(DefaultActivity.TAG, "Pdf Directory created");
        }
        this.fileToSave = new File(pdfFolder +"/"+new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())+".pdf");
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(this.fileToSave));
        document.open();
        PdfPTable table = new PdfPTable(8);
        for(int aw = 0; aw < 16; aw++){
            table.addCell("hi "+aw);
        }
        document.add(table);
        document.close();
    }

    private String getDiaryDirName() {
        return this.getFilesDir().toString();
    }

    private void saveFileToDrive() {
        Log.i(TAG, "Creating new contents.");
        Drive.DriveApi.newDriveContents(mGoogleApiClient).setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
            @Override
            public void onResult(DriveApi.DriveContentsResult result) {
                if (!result.getStatus().isSuccess()) {
                    Log.i(TAG, "Failed to create new contents.");
                    return;
                }
                Log.i(TAG, "New contents created.");
                OutputStream outputStream = result.getDriveContents().getOutputStream();
                try {
                    InputStream is = new FileInputStream(DefaultActivity.this.fileToSave);
                    int c = 0;
                    byte[] buf = new byte[8192];
                    while ((c = is.read(buf, 0, buf.length)) > 0) {
                        outputStream.write(buf, 0, c);
                        outputStream.flush();
                    }
                    outputStream.close();
                    is.close();
                    MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                            .setMimeType("application/pdf").setTitle(DefaultActivity.this.fileToSave.getName()).build();
                    IntentSender intentSender = Drive.DriveApi
                            .newCreateFileActivityBuilder()
                            .setInitialMetadata(metadataChangeSet)
                            .setInitialDriveContents(result.getDriveContents())
                            .build(mGoogleApiClient);
                    startIntentSenderForResult(intentSender, REQUEST_CODE_CREATOR, null, 0, 0, 0);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (IntentSender.SendIntentException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_CREATOR:
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "Image successfully saved.");
                    this.fileToSave.delete();
                    this.fileToSave = null;
                }
                break;
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        saveFileToDrive();
    }


    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
            return;
        }
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    public GoogleApiClient getGoogleApiClient() {
        return this.mGoogleApiClient;
    }
}
