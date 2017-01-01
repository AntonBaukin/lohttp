package net.java.lohttp;

/* Java */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Support utilities and classes.
 *
 * @author anton.baukin@gmail.com.
 */
public class Support
{
	/* Streaming Helpers */

	public static long  pump(InputStream i, OutputStream o)
	  throws IOException
	{
		byte[] b = Support.BUFFERS.get();
		long   s = 0L;

		try
		{
			for(int x;((x = i.read(b)) > 0);s += x)
				o.write(b, 0, x);

			return s;
		}
		finally
		{
			Support.BUFFERS.free(b);
		}
	}

	public static long  pump(Input i, OutputStream o)
	  throws IOException
	{
		byte[] b = Support.BUFFERS.get();
		long   s = 0L;

		try
		{
			for(int x;((x = i.read(b, 0, b.length)) > 0);s += x)
				o.write(b, 0, x);

			return s;
		}
		finally
		{
			Support.BUFFERS.free(b);
		}
	}

	public static Input input(final InputStream i)
	{
		return new Input()
		{
			public int read(byte[] buf, int off, int len)
			{
				try
				{
					return i.read(buf, off, len);
				}
				catch(Throwable e)
				{
					throw EX.wrap(e);
				}
			}
		};
	}


	/* Shared Pool of Buffers */

	public static final ByteBuffers BUFFERS =
	  new ByteBuffers();

	/**
	 * Weak buffer of 512 byte arrays.
	 * Effective with mutual threads.
	 */
	public static final class ByteBuffers
	{
		public byte[] get()
		{
			final WeakReference<Queue<byte[]>> wr = this.pool.get();
			final Queue<byte[]> q = (wr == null)?(null):(wr.get());

			//?: {queue does not exist}
			if(q == null)
				return new byte[512];

			//~: poll the quueue
			final byte[] b = q.poll();

			//~: return | create
			return (b != null)?(b):(new byte[512]);
		}

		public void   free(Collection<byte[]> bufs)
		{
			//?: {nothing to do}
			if((bufs == null) || bufs.isEmpty())
				return;

			//~: existing pool
			final WeakReference<Queue<byte[]>> wr = this.pool.get();
			final Queue<byte[]> q = (wr == null)?(null):(wr.get());

			if(q != null) //?: {queue does exist}
			{
				//~: add valid buffers
				for(byte[] buf : bufs)
					if((buf != null) && (buf.length == 512))
						q.offer(buf);

				return;
			}

			//~: create own pool
			final Queue<byte[]> q2 = new ConcurrentLinkedQueue<byte[]>();
			final WeakReference<Queue<byte[]>> wr2 =
			  new WeakReference<Queue<byte[]>>(q2);

			//?: {swapped it not to the field} waste the buffers
			if(!this.pool.compareAndSet(wr, wr2))
				return;

			//~: add valid buffers
			for(byte[] buf : bufs)
				if((buf != null) && (buf.length == 512))
					q2.offer(buf);
		}

		public void   free(byte[] buf)
		{
			EX.assertx((buf != null) && (buf.length == 512));
			this.free(Collections.singleton(buf));
		}

		private final AtomicReference<WeakReference<Queue<byte[]>>>
			pool = new AtomicReference<WeakReference<Queue<byte[]>>>();
	}


	/* Effective In-Memory Output-Input */

	public static interface TakeBytes
	{
		boolean take(byte[] buf, int off, int len);
	}

	/**
	 * Output stream that uses shared buffers and
	 * allows simultaneous reading of written bytes.
	 * This implementation is not thread-safe!
	 */
	public static final class BytesStream extends OutputStream
	{
		/* public: BytesStream interface */

		/**
		 * Copies the bytes written to the stream given.
		 */
		public void        copy(OutputStream stream)
		  throws IOException
		{
			if(buffers == null)
				throw new IOException("ByteStream is closed!");

			if(buffers.isEmpty())
				return;

			byte[] last = buffers.get(buffers.size() - 1);
			for(byte[] buf : buffers)
				stream.write(buf, 0, (buf == last)?(position):(buf.length));
		}

