package com.teamx.flightcontrol;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;

import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneInputStream;
import com.ibm.watson.developer_cloud.android.library.audio.StreamPlayer;
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType;
import com.ibm.watson.developer_cloud.conversation.v1.ConversationService;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageRequest;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageResponse;
import com.ibm.watson.developer_cloud.http.ServiceCallback;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.RecognizeCallback;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechResults;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;

import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;

/** @Bitcamp 2017 submission
 *
 * IBM Watson implementation park of oour submission to Bitcamp.
 * The code here is incomplete
 *
 * @author Team X
 * @version 1
 */
public class MainActivity extends AppCompatActivity {

    private static String LOG_TAG = MainActivity.class.getSimpleName();
    private String watsonResponseText = "";

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private SpeechToText speechService;
    private TextToSpeech textService;
    private StreamPlayer player = new StreamPlayer();

    private MicrophoneInputStream capture;

    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ButterKnife.bind(this);
        disableSystemOut();

        StaticVariables.timeCounter = SystemClock.elapsedRealtime();


        speechService = initSpeechToTextService();
        textService = initTextToSpeechService();

        capture = new MicrophoneInputStream(true);

        startSpeechService();


    }

    @Override
    protected void onResume() {
        super.onResume();

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
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            capture.close();
        } catch (IOException e) {

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode){
        case REQUEST_RECORD_AUDIO_PERMISSION:
            permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        break;
    }
    if (!permissionToRecordAccepted ) finish();

    }

    private void startSpeechService(){
        new Thread(new Runnable() {

            @Override public void run() {

                try {

                    speechService.recognizeUsingWebSocket(capture, getRecognizeOptions(), new MicrophoneRecognizeDelegate());

                } catch (Exception e) {
                    Log.v(LOG_TAG, "mic bad");

                }

            }

        }).start();
    }

    private SpeechToText initSpeechToTextService() {

        SpeechToText service = new SpeechToText();

        String username = getString(R.string.speech_text_username);

        String password = getString(R.string.speech_text_password);

        service.setUsernameAndPassword(username, password);

        service.setEndPoint("https://stream.watsonplatform.net/speech-to-text/api");

        return service;

    }

    private TextToSpeech initTextToSpeechService() {

        TextToSpeech service = new TextToSpeech();

        String username = getString(R.string.text_speech_username);

        String password = getString(R.string.text_speech_password);

        service.setUsernameAndPassword(username, password);

        return service;

    }

    private RecognizeOptions getRecognizeOptions() {

        return new RecognizeOptions.Builder()

                .continuous(true)

                .contentType(ContentType.OPUS.toString())

                .model("en-US_BroadbandModel")

                .interimResults(true)

                .inactivityTimeout(2000)

                .build();

    }

    private void sendInput(String input) {
        ConversationService service = new ConversationService(ConversationService.VERSION_DATE_2017_02_03);
        service.setUsernameAndPassword("bae6db8d-87ac-49e5-bfc8-f40ae94b52ed", "8nCeZLZz2fSd");

        MessageRequest newMessage = new MessageRequest.Builder().inputText(input).build();

        service.message("783c58be-01c8-4897-8f67-dab1ac531f03", newMessage).enqueue(new ServiceCallback<MessageResponse>() {

            @Override

            public void onResponse(MessageResponse response) {
                new SynthesisTask().execute(response.getText().get(0));
            }

            @Override

            public void onFailure(Exception e) { }

        });
    }

    private class SynthesisTask extends AsyncTask<String, Void, String> {


        @Override protected String doInBackground(String... params) {
            player.playStream(textService.synthesize(params[0], Voice.EN_MICHAEL).execute());
            return "Did synthesize";

        }

    }

    private class MicrophoneRecognizeDelegate implements RecognizeCallback {



        @Override
        public void onTranscription(SpeechResults speechResults) {
            if (SystemClock.elapsedRealtime() - StaticVariables.timeCounter > 4000) {
                if(speechResults.getResults() != null && !speechResults.getResults().isEmpty()) {
                    StaticVariables.timeCounter = SystemClock.elapsedRealtime();
                    String text = speechResults.getResults().get(0).getAlternatives().get(0).getTranscript();
                    Log.v(LOG_TAG, text);
                    sendInput(text);
                }
            }

        }



        @Override public void onConnected() {
            StaticVariables.timeCounter = SystemClock.elapsedRealtime();


        }



        @Override public void onError(Exception e) {




        }



        @Override public void onDisconnected() {



        }

        @Override
        public void onInactivityTimeout(RuntimeException runtimeException) {

        }

        @Override
        public void onListening() {

        }

    }

    private void disableSystemOut() {
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            @Override public void write(int b) {}
        }) {
            @Override public void flush() {}
            @Override public void close() {}
            @Override public void write(int b) {}
            @Override public void write(byte[] b) {}
            @Override public void write(byte[] buf, int off, int len) {}
            @Override public void print(boolean b) {}
            @Override public void print(char c) {}
            @Override public void print(int i) {}
            @Override public void print(long l) {}
            @Override public void print(float f) {}
            @Override public void print(double d) {}
            @Override public void print(char[] s) {}
            @Override public void print(String s) {}
            @Override public void print(Object obj) {}
            @Override public void println() {}
            @Override public void println(boolean x) {}
            @Override public void println(char x) {}
            @Override public void println(int x) {}
            @Override public void println(long x) {}
            @Override public void println(float x) {}
            @Override public void println(double x) {}
            @Override public void println(char[] x) {}
            @Override public void println(String x) {}
            @Override public void println(Object x) {}
            @Override public java.io.PrintStream printf(String format, Object... args) { return this; }
            @Override public java.io.PrintStream printf(java.util.Locale l, String format, Object... args) { return this; }
            @Override public java.io.PrintStream format(String format, Object... args) { return this; }
            @Override public java.io.PrintStream format(java.util.Locale l, String format, Object... args) { return this; }
            @Override public java.io.PrintStream append(CharSequence csq) { return this; }
            @Override public java.io.PrintStream append(CharSequence csq, int start, int end) { return this; }
            @Override public java.io.PrintStream append(char c) { return this; }
        });
    }


}
