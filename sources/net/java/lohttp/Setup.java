package net.java.lohttp;

/* Java */

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * HTTP Server configuration.
 *
 * @author anton.baukin@gmail.com
 */
public class Setup implements Cloneable
{
	/**
	 * IP to listen on, defaults to "127.0.0.1".
	 */
	private String address = "127.0.0.1";

	/**
	 * TCP port of the server, defaults to 8080.
	 */
	private int port = 8080;

	/**
	 * Number of socket wait queue, see ServerSocket.
	 * Defaults to zero that means the system default.
	 */
	private int backlog;

	/**
	 * Socket timeout in milliseconds.
	 * Zero value means the system default.
	 */
	private int soTimeout;

	/**
	 * The pool for incoming requests worker.
	 */
	private Executor pool;

	/**
	 * The limit in bytes of HTTP preamble that includes
	 * the query line with the parameters, the headers,
	 * and empty line that denotes the body.
	 *
	 * Defaults to 128 KiB.
	 */
	private int preambleLimit = 1024 * 128;

	/**
	 * Executed in the socket binding thread.
	 *
	 * Invoked on the response when there's no available
	 * worker to handle the request. Note that the request
	 * object is not created here not to consume any
	 * resources: raw socket is given instead!
	 * Second argument is the exception raised.
	 */
	private Callback deny;

	/**
	 * Callback invoked within a worker thread to process
	 * the request. Arguments are the request and response.
	 */
	private Callback execute;

	/**
	 * Indicates that the configuration is fixed by the server.
	 * Cloned configuration is always not fixed.
	 */
	private boolean fixed;


	/* Cloneable */

	public Setup clone()
	{
		try
		{
			Setup s = (Setup) super.clone();

			//!: un-fix
			s.fixed = false;

			return s;
		}
		catch(Throwable e)
		{
			throw EX.wrap(e);
		}
	}


	/* Java Bean */

	public String getAddress()
	{
		return address;
	}

	public Setup setAddress(String address)
	{
		EX.assertx(!fixed);
		this.address = EX.asserts(address);
		return this;
	}

	public int getPort()
	{
		return port;
	}

	public Setup setPort(int port)
	{
		EX.assertx(!fixed);
		EX.assertx(port > 0 && port < 65536);
		this.port = port;
		return this;
	}

	public int getBacklog()
	{
		return backlog;
	}

	public Setup setBacklog(int backlog)
	{
		EX.assertx(!fixed);
		EX.assertx(backlog >= 0);
		this.backlog = backlog;
		return this;
	}

	public int getSoTimeout()
	{
		return soTimeout;
	}

	public void setSoTimeout(int soTimeout)
	{
		EX.assertx(!fixed);
		EX.assertx(soTimeout >= 0);
		this.soTimeout = soTimeout;
	}

	public Executor getPool()
	{
		return pool;
	}

	public Setup setPool(Executor pool)
	{
		EX.assertx(!fixed);
		this.pool = EX.assertn(pool);
		return this;
	}

	public int getPreambleLimit()
	{
		return preambleLimit;
	}

	public Setup setPreambleLimit(int n)
	{
		EX.assertx(n > 0);
		this.preambleLimit = n;
		return this;
	}

	public Callback getDeny()
	{
		return deny;
	}

	public Setup setDeny(Callback deny)
	{
		EX.assertx(!fixed);
		this.deny = deny;
		return this;
	}

	public Callback getExecute()
	{
		return execute;
	}

	public Setup setExecute(Callback execute)
	{
		EX.assertx(!fixed);
		this.execute = execute;
		return this;
	}

	public boolean isFixed()
	{
		return fixed;
	}


	/* Configuration Helpers */

	public Setup setFixed()
	{
		this.fixed = true;
		return this;
	}

	/**
	 * Sets fixed execution pool with the given
	 * prefix of the threads. The minimum threads number
	 * N must be at least 2, the maximum M is not lower.
	 * The number of waiting tasks is denoted by W
	 * that may not be zero.
	 */
	public Setup setPool(int n, int m, int w, final String prefix)
	{
		EX.assertx(n >= 2);
		EX.assertx(m >= n);
		EX.assertx(w >  0);
		EX.asserts(prefix);

		//~: naming factory
		final int[]    i = new int[1];
		ThreadFactory tf = new ThreadFactory()
		{
			public Thread newThread(Runnable r)
			{
				Thread t = new Thread(r);
				t.setName(prefix + i[0]++);
				return t;
			}
		};

		//~: tasks queue
		BlockingQueue<Runnable> q = new ArrayBlockingQueue<Runnable>(w, true);

		//~: create the pool
		this.setPool(new ThreadPoolExecutor(n, m,
		  1000L, TimeUnit.MILLISECONDS, q, tf)
		);

		return this;
	}
}