		/**
		 * Copies the bytes to the array given and returns
		 * the number of bytes actually copied.
		 */
		public int         copy(byte[] a, int off, int len)
		  throws IOException
		{
			if(buffers == null)
				throw new IOException("ByteStream is closed!");

			if(buffers.isEmpty())
				return 0;

			int    res  = 0;
			byte[] last = buffers.get(buffers.size() - 1);

			for(byte[] buf : buffers)
			{
				int sz = (buf == last)?(position):(buf.length);
				if(sz > len) sz = len;

				System.arraycopy(buf, 0, a, off, sz);
				off += sz; len -= sz; res += sz;

				if(len == 0) break;
			}

			return res;
		}

		/**
		 * Returns a copy of the bytes written.
		 */
		public byte[]      bytes()
		  throws IOException
		{
			byte[] res = new byte[(int) length()];
			int    csz = copy(res, 0, res.length);

			EX.assertx(res.length == csz);
			return res;
		}

		public void        each(TakeBytes take)
		  throws IOException
		{
			each(0L, Long.MAX_VALUE, take);
		}

		/**
		 * Invokes the callback on the inner buffers
		 * without copying them. If callback returns
		 * false, stops the iteration.
		 *
		 * It's not an error for both for the offset,
		 * or the length to go over the bytes end.
		 */
		public void        each(long offset, long length, TakeBytes take)
		  throws IOException
		{
			if(buffers == null)
				throw new IOException("ByteStream is closed!");

			EX.assertx(offset >= 0L);
			EX.assertx(length >= 0L);
			EX.assertn(take);

			//HINT: all the buffers have fixed size 512.

			int b = (int)(offset / 512);
			int o = (int)(offset % 512);

			//?: {no overflow}
			EX.assertx(512L * b + o == offset);

			//c: for all buffers on the right
			for(int i = b;(i < buffers.size());i++)
			{
				byte[] buf = buffers.get(i);
				int    io  = (i == b)?(o):0;
				int    il  = (i + 1 == buffers.size())?(position):(buf.length);

				//~: in-buffer offset
				il -= io;

				//?: {gained the limit}
				if(il > length) il = (int) length;

				//?: {has no bytes} in this buffer
				if(il <= 0) continue;

				//?: {user break}
				if(!take.take(buf, io, il))
					break;

				//~: advance the limit
				length -= il;
			}
		}

		/**
		 * Writes all the bytes from the stream given.
		 * The stream is not closed in this call.
		 */
		public void        write(InputStream stream)
		  throws IOException
		{
			byte[] buf = BUFFERS.get();
			int    sz;

			try
			{
				while((sz = stream.read(buf)) > 0)
					write(buf, 0, sz);
			}
			finally
			{
				BUFFERS.free(buf);
			}
		}

		public long        length()
		{
			return this.length;
		}

		/**
		 * Creates input stream to read all the bytes.
		 * It shares the same data as the bytes stream,
		 * thus may not be used as thread-safe component.
		 */
		public InputStream inputStream()
		{
			return new Stream();
		}

		/**
		 * Extends {@link #inputStream()} to take
		 * a sub-sequence of the bytes stream.
		 *
		 * It's not an error to go out of the bytes available,
		 * both in the offset, and the length.
		 */
		public InputStream inputStream(long offset, long length)
		{
			EX.assertx(offset >= 0L);
			EX.assertx(length >= 0L);

			//HINT: all the buffers have fixed size 512.

			int b = (int)(offset / 512);
			int o = (int)(offset % 512);

			//?: {no overflow}
			EX.assertx(512L * b + o == offset);

			return new Stream(b, o, length);
		}

		public boolean     isNotCloseNext()
		{
			return notCloseNext;
		}

		/**
		 * Allows not to close the stream on next close request.
		 */
		public BytesStream setNotCloseNext(boolean notCloseNext)
		{
			this.notCloseNext = notCloseNext;
			return this;
		}

		public boolean     isNotClose()
		{
			return notClose;
		}

		/**
		 * Rejects closing till the flag is cleared.
		 */
		public BytesStream setNotClose(boolean notClose)
		{
			this.notClose = notClose;
			return this;
		}


		/* public: OutputStream interface */

		public void write(int b)
		  throws IOException
		{
			if(byte1 == null)
				byte1 = new byte[1];
			byte1[0] = (byte) b;

			this.write(byte1, 0, 1);
		}

