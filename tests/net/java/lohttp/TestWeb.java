package net.java.lohttp;

/* Java */

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;


/**
 * Test for a web application.
 * Stopped via the termination.
 *
 * @author anton.baukin@gmail.com
 */
public class TestWeb
{
	public static void  main(String[] argv)
	  throws Exception
	{
		Setup setup = new Setup();

		//~: task executor
		setup.setExecute(args ->
		{
			Request req = (Request)args[0];

			if(req instanceof Get)
				get((Get)req, (Response)args[1]);
			else if("POST".equals(req.getMethod()))
				post((Post)req, (Response)args[1]);
			else
				Responses.NotMethod.write(args[2]);
		});

		//~: start the server
		new LowHat().start(setup, null);

		//!: ... terminate the process
	}

	private static void get(Get req, Response res)
	{
		res.addHeader("Cache-Control", "max-age=0, no-cache");

		String p = null;
		URL    u = null;

		//?: {index page}
		if("/".equals(req.getPath()))
		{
			res.addHeader("Content-Type", "text/html;charset=UTF-8");
			p = "content/index.html";
		}
		//~: treat as a resource page
		else
			p = "content" + req.getPath();

		//~: url of the resource
		if(p != null)
			u = TestWeb.class.getResource(p);

		//?: {resource is not found}
		if(u == null)
		{
			res.setStatus(404);
			res.write(null);
			return;
		}

		//~: write the file
		try(InputStream i = u.openStream())
		{
			res.write(Support.input(i));
		}
		catch(Throwable e)
		{
			res.setStatus(500);
		}
	}

	private static void post(Post req, Response res)
	{
		try
		{
			//~: dispatch to the private method
			Method m = TestWeb.class.getDeclaredMethod(
			  req.getPath().substring(1),
			  Post.class, Response.class);

			//~: make it accessible & invoke
			m.setAccessible(true);
			m.invoke(null, req, res);
		}
		catch(NoSuchMethodException ignore)
		{
			res.setStatus(404);
			res.write(null);
		}
		catch(Throwable e)
		{
			throw EX.wrap(e);
		}
	}

	private static void login(Post req, Response res)
	{
		String l, p;

		try
		{
			EX.assertx(req.decode());

			l = EX.asserts(req.getParam("login"));
			p = EX.asserts(req.getParam("pass"));

			//sec: check the credentials
			EX.assertx("tester@gmail.com".equals(l) &&
			  "password".equals(p)
			);
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