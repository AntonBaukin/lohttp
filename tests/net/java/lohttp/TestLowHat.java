package net.java.lohttp;

/* Java */

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;


/**
 * Tests {@link LowHat}.
 *
 * @author anton.baukin@gmail.com.
 */
public class TestLowHat
{
	/* Unit Tests */

	public void testStartStop()
	  throws Exception
	{
		LowHat server = new LowHat();

		//~: start the server
		server.start(setup, (args) -> print("testing start-stop"));

		try
		{
			//~: execute single touch
			EX.assertx(501 == touch());

			//~: wait for 0.2 second
			await(200);
		}
		finally
		{
			//~: stop the server
			stop(server);
		}
	}

	public void testPauseResume()
	  throws Exception
	{
		LowHat server = new LowHat();

		//~: tune the power
		setup.setBacklog(50);
		setup.setPool(16, 32, 50, "LowHat-");

		//~: waiting executor
		setup.setExecute((args) ->
		{
			await(3L); //<-- wait for some time
			Responses.NotImplemented.write(args[2]);
		});

		//~: start the server
		server.start(setup, (args) -> print("testing pause-resume"));

		//~: command switch, main barrier
		AtomicInteger cs = new AtomicInteger(1);
		CyclicBarrier  b = new CyclicBarrier(50, new Runnable()
		{
			int i, n = 5 + gen.get().nextInt(6);

			public void run()
			{
				if(++i > n) //?: {done with the test}
				{
					cs.set(0);
					return;
				}

				//~: increment the command
				final int c = cs.incrementAndGet();

				//?: even: pause, odd: resume
				if(c % 2 == 1)
					server.resume();
				else
					server.hangup(null);
			}
		});

		//~: test requests
		spawn(50, (task) ->
		{
			while(true) //c: test cycle
			{
				final int c = cs.get();

				//?: {exit test}
				if(c == 0) return;

				//?: odd: not implemented, even: unavailable
				final int e = (c % 2 == 1)?(501):(503);

				//c: requests cycle
				for(int i = 1 + gen.get().nextInt(5); (i != 0); i--)
				{
					//~: issue the request
					int x = touch();

					//?: {inconsistent}
					EX.assertx(x == e, "Expected ", e, ", got ", x);
				}

				//!: wait for everyone
				b.await();
			}
		});

		//~: wait for 2 seconds
		await(2000);

		//~: stop the server
		stop(server);
	}

	public void testMassiveTouch()
	{
		LowHat server = new LowHat();

		//~: tune the power
		setup.setBacklog(50);
		setup.setPool(16, 32, 16, "LowHat-");

		//~: start the server
		server.start(setup, (args) -> print("testing massive touch"));

		//~: test requests
		spawn(50, (task) ->
		{
			await(gen.get().nextInt(10));

			long ts = System.currentTimeMillis();
			for(int i = 0;(i < 100);i++)
			{
				int s = touch();
				EX.assertx(501 == s, "Got status ", s);
			}

			print(task, ": touched 100 in ",
			  System.currentTimeMillis() - ts);
		});

		//~: wait for 4 seconds
		await(4000);

		//~: stop the server
		stop(server);
	}

	public void testGetPost()
	  throws Exception
	{
		LowHat server = new LowHat();

		//~: request handler
		setup.setExecute(DO);

		//~: start the server
		server.start(setup, (args) -> print("testing get & post"));

		try
		{
			//~: execute random get
			EX.assertx(get());

			//~: execute random post
			EX.assertx(post());

			//~: wait for 0.2 second
			await(200);
		}
		finally
		{
			//~: stop the server
			stop(server);
		}
	}


	/* Enter Point & Helpers */

	public static void  main(String[] argv)
	  throws Exception
	{
		run("testStartStop", 0L);
		run("testPauseResume", 0L);
		run("testMassiveTouch", 0L);
		run("testGetPost", 0L);
	}

