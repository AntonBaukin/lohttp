package net.java.lohttp;

/* Java */

import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;


/**
 * Handling of HTTP responses.
 *
 * @author anton.baukin@gmail.com.
 */
public class Responses
{
	/* Basic Response */

	/**
	 * Abstract implementation base.
	 */
	public static abstract class Basic implements Response
	{
		/* Response */

		public int getStatus()
		{
			return 0;
		}

		protected int status = 200;

		public void setStatus(int status)
		{
			EX.assertx(status >= 100 && status < 600);
			this.status = status;
		}

		public void addHeader(String name, String value)
		{
			throw EX.ass();
		}

		public void write(Input body)
		{
			if(body != null)
				throw EX.ass();
		}
	}


	/* Status Only Response */

	/**
	 * Response with only the status code and
	 * optional status text in ASCII encoding.
	 */
	public static final class Status extends Basic
	{
		public Status(int status, String text)
		{
			EX.assertx(status >= 100 && status < 600);
			this.status = status;
			this.text   = text;
			this.output = encode();
		}

		public final String text;


		/* Socket Write */

		public void    write(Object socket)
		{
			try
			{
				if(socket instanceof Socket)
					write(((Socket)socket).getOutputStream());
				else if(socket instanceof OutputStream)
					write((OutputStream) socket);
			}
			catch(Throwable ignore)
			{}
		}

		public void    write(OutputStream socket)
		{
			try
			{
				socket.write(output);
			}
			catch(Throwable ignore)
			{}
		}

		private byte[] encode()
		{
			try
			{
				StringBuilder s = new StringBuilder(64);

				s.append("HTTP/1.1 ");
				s.append(status);

				if(text != null)
					s.append(' ').append(text);

				s.append("\r\n\r\n");
				return s.toString().getBytes("ASCII");
			}
			catch(Throwable e)
			{
				throw EX.wrap(e);
			}
		}

		private final byte[] output;
	}


	/* Common Status Responses */

	public static final Status BadRequest =
	  new Status(400, "Bad Request");

	public static final Status NotMethod =
	  new Status(405, "Method Not Allowed");

	public static final Status RequestTooLarge =
	  new Status(431, "Request Header Fields Too Large");

	public static final Status Error =
	  new Status(500, "Internal Server Error");

	public static final Status NotImplemented =
	  new Status(501, "Not Implemented");

	public static final Status Unavailable =
	  new Status(503, "Service Unavailable");


	/* Response Wrapped for Exception */

	public static class ResponseWrapper extends RuntimeException
	{
		public ResponseWrapper(Status response)
		{
			this.response = response;
		}

		public final Status response;
	}


	/* Dirty Response */

	/**
	 * Answers true when anything (successfully
	 * or not) was written to the socket output.
	 */
	public static interface Dirty
	{
		boolean isDirty();
	}


	/* Response with Headers */

	public static class Headed extends Basic implements Dirty
	{
		public Headed(OutputStream socket)
		{
			this.socket = socket;
		}

		protected OutputStream socket;


		/* Response */

		public boolean isDirty()
		{
			return dirty;
		}

		protected boolean dirty;

		public void    addHeader(String name, String value)
		{
			EX.asserts(name);
			EX.asserts(value);
			EX.assertx(!dirty);

			headers.add(name);
			headers.add(value);
		}

		public void    setStatus(int status)
		{
			EX.assertx(!dirty);
			super.setStatus(status);
		}

		/**
		 * Pairs of header name and value.
		 */
		protected final List<String> headers = new ArrayList<String>(8);

		public void    write(Input body)
		{
			try
			{
				if(!dirty) //?: {the firsts write}
					preamble();

				//?: {actual content presents}
				if(body != null)
					Support.pump(body, socket);
			}
			catch(Throwable e)
			{
				throw EX.wrap(e);
			}
			finally
			{
				dirty = true;
			}
		}


		/* protected: response handling */

		/**
		 * Writes HTTP response preamble:
		 * the status line, and the headers.
		 */
		protected void preamble()
		  throws Throwable
		{
			StringBuilder s = new StringBuilder(256);

			//~: status line
			s.append("HTTP/1.1 ");
			s.append(status);

			if(status == 200)
				s.append(" OK");

			s.append("\r\n");

			//~: the headers
			for(int i = 0;(i < headers.size());i += 2)
				s.append(headers.get(i)).
				  append(": ").
				  append(headers.get(i+1)).
				  append("\r\n");

			//~: preamble delimiter line
			s.append("\r\n");

			//!: stream out
			Support.pump(new Support.CharBytes(s), socket);
		}
	}
}