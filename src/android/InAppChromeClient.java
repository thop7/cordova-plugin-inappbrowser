/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.inappbrowser;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.net.Uri;

import android.os.Message;
import android.webkit.JsPromptResult;
import android.webkit.WebChromeClient;
import android.webkit.WebStorage;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.GeolocationPermissions.Callback;
import android.webkit.ValueCallback;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

public class InAppChromeClient extends WebChromeClient {

    private CordovaWebView parentView;
    private WebView view;
    private Activity activity;
    private Dialog dialog;

    private String LOG_TAG = "InAppChromeClient";
    private long MAX_QUOTA = 100 * 1024 * 1024;

    public InAppChromeClient(CordovaWebView parentView, Activity activity) {
        super();
        this.parentView = parentView;
        this.activity = activity;
    }
    /**
     * Handle database quota exceeded notification.
     *
     * @param url
     * @param databaseIdentifier
     * @param currentQuota
     * @param estimatedSize
     * @param totalUsedQuota
     * @param quotaUpdater
     */
    @Override
    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long currentQuota, long estimatedSize,
            long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater)
    {
        LOG.d(LOG_TAG, "onExceededDatabaseQuota estimatedSize: %d  currentQuota: %d  totalUsedQuota: %d", estimatedSize, currentQuota, totalUsedQuota);
        quotaUpdater.updateQuota(MAX_QUOTA);
    }

    /**
     * Instructs the client to show a prompt to ask the user to set the Geolocation permission state for the specified origin.
     *
     * @param origin
     * @param callback
     */
    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, Callback callback) {
        super.onGeolocationPermissionsShowPrompt(origin, callback);
        callback.invoke(origin, true, false);
    }

    /**
     * Tell the client to display a prompt dialog to the user.
     * If the client returns true, WebView will assume that the client will
     * handle the prompt dialog and call the appropriate JsPromptResult method.
     *
     * The prompt bridge provided for the InAppBrowser is capable of executing any
     * oustanding callback belonging to the InAppBrowser plugin. Care has been
     * taken that other callbacks cannot be triggered, and that no other code
     * execution is possible.
     *
     * To trigger the bridge, the prompt default value should be of the form:
     *
     * gap-iab://<callbackId>
     *
     * where <callbackId> is the string id of the callback to trigger (something
     * like "InAppBrowser0123456789")
     *
     * If present, the prompt message is expected to be a JSON-encoded value to
     * pass to the callback. A JSON_EXCEPTION is returned if the JSON is invalid.
     *
     * @param view
     * @param url
     * @param message
     * @param defaultValue
     * @param result
     */
    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        // See if the prompt string uses the 'gap-iab' protocol. If so, the remainder should be the id of a callback to execute.
        if (defaultValue != null && defaultValue.startsWith("gap")) {
            if(defaultValue.startsWith("gap-iab://")) {
                PluginResult scriptResult;
                String scriptCallbackId = defaultValue.substring(10);
                if (scriptCallbackId.startsWith("InAppBrowser")) {
                    if(message == null || message.length() == 0) {
                        scriptResult = new PluginResult(PluginResult.Status.OK, new JSONArray());
                    } else {
                        try {
                            scriptResult = new PluginResult(PluginResult.Status.OK, new JSONArray(message));
                        } catch(JSONException e) {
                            scriptResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
                        }
                    }
                    this.parentView.sendPluginResult(scriptResult, scriptCallbackId);
                    result.confirm("");
                    return true;
                }
            }
            else
            {
                // Anything else with a gap: prefix should get this message
                LOG.w(LOG_TAG, "InAppBrowser does not support Cordova API calls: " + url + " " + defaultValue);
                result.cancel();
                return true;
            }
        }
        return false;
    }
    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {

      // now create a new web view

      this.view = new WebView(this.activity);
      this.dialog = new Dialog(this.activity) {
          @Override
          public void onBackPressed () {
            closeView();
          }
      };
      this.dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
      this.dialog.setCancelable(true);
      WebSettings settings = this.view.getSettings();
      settings.setJavaScriptEnabled(true);
      settings.setJavaScriptCanOpenWindowsAutomatically(true);
      settings.setSupportMultipleWindows(true);
      settings.setUseWideViewPort(true);
      settings.setLoadWithOverviewMode(true);
      this.view.setWebViewClient(new WebViewClient());
      WebChromeClient parent = this;
      this.view.setWebChromeClient(new WebChromeClient(){
          @Override
          public void onCloseWindow(WebView window) {
              super.onCloseWindow(window);
              closeView();
          }
          // For Android 5.0+
          public boolean onShowFileChooser (WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams)
          {
              return parent.onShowFileChooser(webView, filePathCallback, fileChooserParams);
          }
      });
      this.view.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
      //this.view.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      this.view.requestFocus();
      this.view.requestFocusFromTouch();

      RelativeLayout webViewLayout = new RelativeLayout(this.activity);
      webViewLayout.addView(this.view);

      LinearLayout main = new LinearLayout(this.activity);
      main.setOrientation(LinearLayout.VERTICAL);
      main.addView(webViewLayout);
      // tell the transport about the new view
      WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
      transport.setWebView(this.view);
      resultMsg.sendToTarget();

      WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
      lp.copyFrom(this.dialog.getWindow().getAttributes());
      lp.width = WindowManager.LayoutParams.MATCH_PARENT;
      lp.height = WindowManager.LayoutParams.MATCH_PARENT;
      lp.x = -100;
      lp.y = -100;

      this.dialog.setContentView(main);
      this.dialog.show();
      this.dialog.getWindow().setAttributes(lp);

      return true;
    }

    @Override
    public void onCloseWindow(WebView view){
        super.onCloseWindow(view);
        if (this.dialog != null) {
          this.dialog.dismiss();
          this.dialog = null;
        }
    }

    public void closeView() {
      this.view.setWebViewClient(new WebViewClient() {
          // NB: wait for about:blank before dismissing
          public void onPageFinished(WebView view, String url) {
              if (dialog != null) {
                  dialog.dismiss();
                  dialog = null;
              }
              view.removeAllViews();
              view.destroy();
              view = null;
          }
      });
      this.view.loadUrl("about:blank");
    }
}