		public void write(byte[] b, int off, int len)
		  throws IOException
		{
			if(buffers == null)
				throw new IOException("ByteStream is closed!");

			while(len > 0)
			{
				//?: {no a buffer}
				if(buffers.isEmpty())
				{
					byte[] xb = BUFFERS.get();
					EX.assertx(xb.length == 512);

					buffers.add(xb);
					continue;
				}

				byte[] x = buffers.get(buffers.size() - 1);
				int    s = x.length - position;

				//?: {has no free space in the current buffer}
				if(s == 0)
				{
					byte[] xb = BUFFERS.get();
					EX.assertx(xb.length == 512);

					buffers.add(xb);
					position = 0;
					continue;
				}

				//?: {restrict free space to the length left}
				if(s > len) s = len;

				System.arraycopy(b, off, x, position, s);
				off += s; len -= s; position += s;
				this.length += s;
			}
		}

		public void erase()
		  throws IOException
		{
			if(buffers == null)
				throw new IOException("ByteStream is closed!");

			BUFFERS.free(buffers);
			buffers.clear();

			length = position = 0;
		}

		public void flush()
		  throws IOException
		{
			if(buffers == null)
				throw new IOException("ByteStream is closed!");
		}

		public void close()
		{
			if((buffers == null) | notClose)
				return;

			if(notCloseNext)
			{
				notCloseNext = false;
				return;
			}

			BUFFERS.free(buffers);
			buffers = null;
		}

		public void closeAlways()
		{
			this.notClose = this.notCloseNext = false;
			this.close();
		}


		/* Input Stream */

		private class Stream extends InputStream
		{
			public Stream()
			{
				this.limit = Long.MAX_VALUE;
			}

			public Stream(int bufind, int bufpos, long limit)
			{
				this.bufind = bufind;
				this.bufpos = bufpos;
				this.limit  = limit;
			}


			/* public: InputStream interface */

			public int     read()
			  throws IOException
			{
				if(byte1 == null)
					byte1 = new byte[1];

				int x = this.read(byte1, 0, 1);
				return (x <= 0)?(-1):(byte1[0] & 0xFF);
			}

			public int     read(byte[] b, int off, int len)
			  throws IOException
			{
				EX.assertn(b);
				if((off < 0) | (len < 0) | (len > b.length - off))
					throw new IndexOutOfBoundsException();

				if(buffers == null)
					throw new IOException("ByteStream is closed!");
				if(bufind  == -1)
					throw new IOException("Input Stream of ByteStream is closed!");

				if(buffers.isEmpty())
					return -1;

				//~: limit the length
				if(len > limit) len = (int)limit;

				int got = 0;
				while(len != 0)
				{
					byte[] buf = buffers.get(bufind);
					int    sz;

					//?: {it is the current buffer}
					if(bufind == buffers.size() - 1)
					{
						if(bufpos >= position)
							break;

						sz = position - bufpos;
					}
					//!: it is one of the fully filled buffers
					else if(bufpos == buf.length)
					{
						bufind++; bufpos = 0;
						continue;
					}
					else
						sz = buf.length - bufpos;

					if(sz > len) sz = len;
					System.arraycopy(buf, bufpos, b, off, sz);
					bufpos += sz; off += sz; got += sz;
					len -= sz; limit -= sz;
				}

				return (got == 0)?(-1):(got);
			}

			public void    close()
			  throws IOException
			{
				this.bufind = -1;
			}

			public boolean markSupported()
			{
				return false;
			}


			/* private: read position */

			private int    bufind;
			private int    bufpos;
			private long   limit;
			private byte[] byte1;
		}


		/* private: list of buffers */

		private ArrayList<byte[]> buffers =
		  new ArrayList<byte[]>(16);

		/**
		 * The position within the last
		 * buffer of the list.
		 */
		private int     position;
		private long    length;
		private boolean notCloseNext;
		private boolean notClose;
		private byte[]  byte1;
	}


	/* Characters Bytes */

	/**
	 * Takes a character sequence, splits it in
	 * multiple small buffers (on demand) and
	 * provides them as input stream.
	 *
	 * Replaces String.getBytes("UTF-8") not
	 * producing large byte buffers.
	 */
	public static final class CharBytes extends InputStream
	{
		public CharBytes(CharSequence string)
		{
			this(256, string);
		}

		public CharBytes(int buffer, CharSequence string)
		{
			this.buffer = buffer;
			this.string = EX.assertn(string);
			EX.assertx(buffer > 0);

			try
			{
				this.o = new OutputStreamWriter(w, "UTF-8");
			}
			catch(Exception e)
			{
				throw EX.wrap(e);
			}
		}

