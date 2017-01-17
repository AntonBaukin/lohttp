package net.java.lohttp;

/* Java */

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/* Lo* HTTP */

import net.java.lohttp.Requests.Scanner;
import net.java.lohttp.Responses.Dirty;
import net.java.lohttp.Responses.ResponseWrapper;
import net.java.lohttp.Support.Allocator;
import net.java.lohttp.Support.Barrier;


/**
 * Implements tiny HTTP server.
 *
 * @author anton.baukin@gmail.com
 */
public class LowHat implements Server
{
	/* HTTP Server */

	public void start(Setup setup, Callback done)
	{
		EX.assertn(setup);

		synchronized(mutex)
		{
			EX.assertx(socket == null);
			EX.assertx(peer == null);

			//~: configure
			setup(setup);

			//~: allocate the executor
			Allocator exe = new Allocator();
			setup.getPool().execute(exe);

			//~: wait for it
			exe.await(0L);

			try
			{
				//~: create the socket
				socket = new ServerSocket();

				//~: reuse the port
				socket.setReuseAddress(true);

				//~: bind the socket
				socket.bind(new InetSocketAddress(InetAddress.
				  getByName(setup.getAddress()), setup.getPort()
				), setup.getBacklog());

				//~: create the peer
				peer = createServerPeer(socket);

				//~: execute it
				exe.run(peer);

				//~: pre-allocate a hot thread
				this.allocated.set(null);
				preallocate();

				//~: wait for the start
				peer.started.await();

				//~: notify the callback
				if(done != null)
					done.act(this);
			}
			catch(Throwable e)
			{
				//~: release the socket
				if(socket != null) try
				{
					socket.close();
				}
				catch(Throwable x)
				{
					EX.sup(e, x);
				}
				finally
				{
					socket = null;
				}

				//~: relese the executor
				if(exe != null)
					exe.run(Allocator.Nothing);

				throw EX.wrap(e);
			}
		}
	}

	public void hangup(Callback done)
	{
		synchronized(mutex)
		{
			Throwable error = null;

			try
			{
				//~: pause the workers
				pause(true);

				//~: wait for the tasks
				tasks.await();
			}
			catch(Throwable e)
			{
				error = e;
			}
			finally
			{
				//~: notify the callback
				if(done != null) try
				{
					if(error == null)
						done.act(this);
					else
						done.act(this, error);
				}
				catch(Throwable e)
				{
					error = EX.sup(error, e);
				}
			}

			if(error != null)
				throw EX.wrap(error);
		}
	}

	public void resume()
	{
		synchronized(mutex)
		{
			//~: resume the workers
			pause(false);
		}
	}

	public void close()
	{
		synchronized(mutex)
		{
			//?: {socket is not bound}
			if(socket == null)
				return;

			//~: safe swap socket
			final ServerSocket s = socket;
			socket = null;

			//~: safe swap peer
			final ServerPeer p = peer;
			peer = null;

			try //~: close the socket
			{
				s.close();
			}
			catch(Throwable e)
			{
				throw EX.wrap(e);
			}
		}
	}


	/* protected: server runtime state */

	protected final Object mutex = new Object();

	protected Setup setup;

	protected ServerSocket socket;

	protected ServerPeer peer;


	/* Server Peer */

	protected ServerPeer createServerPeer(ServerSocket socket)
	{
		return new ServerPeer(socket);
	}

	protected class ServerPeer implements Runnable
	{
		public ServerPeer(ServerSocket socket)
		{
			this.socket = socket;
		}

		public final ServerSocket socket;


		/* Server Peer */

		public void run()
		{
			//~: notify started
			started.countDown();

			//c: socket handling cycle
			while(!socket.isClosed() && socket.isBound()) try
			{
				//~: wait for the incoming request
				handle(socket.accept());
			}
			catch(Throwable ignore)
			{
				//~: continue operations
			}

			//~: notify exited
			done.countDown();
		}

		public final CountDownLatch started =
		  new CountDownLatch(1);

		public final CountDownLatch done =
		  new CountDownLatch(1);
	}


	/* protected: setup & processing */

	protected void setup(Setup setup)
	{
		//?: {has no pool}
		if(setup.getPool() == null)
			setup.setPool(2, 4, 16, "LowHat-");

		//!: fix the setup
		setup.setFixed();

		//=: configuration
		this.setup = setup;
	}

