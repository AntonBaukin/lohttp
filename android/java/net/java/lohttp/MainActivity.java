package net.java.lohttp;

/* Java */

import java.net.BindException;

/* Android */

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;


/**
 * Embeds browser in the window.
 *
 * @author anton.baukin@gmail.com
 */
public class MainActivity extends AppCompatActivity
{
	public static final String LOG = "LoHTTPDemo";


	/* Activity Lifecycle */

	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(getResources().getIdentifier(
		  "main", "layout", getPackageName()));

		browser = (WebView) findViewById(getResources().
		  getIdentifier("webview", "id", getPackageName()));

		browser.getSettings().setJavaScriptEnabled(true);
		browser.setWebViewClient(new WebViewClient());
	}

	protected WebView browser;

	protected void onStart()
	{
		super.onStart();

		//~: create the setup
		if(setup == null)
			setup = createSetup();

		//~: start the server
		for(int itry = 0;;itry++)
		{
			try
			{
				tryStart();

				//!: success
				break;
			}
			catch(Throwable e)
			{
				//Hint: even with socket reuse address, we
				//  sometimes have socket in TIME_WAIT state,
				//  thus have to increment the port.

				//?: {bind exception}
				if(EX.xrt(e) instanceof BindException)
				{
					//!: increment the port
					int port = setup.getPort() + 1;
					setup = setup.clone().setPort(
					  (port < 8900)?(port):(8800));

					//?: {try again limit}
					if(itry < 10) continue;
				}

				throw EX.wrap(e);
			}
		}
	}

	/**
	 * Web server setup.
	 */
	protected Setup setup;

	protected LowHat server = new LowHat();

	protected void onStop()
	{
		//~: preserve the resources of browser
		browser.loadUrl("about:blank");

		//~: stop the server
		Log.d(LOG, "stopping the server...");
		server.hangup(new Callback()
		{
			public void act(Object... args)
			{
				Log.d(LOG, "the server is stopped!");
				server.close();
			}
		});

		super.onStop();
	}


	/* protected: server activation */

	protected Setup createSetup()
	{
		Setup s = new Setup();

		//~: initial port
		s.setPort(8800);

		//~: executor
		s.setExecute(new WebExecute(getAssets()));

		return s;
	}

	protected void  tryStart()
	{
		Log.d(LOG, "starting the server on " + setup.getPort() + "...");

		server.start(setup, new Callback()
		{
			public void act(Object... args)
			{
				Log.d(LOG, "server is started!");

				//~: server ready, load the initial page
				browser.loadUrl("http://127.0.0.1:" + setup.getPort());
			}
		});
	}
}