		public final CharSequence string;


		/* Input Stream */

		public int   read()
		  throws IOException
		{
			if((b == null) || (j == b.length))
			{
				b = next();

				if(b == null)
					return -1;
			}

			return b[j++] & 0xFF;
		}

		public int   read(byte[] buf, int o, int l)
		  throws IOException
		{
			int s = 0;

			while(l > 0)
			{
				if((b == null) || (j == b.length))
				{
					b = next();

					if(b == null)
						break;
				}

				//~: the remaining length
				int x = Math.min(l, b.length - j);

				System.arraycopy(b, j, buf, o, x);
				j += x; o += x; l -= x; s += x;
			}

			if(s > 0)
				return s;

			if((b == null) || (j == b.length))
				b = next();

			return (b == null)?(-1):(0);
		}

		public void  close()
		{
			j = string.length();
			b = null;
		}

		public Input input()
		{
			return new Input()
			{
				public int read(byte[] buf, int off, int len)
				{
					try
					{
						return CharBytes.this.read(buf, off, len);
					}
					catch(Throwable e)
					{
						throw EX.wrap(e);
					}
				}
			};
		}


		/* private: local buffer */

		/**
		 * Converts the following sequence of characters
		 * with support for surrogate pairs.
		 */
		private byte[] next()
		  throws IOException
		{
			final int sl = string.length();

			//?: {no characters}
			if(i >= sl)
				return null;

			//~: length to copy
			int l = Math.min(buffer, sl - i);

			//~: substring of the interest
			String x = string.subSequence(i, i + l).toString();

			//~: write it
			o.write(x);
			i += l;
			o.flush();

			j = 0; //<-- start the new buffer
			return w.reset();
		}

		/**
		 * Current index in the string.
		 */
		private int i;

		/**
		 * Current index in the buffer.
		 */
		private int j;

		/**
		 * Current buffer.
		 */
		private byte[] b;

		/**
		 * Approximated size of the bytes buffer.
		 * Used primary for the testing.
		 */
		private final int buffer;

		/**
		 * Wrapper of the encoding stream
		 */
		private final WrappingBytes w = new WrappingBytes();

		private final OutputStreamWriter o;


		/* Wrapping Bytes */

		private class WrappingBytes extends OutputStream
		{
			public void   write(int b)
			  throws IOException
			{
				bos.write(b);
				length++;
			}

			public void   write(byte[] b, int off, int len)
			  throws IOException
			{
				bos.write(b, off, len);
				length += len;
			}

			public byte[] reset()
			{
				byte[] a = bos.toByteArray();

				bos = new ByteArrayOutputStream(buffer * 2);
				length = 0;

				return a;
			}

			private ByteArrayOutputStream bos =
			  new ByteArrayOutputStream(buffer * 2);

			private int length;
		}
	}


	/* Limited Input Stream */

	public static final class LimitedInput extends InputStream
	{
		public LimitedInput(InputStream input, long limit)
		{
			this.input = input;
			this.limit = limit;

			EX.assertx(limit >= 0L);
		}

		public final InputStream input;

		/* Input Stream */

		public int  read()
		  throws IOException
		{
			if(limit == 0L)
				return -1;

			int b = input.read();

			if(b != -1)
				limit--;

			return b;
		}

		public int read(byte[] b, int off, int len)
		  throws IOException
		{
			if(limit == 0L)
				return -1;

			//?: {wrong request}
			if(len == 0) return 0;
			if(len < 0) throw new IOException("Illegal array length!");

			if(len > limit) //?: {got over the limit}
				len = (int) limit;

			//~: read from the stream
			int s = input.read(b, off, len);

			if(s > 0) //<-- lower the limit
				limit -= s;

			return s;
		}

		private long limit;

		public void close()
		  throws IOException
		{
			input.close();
		}
	}


	/* Composite Input Stream */

	public static class CompositeInput extends InputStream
	{
		public CompositeInput(InputStream... inputs)
		{
			this.inputs = inputs;
		}

		protected final InputStream[] inputs;


		/* Input Stream */

		public int read()
		  throws IOException
		{
			while(index < inputs.length)
			{
				int b = inputs[index].read();

				if(b == -1)
					index++;
				else
					return b;
			}

			return -1;
		}

