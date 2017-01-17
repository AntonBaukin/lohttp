package net.java.lohttp;

/* Java */

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;

/* Android */

import android.content.res.AssetManager;


/**
 * Executes HTTP requests coming
 * to the local server.
 *
 * @author anton.baukin@gmail.com
 */
public class WebExecute implements Callback
{
	public WebExecute(AssetManager assets)
	{
		this.assets = assets;
	}

	protected final AssetManager assets;


	/* Callback */

	public void  act(Object... args)
	{
		Request req = (Request)args[0];

		if(req instanceof Get)
			get((Get)req, (Response)args[1]);
		else if("POST".equals(req.getMethod()))
			post((Post)req, (Response)args[1]);
		else
			Responses.NotMethod.write(args[2]);
	}

	private void get(Get req, Response res)
	{
		res.addHeader("Cache-Control", "max-age=0, no-cache");

		String p = null;

		//?: {index page}
		if("/".equals(req.getPath()))
		{
			res.addHeader("Content-Type", "text/html;charset=UTF-8");
			p = "index.html";
		}
		//~: treat as a resource page
		else
			p = req.getPath().substring(1);

		//~: asset file name
		try(InputStream i = assets.open(p))
		{
			res.write(Support.input(i));
		}
		catch(FileNotFoundException ignore)
		{
			res.setStatus(404);
			res.write(null);
		}
		catch(Throwable ignore)
		{
			res.setStatus(500);
		}
	}

	private void post(Post req, Response res)
	{
		res.addHeader("Cache-Control", "max-age=0, no-cache");

		if("/login".equals(req.getPath()))
			login(req, res);
		else
		{
			res.setStatus(404);
			res.write(null);
		}
	}

	private void login(Post req, Response res)
	{
		String l, p;

		try
		{
			EX.assertx(req.decode());

			l = EX.asserts(req.getParam("login"));
			p = EX.asserts(req.getParam("pass"));

			//sec: check the credentials
			EX.assertx("tester@gmail.com".equals(l) &&
			  "password".equals(p));
		}
		catch(Throwable ignore)
		{
			res.setStatus(400);
			res.write(null);
			return;
		}

		//~: write success
		json(res, "{\"success\":true}");
	}

	private static void json(Response res, String body)
	{
		res.addHeader("Content-Type",
		  "application/json;charset=UTF-8");

		res.write(new Support.CharBytes(body).input());
	}
}