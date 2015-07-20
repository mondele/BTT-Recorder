package wycliffeassociates.recordingapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import java.util.ArrayList;


public class Record extends Activity {
    private String recordedFilename = null;
    private WavRecorder recorder = null;
    final Context context = this;
    private String outputName = null;
    private ArrayList<String> temporaryFiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.record);
        setButtonHandlers();
        enableButtons(false);
    }

    private void setButtonHandlers() {
        findViewById(R.id.btnRecord).setOnClickListener(btnClick);
        findViewById(R.id.btnStop).setOnClickListener(btnClick);
        findViewById(R.id.btnPlay).setOnClickListener(btnClick);
        findViewById(R.id.btnSave).setOnClickListener(btnClick);
        findViewById(R.id.btnPause).setOnClickListener(btnClick);
    }

    private void enableButton(int id,boolean isEnable){
        findViewById(id).setEnabled(isEnable);
        if(isEnable){
            findViewById(id).setVisibility(View.VISIBLE);
        }else{
            findViewById(id).setVisibility(View.INVISIBLE);
        }
    }

    private void enableButtons(boolean isRecording) {
        enableButton(R.id.btnRecord, !isRecording);
        enableButton(R.id.btnStop, isRecording);
        enableButton(R.id.btnPlay, !isRecording);
        enableButton(R.id.btnSave, true);
        enableButton(R.id.btnPause, isRecording);
    }

    private void saveRecording(){
        try {
            getSaveName(context);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean getSaveName(Context c){
        final EditText toSave = new EditText(c);
        toSave.setInputType(InputType.TYPE_CLASS_TEXT);

        //prepare the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(c);
        builder.setTitle("Save as");
        builder.setView(toSave);

        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setName(toSave.getText().toString());
                //SAVE FILE HERE
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        return true;
    }

    public void setName(String newName){
        outputName = newName;
        recordedFilename= recorder.saveFile(outputName);
    }

    public String getName(){return outputName;}

    private void startRecording(){
        if(recorder != null){
            recorder.release();
        }
        recorder = null;
        recorder = null;
        Toast.makeText(getApplicationContext(), "Starting Recording", Toast.LENGTH_LONG).show();
        recorder = new WavRecorder();
        recorder.record();
    }

    private void stopRecording(){
        temporaryFiles.add(recorder.getFilename());
        if (temporaryFiles.size()  < 2) {
            recorder.stop();
        }
        else {
            recorder.stop();
            recorder.pause_stop(temporaryFiles);
        }

        Toast.makeText(getApplicationContext(), "Stopping Recording", Toast.LENGTH_LONG).show();
        recordedFilename = recorder.getFilename();
    }

    private void playRecording(){
        Toast.makeText(getApplicationContext(), "Playing Audio", Toast.LENGTH_LONG).show();
        WavPlayer.play(recordedFilename);
    }

    private void pauseRecording(){
        recorder.stop();
        temporaryFiles.add(recorder.getFilename());
        Toast.makeText(getApplicationContext(), "Audio Paused", Toast.LENGTH_LONG).show();
    }

    private View.OnClickListener btnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()){
                case R.id.btnRecord:{
                    enableButtons(true);
                    startRecording();
                    break;
                }
                case R.id.btnStop:{
                    enableButtons(false);
                    stopRecording();
                    break;
                }
                case R.id.btnPlay:{
                    enableButtons(false);
                    playRecording();
                    break;
                }
                case R.id.btnSave:{
                    saveRecording();
                    break;
                }
                case R.id.btnPause:{
                    enableButtons(false);
                    pauseRecording();
                    break;
                }
            }
        }
    };
}