		public int read(byte[] b, int off, int len)
		  throws IOException
		{
			int s = 0;

			//?: {wrong request}
			if(len == 0) return 0;
			if(len < 0) throw new IOException("Illegal array length!");

			//c: input operations
			while((index < inputs.length) && (len > 0))
			{
				int x = inputs[index].read(b, off, len);

				if(x <= 0)
				{
					index++;
					continue;
				}

				//~: advance the buffer
				s += x; off += x; len -= x;
			}

			return (s == 0)?(-1):(s);
		}

		protected int index;

		public void close()
		  throws IOException
		{
			Throwable error = null;

			for(InputStream i : inputs) try
			{
				i.close();
			}
			catch(Throwable e)
			{
				error = EX.sup(error, e);
			}

			if(error instanceof IOException)
				throw (IOException) error;
			else if(error != null)
				throw EX.wrap(error);
		}
	}



	/* Thread Allocator */

	/**
	 * Allocates a thread and waits for single task.
	 */
	public static final class Allocator implements Runnable
	{
		/**
		 * For thread pool only!
		 */
		public void    run()
		{
			//~: notify is ready
			ready.countDown();

			try
			{
				//~: wait for a task
				run.await();

				final Runnable task = this.task.get();

				//~: execute the task
				if(task != null)
					task.run();
			}
			catch(Throwable ignore)
			{}
			finally
			{
				//!: release the waiting threads
				done.countDown();
			}
		}

		/**
		 * Allocator may execute one task only.
		 * When it's occupied, this call returns false.
		 */
		public boolean run(Runnable task)
		{
			EX.assertn(task);

			//?: {assigned the task}
			if(this.task.compareAndSet(null, task))
			{
				run.countDown(); //<-- release the wait
				return true;
			}

			return false;
		}

		/**
		 * Waits till the thread allocated.
		 */
		public void    await(long ms)
		{
			EX.assertx(ms >= 0L);

			//~: wait for the end
			try
			{
				if(ms == 0L)
					ready.await();
				else
					ready.await(ms, TimeUnit.MILLISECONDS);
			}
			catch(InterruptedException e)
			{
				throw EX.wrap(e);
			}
		}

		/**
		 * If not task was assigned, assignes Nothing,
		 * thus releasing the pool. Waits not!
		 */
		public void    release()
		{
			//~: try assign nothing
			this.run(Nothing);
		}

		/**
		 * Releases and waits for the quit.
		 */
		public void    join()
		{
			//~: try assign nothing
			this.run(Nothing);

			//~: wait for the end
			try
			{
				done.await();
			}
			catch(InterruptedException e)
			{
				throw EX.wrap(e);
			}
		}

		private final CountDownLatch ready =
		  new CountDownLatch(1);

		private final CountDownLatch run =
		  new CountDownLatch(1);

		private final CountDownLatch done =
		  new CountDownLatch(1);

		private final AtomicReference<Runnable> task =
		  new AtomicReference<Runnable>();

		public static final Runnable Nothing = new Runnable()
		{
			public void run()
			{}
		};
	}


	/* Barrier Synchronization Primitive */

	/**
	 * Counting barrier that releases the waiting
	 * threads when it reaches zero.
	 *
	 * Warning: be aware that while the waiting
	 * threads are resumed the counter may go
	 * up again as this is not a count down.
	 */
	public static final class Barrier
	{
		/**
		 * Increments the counter if the barrier
		 * is not locked, else throws exception.
		 *
		 */
		public void inc()
		{
			if(n.incrementAndGet() <= 0)
				throw EX.ass();
		}

		/**
		 * Decrements the counter releasing the threads
		 * when it reaches zero. Throws exception if
		 * the barrier is not locked.
		 */
		public void dec()
		{
			final int n = this.n.decrementAndGet();

			if(n != 0)
			{
				if(n <= 0)
					throw EX.ass();
				return;
			}

			synchronized(this)
			{
				final int n2 = this.n.get();

				if(n2 == 0)
					this.notifyAll();
			}
		}

		/**
		 * Waits till the barrier goes to zero.
		 */
		public void await()
		  throws InterruptedException
		{
			synchronized(this)
			{
				while(true)
				{
					final int n = this.n.get();

					if(n == 0)
						return;

					EX.assertx(n > 0);
					this.wait();
				}
			}
		}

		private final AtomicInteger n =
		  new AtomicInteger();
	}
}