	private static void run(String method, long seed)
	  throws Exception
	{
		TestLowHat test = new TestLowHat();

		test.seeder = (seed == 0L)?(new Random()):(new Random(seed));
		test.gen.set(test.seeder);

		test.method = method;
		test.setup  = new Setup();

		//~: random port 8000 .. 8999
		//test.setup.setPort(8000 +
		//  (int)(System.currentTimeMillis() % 1000));

		test.getClass().getMethod(method).invoke(test);
	}

	private Random seeder;
	private String method;
	private Setup  setup;

	private void print(Object... msg)
	{
		System.out.println(EX.cat(method, ": ", msg));
	}

	private static void await(long ms)
	{
		EX.assertx(ms >= 0L);

		try
		{
			Thread.sleep(ms);
		}
		catch(Throwable e)
		{
			throw EX.wrap(e);
		}
	}

	private void stop(LowHat server)
	{
		print("pausing server");
		server.hangup((args) -> {
			print("server paused");
			print("terminating server");
			server.close();
			print("server closed");
			((ThreadPoolExecutor) setup.getPool()).shutdown();
		});
	}

	/**
	 * Executes GET request to the server, waits
	 * 1 .. 10 milliseconds, then returns the status.
	 */
	private int  touch()
	  throws Exception
	{
		URL url = new URL("http", setup.getAddress(), setup.getPort(), "");

		//~: open the connection
		HttpURLConnection co = (HttpURLConnection) url.openConnection();

		try
		{
			co.setRequestMethod("GET");
			co.setDoOutput(false);
			co.setUseCaches(false);

			return co.getResponseCode();
		}
		finally
		{
			await(1 + gen.get().nextInt(10));
			co.disconnect();
		}
	}

	/**
	 * Executes GET request to the server by random path
	 * and random number of the parameters and the headers.
	 * Each parameter or header has a number assigned.
	 * Returns true when the server returned the expected sum.
	 */
	protected class TestGet
	{
		/**
		 * Expected result sum.
		 */
		public int result;

		public boolean request()
		  throws Exception
		{
			//~: open the connection
		HttpURLConnection co = (HttpURLConnection) url().openConnection();

		try
		{
			//~: init the connection
			init(co);

			//~: issue the request
			int status = co.getResponseCode();

			//?: {server had failed}
			if(status != 200)
				return false;

			//~: response header
			String r = co.getHeaderField("Response");
			EX.assertx(Integer.toString(result).equals(r));

			//~: response body
			try(Support.BytesStream b = new Support.BytesStream())
			{
				b.write(co.getInputStream());
				r = new String(b.bytes(), "UTF-8");
				EX.assertx(Integer.toString(result).equals(r));
			}
		}
		finally
		{
			await(1 + gen.get().nextInt(10));
			co.disconnect();
		}

		return true;
		}

		protected void init(HttpURLConnection co)
		  throws Exception
		{
			co.setRequestMethod("GET");
			co.setDoOutput(false);
			co.setUseCaches(false);

			//?: {add headers}
			if(gen.get().nextInt(5) != 0)
			{
				int n = 1 + gen.get().nextInt(5);
				for(int i = 0;(i < n);i++)
				{
					int x, y = Math.abs(gen.get().nextInt());
					result += (x = gen.get().nextInt());
					co.setRequestProperty(Integer.toHexString(y), "" + x);
				}
			}
		}