	/**
	 * Executed by the main accepting thread.
	 */
	protected void handle(final Socket s)
	{
		//~: target work task
		final Runnable task = new Runnable()
		{
			public void run()
			{
				try
				{
					worker(s);
				}
				finally
				{
					try //~: close the socket
					{
						s.close();
					}
					catch(Throwable ignore)
					{}
					finally
					{
						//~: exclude task from the set
						tasks.dec();
					}
				}
			}
		};

		//~: count the task
		tasks.inc();

		try //~: allocate worker
		{
			allocate(task);
		}
		catch(Throwable e)
		{
			final Callback deny = setup.getDeny();

			try //~: unavailable
			{
				//?: {no user-defined deny}
				if(deny == null)
					Responses.Unavailable.write(s.getOutputStream());
				//~: invoke the deny on the socket
				else
					deny.act(s, e);
			}
			catch(Throwable ignore)
			{}
			finally
			{
				try //!: close the socket now
				{
					s.close();
				}
				catch(Throwable ignore)
				{}
				finally
				{
					//~: exclude task from the set
					tasks.dec();
				}
			}
		}
	}

	/**
	 * Handles the request and writes the response.
	 * Executed in own thread and throws nothing.
	 */
	protected void worker(Socket s)
	{
		final Callback ex = setup.getExecute();
		boolean        id = true; //<-- is dirty

		try
		{
			//~: socket timeout
			if(setup.getSoTimeout() != 0)
				s.setSoTimeout(setup.getSoTimeout());

			if(ex == null) //?: {no executor}
				Responses.NotImplemented.write(s.getOutputStream());
			else
			{
				//~: parse the request
				Request req = null; try
				{
					req = parseRequest(s.getInputStream());
				}
				catch(ResponseWrapper w)
				{
					w.response.write(s.getOutputStream());
				}

				if(req != null) //?: {got valid request}
				{
					//~: create the response
					Response res = createResponse(s);

					try //!: execute the handler
					{
						ex.act(req, res, s);
					}
					finally
					{
						if(res instanceof Dirty)
							id = ((Dirty)res).isDirty();
					}
				}
			}
		}
		catch(Throwable e)
		{
			if(!id) try //?: {not yet committed}
			{
				Responses.Error.write(s.getOutputStream());
			}
			catch(Throwable ignore)
			{
				//!: worker throws nothing
			}
		}
	}

	protected void allocate(Runnable task)
	  throws Throwable
	{
		//~: take cached allocator
		final Object xa = allocated.get();

		//?: {server is paused}
		if(xa == this.paused)
			throw new IllegalStateException();

		//~: {cached allocator got our task}
		if((xa != null) && ((Allocator)xa).run(task))
		{
			//~: release it
			allocated.compareAndSet(xa, null);

			//~: cache new worker
			preallocate();

			return;
		}

		//~: allocate a new instance
		final Allocator na = new Allocator();

		//~: schedule the execution
		setup.getPool().execute(na);

		//~: assign our task there
		EX.assertx(na.run(task));

		//~: cache new worker
		preallocate();
	}

	protected void preallocate()
	{
		Allocator a = new Allocator();

		try //~: run for the pool
		{
			setup.getPool().execute(a);
		}
		catch(Throwable ignore)
		{
			//~: could not schedule
			return;
		}

		//?: {not assigned} release the thread
		if(!allocated.compareAndSet(null, a))
			a.release();
	}

	protected void pause(boolean paused)
	{
		if(!paused) //?: {release pause}
		{
			//~: clear the pause flag
			if(allocated.compareAndSet(this.paused, null))
				preallocate(); //<-- cache a thread

			return;
		}

		//~: assign paused flag
		final Object xa = allocated.getAndSet(this.paused);

		//~: release cached allocator
		if((xa != null) && (xa != this.paused))
			((Allocator)xa).release();
	}

	/**
	 * Flag object indicating that the server is paused.
	 */
	protected final Object paused = new Object();

	/**
	 * Hot execution thread pre-allocated, or paused flag.
	 */
	protected final AtomicReference<Object>
	  allocated = new AtomicReference<Object>();

	/**
	 * Currently executed (allocated) tasks.
	 */
	protected final Barrier tasks = new Barrier();


	/* protected: http handling */

	/**
	 * Perses the request. Returns Request instance
	 * to continue, or Response to report an error.
	 */
	protected Request  parseRequest(InputStream s)
	  throws ResponseWrapper, IOException
	{
		Scanner req = new Scanner(setup, s);

		//?: {is GET}
		if("GET".equals(req.getMethod()))
			return req.upgrade(Requests.Got.class);

		//~: upgrade to general bodied request
		return req.upgrade(Requests.Bodied.class);
	}

	protected Response createResponse(Socket s)
	  throws IOException
	{
		return new Responses.Headed(s.getOutputStream());
	}
}