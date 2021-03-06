package org.wycliffeassociates.translationrecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Created by sarabiaj on 3/1/2017.
 */

public class DocumentationActivity extends AppCompatActivity {

    WebView webView;
    boolean mIsOnline = false;
    String mNetworkStatus;
    private WifiBroadcastReceiver mWifiBroadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_documentation);
        webView = (WebView) findViewById(R.id.webview);
        webView.setWebViewClient(new URLFilteringWebClient());
        if (isNetworkAvailable()) {
            mIsOnline = true;
            mNetworkStatus = "Online";
        } else {
            mNetworkStatus = "Offline";
        }
        if(mIsOnline) {
            webView.loadUrl("https://btt-recorder.readthedocs.io/en/latest/");
        } else {
            webView.loadUrl("file:///android_asset/btt-recorder.readthedocs.io/index.html");
        }
        Toolbar mToolbar = (Toolbar) findViewById(R.id.documentation_toolbar);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.help_menu);
            if(mIsOnline) {
                getSupportActionBar().setIcon(R.drawable.ic_wifi_white_24dp);
            } else {
                getSupportActionBar().setIcon(R.drawable.ic_wifi_off_white_24dp);
            }
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        mWifiBroadcastReceiver = new WifiBroadcastReceiver();
        registerReceiver(mWifiBroadcastReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mWifiBroadcastReceiver);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        boolean isAvailable = false;
        if (networkInfo != null && networkInfo.isConnected()) {
            // Network is present and connected
            isAvailable = true;
        }
        return isAvailable;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.helps_menu, menu);
        return true;
    }

    @Override
    public void onBackPressed() {
        if(webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_close: {
                finish();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private class URLFilteringWebClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            String urlHost = Uri.parse(url).getHost();
            if (urlHost.equals("tr-info.readthedocs.io") || (!mIsOnline && url.contains("file:///android_asset/tr-info.readthedocs.io/"))) {
                // This is my web site, so do not override; let my WebView load the page
                return false;
            }
            // Otherwise, the link is not for a page on my site, so launch another Activity that handles URLs
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        }
    }

    public class WifiBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            NetworkInfo networkInfo = intent
                    .getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if (networkInfo != null) {
                if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    //get the different network states
                    if (networkInfo.getState() ==NetworkInfo.State.CONNECTED) {
                        getSupportActionBar().setIcon(R.drawable.ic_wifi_white_24dp);

                    } else if(networkInfo.getState() == NetworkInfo.State.DISCONNECTED) {
                        getSupportActionBar().setIcon(R.drawable.ic_wifi_off_white_24dp);
                    }
                }
            }

        }
    }
}
