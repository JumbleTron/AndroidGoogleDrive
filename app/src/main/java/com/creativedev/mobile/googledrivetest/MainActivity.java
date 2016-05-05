package com.creativedev.mobile.googledrivetest;

import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
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
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataChangeSet;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final int REQUEST_CODE_RESOLUTION = 1;
    //private static final int REQUEST_CODE_CREATOR = 2;
    private static final String TAG = "drive-quickstart";
    private static final String DIR_NAME = "test_dir";
    private static final String PREFS_NAME = "blooddiarySettings";

    private File fileToSave;
    private GoogleApiClient mGoogleApiClient;
    private SharedPreferences settings;
    private String drive_folder_id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.settings = getSharedPreferences(PREFS_NAME, 0);
        this.drive_folder_id = this.settings.getString("drive_dir_id", null);

        Button uploadBtn = (Button)findViewById(R.id.upload_btn);
        uploadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.uploadFile();
            }
        });
        Button newBtn = (Button)findViewById(R.id.creatorUpload);
        newBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DefaultActivity.class));
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

    @Override
    public void onConnected(Bundle connectionHint) {
        if(this.drive_folder_id != null) {
            DriveId folderId = DriveId.decodeFromString(this.drive_folder_id);
            DriveFolder folder = Drive.DriveApi.getFolder(mGoogleApiClient, folderId);
            folder.getMetadata(mGoogleApiClient).setResultCallback(metadataRetrievedCallback);
        } else {
            MainActivity.this.createDirInDrive();
        }
    }

    final private ResultCallback<DriveResource.MetadataResult> metadataRetrievedCallback = new ResultCallback<DriveResource.MetadataResult>() {
        @Override
        public void onResult(DriveResource.MetadataResult result) {
            if (!result.getStatus().isSuccess()) {
                Log.v(TAG, "Problem while trying to fetch metadata.");
                return;
            }
            Metadata metadata = result.getMetadata();
            if(metadata.isTrashed()){
                MainActivity.this.createDirInDrive();
            } else{
                Drive.DriveApi.newDriveContents(getGoogleApiClient()).setResultCallback(driveContentsCallback);
            }

        }
    };

    final private ResultCallback<DriveFolder.DriveFolderResult> folderCreatedCallback = new ResultCallback<DriveFolder.DriveFolderResult>() {
        @Override
        public void onResult(DriveFolder.DriveFolderResult result) {
            if (!result.getStatus().isSuccess()) {
                Log.e(MainActivity.TAG,"Error while trying to create the folder");
                return;
            }
            Log.i(MainActivity.TAG,"Created a folder: " + result.getDriveFolder().getDriveId());
            SharedPreferences.Editor editor = MainActivity.this.settings.edit();
            editor.putString("drive_dir_id", result.getDriveFolder().getDriveId().encodeToString());
            editor.commit();
            MainActivity.this.drive_folder_id = result.getDriveFolder().getDriveId().encodeToString();
            Drive.DriveApi.newDriveContents(getGoogleApiClient()).setResultCallback(driveContentsCallback);
        }
    };


    final private ResultCallback<DriveApi.DriveContentsResult> driveContentsCallback = new ResultCallback<DriveApi.DriveContentsResult>() {
            @Override
            public void onResult(DriveApi.DriveContentsResult result) {
                if (!result.getStatus().isSuccess()) {
                    Log.e(MainActivity.TAG, "Error while trying to create new file contents");
                    return;
                }
                final DriveContents driveContents = result.getDriveContents();
                OutputStream outputStream = driveContents.getOutputStream();
                InputStream is = null;
                try {
                    is = new FileInputStream(MainActivity.this.fileToSave);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                int c = 0;
                byte[] buf = new byte[8192];
                try {
                    while ((c = is.read(buf, 0, buf.length)) > 0) {
                        outputStream.write(buf, 0, c);
                        outputStream.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                DriveFolder folder = DriveId.decodeFromString(MainActivity.this.drive_folder_id).asDriveFolder();
                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                        .setTitle(MainActivity.this.fileToSave.getName())
                        .setMimeType("application/pdf")
                        .build();
                folder.createFile(getGoogleApiClient(), changeSet, driveContents).setResultCallback(fileCallback);
            }
    };

    final private ResultCallback<DriveFolder.DriveFileResult> fileCallback = new ResultCallback<DriveFolder.DriveFileResult>() {
        @Override
        public void onResult(DriveFolder.DriveFileResult result) {
            if (!result.getStatus().isSuccess()) {
                Log.e(MainActivity.TAG,"Error while trying to create the file");
                return;
            }
            Log.d(MainActivity.TAG,"Created a file with content: " + result.getDriveFile().getDriveId());
        }
    };

    private void createDirInDrive() {
        MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setTitle("blood_diary").build();
        Drive.DriveApi.getRootFolder(getGoogleApiClient()).createFolder(getGoogleApiClient(), changeSet).setResultCallback(folderCreatedCallback);
    }

    private void createPdfFile() throws DocumentException, FileNotFoundException {
        File pdfFolder = new File(this.getDiaryDirName(), MainActivity.DIR_NAME);
        if (!pdfFolder.exists()) {
            pdfFolder.mkdir();
            Log.d(MainActivity.TAG, "Pdf Directory created");
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

    public GoogleApiClient getGoogleApiClient() {
        return this.mGoogleApiClient;
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
            MainActivity.this.uploadFile();
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }
}