		protected URL  url()
		  throws Exception
		{
			//~: create the request url
			StringBuilder url = new StringBuilder();
			url.append("http://").append(setup.getAddress());
			url.append(':').append(setup.getPort());

			//?: {add path}
			if(gen.get().nextInt(5) != 0)
			{
				int n = 1 + gen.get().nextInt(5);
				for(int i = 0;(i < n);i++)
				{
					int x = Math.abs(gen.get().nextInt());
					url.append('/').append(Integer.toHexString(x));
				}
			}

			//?: {add query}
			if(gen.get().nextInt(5) != 0)
			{
				url.append('?');
				int n = 1 + gen.get().nextInt(10);

				for(int i = 0;(i < n);i++)
				{
					int y, x = Math.abs(gen.get().nextInt());
					url.append(Integer.toHexString(x));
					url.append('=');
					result += (y = gen.get().nextInt());
					url.append(y);

					//?: {double it}
					if(gen.get().nextInt(3) != 0)
					{
						url.append('&');
						url.append(Integer.toHexString(x));
						url.append('=');
						result += (y = gen.get().nextInt());
						url.append(y);
					}

					if(i + 1 != n) url.append('&');
				}
			}

			return new URL(url.toString());
		}
	}

	private boolean get()
	  throws Exception
	{
		return new TestGet().request();
	}

	protected class TestPost extends TestGet
	{
		protected void init(HttpURLConnection co)
		  throws Exception
		{
			super.init(co);

			co.setDoOutput(true);
			co.setRequestMethod("POST");
			co.setRequestProperty("Content-Type",
			  "application/x-www-form-urlencoded");

			//~: body with the numbers
			StringBuilder body = new StringBuilder();

			int n = 1 + gen.get().nextInt(1000);
			for(int i = 0;(i < n);i++)
			{
				int y, x = Math.abs(gen.get().nextInt());
				body.append(Integer.toHexString(x));
				body.append('=');
				result += (y = gen.get().nextInt());
				body.append(y);

				//?: {double it}
				if(gen.get().nextInt(3) != 0)
				{
					body.append('&');
					body.append(Integer.toHexString(x));
					body.append('=');
					result += (y = gen.get().nextInt());
					body.append(y);
				}

				if(i + 1 != n) body.append('&');
			}

			//~: content length
			co.setRequestProperty("Content-Length",
			  Integer.toString(body.length()));

			//~: stream the body
			co.getOutputStream().write(
			  body.toString().getBytes("UTF-8"));
		}
	}

	private boolean post()
	  throws Exception
	{
		return new TestPost().request();
	}

	/**
	 * Callback that writes correct answer
	 * for test get and post requests.
	 */
	private Callback DO = args ->
	{
		Request  req = (Request) args[0];
		Response res = (Response) args[1];

		//?: {post request} decode the body
		if(req instanceof Post)
			EX.assertx(((Post)req).decode());

		//~: resulting number
		int[] result = new int[1];

		//~: add the parameters
		for(String p : req.getParams())
		{
			if(!p.matches("[0-9a-f]+"))
				continue;

			req.takeParams(p, v -> result[0] += Integer.parseInt(v));
		}

		//~: add the headers
		for(String h : req.getHeaders())
		{
			if(!h.matches("[0-9a-f]+"))
				continue;

			result[0] += Integer.parseInt(req.getHeader(h));
		}

		//~: response as the header
		res.addHeader("Response", "" + result[0]);

		//~: response as the body
		res.write(new Support.CharBytes("" + result[0]).input());
	};

	private Thread[] spawn(int n, Task task)
	{
		EX.assertx(n >= 1);

		//~: random seeds
		long[] seeds = new long[n];
		for(int i = 0;(i < n);i++)
			seeds[i] = this.seeder.nextLong();

		//~: create threads
		Thread[] threads = new Thread[n];
		IntStream.range(0, n).forEach(i ->
		{
			threads[i] = new Thread(() ->
			{
				gen.set(new Random(seeds[i]));

				try
				{
					task.run(i);
				}
				catch(Throwable e)
				{
					e.printStackTrace(System.err);
				}
			});
		});

		for(int i = 0;(i < n);i++)
			threads[i].start();

		return threads;
	}

	private ThreadLocal<Random> gen = new ThreadLocal<>();

	@FunctionalInterface
	interface Task
	{
		void run(int index)
		  throws Throwable;
	}
}