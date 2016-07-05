package wycliffeassociates.recordingapp.FilesPage.Export;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.io.File;
import java.util.ArrayList;

import wycliffeassociates.recordingapp.FileManagerUtils.FileItem;
import wycliffeassociates.recordingapp.FilesPage.AudioFilesAdapter;
import wycliffeassociates.recordingapp.ProjectManager.Project;

/**
 * Created by sarabiaj on 12/10/2015.
 */
public class AppExport extends Export {

//    public AppExport(ArrayList<FileItem> fileItemList, AudioFilesAdapter adapter, String currentDir){
//        super(fileItemList, adapter, currentDir);
//        mCurrentDir = currentDir;
//    }

    public AppExport(File exportProject, Project project){
        super(exportProject, project);
    }

    @Override
    public void export(){
        //if(Export.shouldZip(mExportList)){
            zipFiles(this);
//        } else {
//            handleUserInput();
//        }
    }

    @Override
    protected void handleUserInput() {
        //if(Export.shouldZip(mExportList)) {
            exportZipApplications(mZipFile);
//        } else {
//            exportApplications(mExportList);
//        }
    }

//    /**
//     *  Passes URIs to relevant audio applications.
//     *
//     *      @param exportList
//     *          a list of filenames to be exported
//     */
//    private void exportApplications(ArrayList<String> exportList){
//
//        Intent sendIntent = new Intent();
//        sendIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//
//        File tFile;
//
//        //individual file
//        if(exportList.size() < 2){
//            Uri audioUri;
//
//            tFile = new File (exportList.get(0));
//            audioUri = Uri.fromFile(tFile);
//            sendIntent.setAction(Intent.ACTION_SEND);
//            String filename = exportList.get(0).replace(mCurrentDir + "/", "");
//            sendIntent.putExtra(Intent.EXTRA_TITLE, filename);
//
//            //send individual URI
//            sendIntent.putExtra(Intent.EXTRA_STREAM, audioUri);
//        //multiple files
//        } else {
//
//            ArrayList<Uri> audioUris = new ArrayList<Uri>();
//            for(int i=0; i<exportList.size(); i++){
//                tFile = new File(exportList.get(i));
//                audioUris.add(Uri.fromFile(tFile));
//            }
//            sendIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
//
//            //send multiple arrayList of URIs
//            sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, audioUris);
//        }
//
//        //open
//        sendIntent.setType("audio/*");
//        mCtx.startActivity(Intent.createChooser(sendIntent, "Export Audio"));
//    }

    /**
     *  Passes zip file URI to relevant audio applications.
     *      @param zipFile a list of filenames to be exported
     */
    private void exportZipApplications(File zipFile){
        Intent shareIntent = new Intent(this.mCtx.getActivity(), AppExport.ShareZipToApps.class);
        shareIntent.putExtra("zipPath", zipFile.getAbsolutePath());
        mCtx.startActivity(shareIntent);
    }

    public static class ShareZipToApps extends Activity{
        File mFile;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            String path = getIntent().getStringExtra("zipPath");
            Intent sendIntent = new Intent();
            sendIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            mFile = new File (path);
            Uri audioUri = Uri.fromFile(mFile);
            sendIntent.setAction(Intent.ACTION_SEND);
            //send individual URI
            sendIntent.putExtra(Intent.EXTRA_STREAM, audioUri);
            //open
            sendIntent.setType("application/zip");
            this.startActivityForResult(Intent.createChooser(sendIntent, "Export Zip"), 3);
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if(mFile != null){
                mFile.delete();
            }
        }
    }
}
