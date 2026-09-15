package com.wangyouyong.mes;

import android.annotation.SuppressLint;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;

/**
 * 应用入口：拦截系统返回键，先交给 Web 层页面栈处理，页面栈为空时才退出应用。
 *
 * <p>JS 侧通过 {@code window.AndroidBackBridge.register(depth)} 同步页面栈深度，
 * 返回键按下时主线程调用 {@code window.__androidBackPressed()}，
 * 由 Vue 页面栈决定返回上一页或放行退出。</p>
 */
public class MainActivity extends BridgeActivity
{
    private boolean backHandlerReady = false;
    private boolean backEventPending = false;

    /** Web 页面每次页面栈变化时调用，告知原生层当前页面栈深度。 */
    public static class BackBridge
    {
        private final MainActivity activity;

        BackBridge(MainActivity activity)
        {
            this.activity = activity;
        }

        @JavascriptInterface
        public void register(int stackDepth)
        {
            activity.onBackStackChanged(stackDepth);
        }
    }

    @Override
    public void onStart()
    {
        super.onStart();
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getWebView() != null)
        {
            WebView webView = bridge.getWebView();
            webView.addJavascriptInterface(new BackBridge(this), "AndroidBackBridge");
        }
    }

    private void onBackStackChanged(int stackDepth)
    {
        backHandlerReady = stackDepth > 0;
        if (backHandlerReady && backEventPending)
        {
            backEventPending = false;
            notifyWebBackPressed();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void notifyWebBackPressed()
    {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getWebView() != null)
        {
            bridge.getWebView().post(() -> bridge.eval("window.__androidBackPressed && window.__androidBackPressed();", null));
        }
    }

    @Override
    public void onBackPressed()
    {
        if (backHandlerReady)
        {
            notifyWebBackPressed();
        }
        else
        {
            backEventPending = true;
            super.onBackPressed();
        }
    }
}
