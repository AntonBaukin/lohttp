package net.java.lohttp;

/* Java */

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* lo*-http: support */

import net.java.lohttp.Responses.ResponseWrapper;
import net.java.lohttp.Support.BytesStream;
import net.java.lohttp.Support.TakeBytes;


/**
 * Handling of HTTP requests.
 *
 * @author anton.baukin@gmail.com
 */
public class Requests
{
	/* Get Request */

	/**
	 * Represents GET request.
	 */
	public static class Got extends Basic implements Get
	{
		/**
		 * Assigns the HTTP preamble data, but skips
		 * the socket stream as further input is.
		 */
		public Got(Basic source, InputStream socket)
		{
			super(source);

			//?: {is a GET request}
			EX.assertx("GET".equals(source.getMethod()));
		}

		public String getMethod()
		{
			return "GET";
		}
	}


	/* Request with Body */

	public static class Bodied extends Basic implements Post
	{
		public Bodied(Basic source, InputStream input)
		{
			super(source);
			this.input = input;

			//?: {not a GET request}
			EX.assertx(!"GET".equals(source.getMethod()));
		}


		/* Post Request */

		/**
		 * Content type expected to decode
		 * the parameters of the request body.
		 */
		public static final String UE_BODY_CT =
		  "application/x-www-form-urlencoded";

		@SuppressWarnings("unchecked")
		public boolean     decode()
		{
			String ct = getHeader("Content-Type");

			//?: {not a form body request}
			if((ct == null) || !ct.contains(UE_BODY_CT))
				return false;

			try
			{
				//~: input reader
				Reader r = new InputStreamReader(input, "UTF-8");

				//~: resulting parameters
				Map<String, Object> ps = new LinkedHashMap<String, Object>();

				//~: parse the parameters
				Requests.params(r, ps);

				//?: {no parameters found}
				if(ps.isEmpty())
					return false;

				//~: clear cached names
				this.paramNames = null;

				//~: assign them
				for(Map.Entry<String, Object> e : ps.entrySet())
				{
					List<String> xs;

					//~: values to add
					if(e.getValue() instanceof String)
						xs = Arrays.asList((String) e.getValue());
					else
						xs = (List<String>) e.getValue();

					//~: value object
					Object vo = params.get(e.getKey());

					//?: {new single value}
					if((vo == null) && (xs.size() == 1))
					{
						params.put(e.getKey(), xs.get(0));
						continue;
					}

					//~: existing values
					List<String> es;

					if(vo instanceof String)
					{
						es = new ArrayList<String>(xs.size() + 1);
						es.add((String) vo);
						es.addAll(xs);
					}
					else if(vo != null)
					{
						es = new ArrayList<String>(xs.size() + ((String[])vo).length);
						es.addAll(Arrays.asList((String[])vo));
						es.addAll(xs);
					}
					else
						es = xs;

					//~: save the array
					params.put(e.getKey(), es.toArray(new String[es.size()]));
				}

				return true;
			}
			catch(Throwable e)
			{
				throw EX.wrap(e);
			}
		}

		public InputStream input()
		{
			return input;
		}

		protected final InputStream input;
	}


	/* Basic Request */

	public static abstract class Basic implements Request
	{
		/* HTTP Request */

		public String   getMethod()
		{
			return method;
		}

		protected String method;

		public String   getPath()
		{
			return path;
		}

		protected String path;

		public String   getParam(String name)
		{
			Object p = params.get(name);

			if(p == null)
				return null;

			if(p instanceof String)
				return (String) p;

			return ((String[])p)[0];
		}

		public String[] getParams()
		{
			if(paramNames == null)
				paramNames = params.keySet().
				  toArray(new String[params.size()]);

			return paramNames;
		}

		protected String[] paramNames;

		protected final HashMap<String, Object> params;

		public int      takeParams(String name, Take take)
		{
			Object p = params.get(name);

			if(p == null)
				return 0;

			if(p instanceof String)
			{
				take.accept((String)p);
				return 1;
			}

			for(String x : (String[])p)
				take.accept(x);
			return ((String[])p).length;
		}

		public String   getHeader(String name)
		{
			Object h = headers.get(name.toLowerCase());

			if(h == null)
				return null;

			if(h instanceof String)
				return (String) h;

			return ((String[])h)[0];
		}

		public String[] getHeaders()
		{
			if(headerNames == null)
				headerNames = headers.keySet().
				  toArray(new String[headers.size()]);

			return headerNames;
		}

		protected String[] headerNames;

		protected final HashMap<String, Object> headers;

		public int      takeHeaders(String name, Take take)
		{
			Object h = headers.get(name);

			if(h == null)
				return 0;

			if(h instanceof String)
			{
				take.accept((String)h);
				return 1;
			}

			for(String x : (String[])h)
				take.accept(x);
			return ((String[])h).length;
		}


		/* protected: HTTP Handling */

		/**
		 * Creates the parser and does scan.
		 */
		protected Basic(Setup setup)
		{
			this.setup   = setup;
			this.params  = new LinkedHashMap<String, Object>();
			this.headers = new LinkedHashMap<String, Object>();
		}

		protected final Setup setup;

		/**
		 * Copying constructor that does nothing.
		 */
		public Basic(Basic source)
		{
			this.setup   = source.setup;
			this.method  = source.method;
			this.params  = source.params;
			this.headers = source.headers;
			this.path    = source.path;
		}
	}


	/* Scanning Request */

	public static class Scanner extends Basic
	{
		public Scanner(Setup setup, InputStream socket)
		  throws IOException
		{
			super(setup);

			this.socket  = socket;
			this.parser  = parse();
		}


		/* Request Upgrade */

		/**
		 * Upgrades to the request class closing the parser.
		 * Scanner instance may not be used further!
		 */
		public Basic upgrade(Class<? extends Basic> c)
		{
			try
			{
				InputStream stream = socket;

				//?: {scan got out of the header}
				if(parser.bytes.length() > parser.whole.e)
				{
					final int    s = (int)(parser.bytes.length() - parser.whole.e);
					final byte[] b = new byte[s];

					//~: copy the bytes
					parser.bytes.each(parser.whole.e, s, new TakeBytes()
					{
						int o;

						public boolean take(byte[] buf, int off, int len)
						{
							System.arraycopy(buf, off, b, o, len);
							o += len;
							return true;
						}
					});

					//~: create composite stream
					stream = new Support.CompositeInput(
					  new ByteArrayInputStream(b), socket);
				}

				//?: {content length is provided} limited stream
				String length = getHeader("Content-Length");
				if(length != null)
					stream = new Support.LimitedInput(
					  stream, Long.parseLong(length));

				//~: create the new instance
				return c.getConstructor(Basic.class, InputStream.class).
				  newInstance(this, stream);
			}
			catch(Throwable e)
			{
				throw EX.wrap(e);
			}
			finally
			{
				//!: close the parser always
				parser.close();
			}
		}


		/* protected: parse operations */

		protected final InputStream socket;
		protected final Parser parser;

		protected Parser  parse()
		  throws IOException
		{
			//~: create the parser
			Parser p = new Parser(socket, setup.getPreambleLimit());

			//~: scan the preamble
			int x = p.scan();

			try
			{
				if(x == 2) //?: {limit reached}
					throw new ResponseWrapper(Responses.RequestTooLarge);
				else if(x != 0) //?: {format error}
					throw new ResponseWrapper(Responses.BadRequest);

				//~: decode the first line
				x = p.first();

				if(x != 0) //?: {parse error}
					throw new ResponseWrapper(Responses.BadRequest);

				//~: parse the headers
				x = p.headers();

				if(x != 0) //?: {parse error}
					throw new ResponseWrapper(Responses.BadRequest);

				try
				{
					postScan(p);
				}
				catch(Throwable ignore)
				{
					p.close();
					throw new ResponseWrapper(Responses.BadRequest);
				}
			}
			finally
			{
				//?: {not fine} close the parser
				if(x != 0)
					p.close();
			}

			return p;
		}

		protected void    postScan(Parser p)
		  throws Throwable
		{
			method(p);
			params(p);
			headers(p);
			path(p);
		}

		/**
		 * Assigns the request method.
		 */
		protected void    method(Parser p)
		  throws Exception
		{
			EX.assertn(p.method);

			//~: resulting string
			final StringBuilder s = new StringBuilder(8);

			//c: append the bytes
			p.bytes.each(p.method.b, p.method.e - p.method.b, new TakeBytes()
			{
				public boolean take(byte[] buf, int off, int len)
				{
					for(int i = 0; (i < len); i++)
					{
						final char c = Character.toUpperCase(
						  (char)(buf[off + i] & 0xFF));

						//?: {not a letter}
						EX.assertx(c >= 'A' & c <= 'Z');

						//~: accumulate
						s.append(c);
					}

					return true;
				}
			});

			//=: assign the method
			method = s.toString();
			EX.asserts(method);
		}

		/**
		 * Parses the parameters of the query.
		 */
		@SuppressWarnings("unchecked")
		protected void    params(Parser p)
		  throws Exception
		{
			//?: {no parameters}
			if(p.params == null)
				return;

			//~: input reader
			Reader r = new InputStreamReader(p.bytes.inputStream(
			  p.params.b, p.params.e - p.params.b), "UTF-8");

			//~: resulting parameters
			Map<String, Object> ps = new LinkedHashMap<String, Object>();

			//~: parse the parameters
			Requests.params(r, ps);

			//~: assign them
			for(Map.Entry<String, Object> e : ps.entrySet())
				if(e.getValue() instanceof String)
					params.put(e.getKey(), e.getValue());
				else
				{
					List<String> vs = (List<String>) e.getValue();
					params.put(e.getKey(), vs.toArray(new String[vs.size()]));
				}
		}

		/**
		 * Assigns the headers from the Parser.
		 */
		protected void    headers(Parser p)
		  throws Throwable
		{
			//?: {headers are not even}
			EX.assertx(p.headers.length % 2 == 0);

			//~: temporary string
			final StringBuilder s = new StringBuilder(64);
			final char[]        b = new char[16];

			//c: for each header
			for(int i = 0;(i < p.headers.length);i += 2)
			{
				Pair hp = p.headers[i];
				Pair vp = p.headers[i+1];

				//?: {header name is empty}
				EX.assertx(hp.b < hp.e);

				//?: {header value is empty}
				EX.assertx(vp.b < vp.e);

				//~: concatenate the ANSI name
				s.delete(0, s.length());
				p.bytes.each(hp.b, hp.e - hp.b, new TakeBytes()
				{
					public boolean take(byte[] buf, int off, int len)
					{
						for(int i = 0; (i < len); i++)
						{
							final char c = Character.toLowerCase(
							  (char)(buf[off + i] & 0xFF));

							//?: {is character}
							final boolean ic = (c >= 'a' & c <= 'z');

							//?: {is digit}
							final boolean id = (c >= '0' & c <= '9');

							//?: {is invalid character}
							if(!ic && !id && !isHeaderCharacter(c))
								throw EX.ass();

							//~: accumulate
							s.append(c);
						}

						return true;
					}
				});

				//~: header name
				final String hn = s.toString();
				s.delete(0, s.length());

				//~: concatenate the header value
				InputStreamReader vr = new InputStreamReader(
				  p.bytes.inputStream(vp.b, vp.e - vp.b), "UTF-8"
				);

				while(true)
				{
					int sz = vr.read(b);
					if(sz <= 0) break;
					s.append(b, 0, sz);
				}

				//?: {header not exists}
				if(!headers.containsKey(hn))
					headers.put(hn, s.toString());
				else
				{
					Object hv = headers.get(hn);

					if(hv instanceof String)
						hv = new String[]{ (String)hv, s.toString() };
					else
					{
						EX.assertx(hv instanceof String[]);
						String[] xhv = new String[((String[])hv).length + 1];
						System.arraycopy(hv, 0, xhv, 0, xhv.length - 1);
						xhv[xhv.length - 1] = s.toString();
						hv = xhv;
					}

					headers.put(hn, hv);
				}
			}
		}

		protected boolean isHeaderCharacter(final char c)
		{
			return (c == '-');
		}

		/**
		 * Assigns the request path.
		 */
		protected void    path(Parser p)
		  throws Exception
		{
			EX.assertn(p.path);

			//~: resulting string
			final StringBuilder s = new StringBuilder(32);
			final List<String>  a = new ArrayList<String>(8);

			//~: take procedure
			final TakeBytes take = new TakeBytes()
			{
				public boolean take(byte[] buf, int off, int len)
				{
					for(int i = 0; (i < len); i++)
					{
						final int c = buf[off + i] & 0xFF;

						if(c != '/')
						{
							s.appendCodePoint(buf[off + i] & 0xFF);
							continue;
						}

						//~: add separator
						if(a.isEmpty() || !"/".equals(a.get(a.size() - 1)))
							a.add("/");

						//~: add previous component
						if(s.length() != 0) try
						{
							a.add(URLDecoder.decode(s.toString(), "UTF-8"));
							s.delete(0, s.length());
						}
						catch(Throwable e)
						{
							throw EX.wrap(e);
						}
					}

					return true;
				}
			};

			//c: append the bytes
			p.bytes.each(p.path.b, p.path.e - p.path.b, take);

			//~: closing '/'
			take.take(new byte[]{'/'}, 0, 1);

			//?: {is empty path}
			EX.asserte(a);

			//~: join the path
			for(String x : a) s.append(x);

			//=: assign it
			path = s.toString();
		}
	}


	/* Request Parser */

	/**
	 * Begin-end (excluding) pair used by the parser.
	 */
	public static class Pair
	{
		/**
		 * Begin including.
		 */
		public final int b;

		/**
		 * End excluding.
		 */
		public final int e;

		public Pair(int b, int e)
		{
			this.b = b;
			this.e = e;
		}
	}

	public static interface TakeBytesResult extends TakeBytes
	{
		/* Processing Result */

		Object result();
	}

	/**
	 * HTTP preamble parser with the exposed state.
	 * Ends parsing when it meets empty line.
	 * Marks the positions, does no decoding.
	 */
	public static class Parser implements Closeable
	{
		public Parser(InputStream input, int limit)
		{
			this.input = input;
			this.bytes = new BytesStream();
			this.limit = limit;
		}


		/* Parser Parameters */

		/**
		 * The original input stream (of the socket).
		 */
		public final InputStream input;

		/**
		 * All bytes read from the socket. This includes
		 * the status line, optional headers, empty line,
		 * and possibly a part (smaller 512 bytes) of the
		 * body content.
		 */
		public final BytesStream bytes;

		/**
		 * Limit of the preamble size of the HTTP request.
		 * Parser may overcome it in part of 512 bytes.
		 */
		public final int limit;


		/* Parse Results */

		/**
		 * Whole HTTP preamble including the blank line.
		 * Note that if the bytes buffer has more bytes,
		 * those bytes are of the body.
		 *
		 * Assigned in scan() if was fine.
		 */
		public Pair whole;

		/**
		 * The first line of the query excluding
		 * the trailing '\n' or '\r\n'.
		 *
		 * Assigned in first() if was fine.
		 */
		public Pair first;

		public Pair method;

		public Pair path;

		/**
		 * Query paramaters excluding leading '?'
		 * and trailing spaces. Not assigned if
		 * the request has no parameters.
		 */
		public Pair params;

		public Pair protocol;

		/**
		 * The headers of the preamble. Assigned by headers(),
		 * if it was fine. Each event item is a header name
		 * excluding ':'.  Odd items are the values,
		 * line breaks excluded.
		 */
		public Pair[] headers;


		/* Parser */

		public void close()
		{
			bytes.close();
		}

		/**
		 * Initial parse operation that finds the HTT preamble
		 * that includes the query line, the headers, and empty
		 * line that delimiters the preamble from the body.
		 *
		 * Returns 0 when everything was fine, 1 in the case
		 * of illegal characters combination, 2 limit reached.
		 *
		 * Feeds all the bytes to {@link #bytes} stream. All
		 * following operations work on that buffer.
		 */
		public int  scan()
		  throws IOException
		{
			//~: parse states
			final int  OO = 0;
			final int  OR = 1;
			final int  ON = 2;
			final int  RN = 3;
			final int  NN = 4;
			final int RNR = 5;

			//~: parse variables
			byte[] b = Support.BUFFERS.get();
			int    l = this.limit;
			int    o = 0; //<-- offset
			int    x = 0; //<-- parse state

			try
			{
				//c: fill whole preamble
				while(l > 0)
				{
					//~: socket input
					int s = input.read(b);

					if(s <= 0) //?: {got nothing}
						break;

					//~: tee to the bytes
					bytes.write(b, 0, s);

					//~: limit
					l -= s;

					//c: scan depending on the state
					scan: for(int i = 0;(i < s);i++) switch(b[i] & 0xFF)
					{
						case '\r':
						{
							if(x == OO)
								x = OR;
							else if(x == RN)
								x = RNR;
							else
								return 1;
							break;
						}

						case '\n':
						{
							if(x == OO)
								x = ON;
							else if(x == OR)
								x = RN;
							else if(x == ON || x == RNR)
							{
								o += i + 1; //<-- offset after the new line
								x  = NN;    //<-- final state
								break scan;
							}
							else
								return 1;
							break;
						}

						default:
						{
							if(x == OR || x == RNR)
								return 1;

							x = OO; //<-- default state
						}
					}

					//?: {reached the stream end}
					if(x != NN && s < b.length)
						return 1;

					if(x == NN)
						break; //<-- offset is given

					o += s; //~: advance the offset
				}
			}
			finally
			{
				Support.BUFFERS.free(b);
			}

			//?: {reached the limit}
			if(x != NN)
				return 2;

			//=: whole to the offset
			this.whole = new Pair(0, o);

			return 0;
		}

		/**
		 * Parses the first line of the query that contains
		 * the method, query path, query parameters, protocol.
		 *
		 * Returns 0 when everything was fine, 1, 2, 3, 4 if
		 * method, path, parameters, or protocol were wrong.
		 */
		public int  first()
		  throws IOException
		{
			EX.assertn(whole);

			//~: parse the first line
			TakeFirst take = new TakeFirst();
			bytes.each(0, whole.e, take);

			return (Integer) take.result();
		}

		protected class TakeFirst implements TakeBytesResult
		{
			public int      o = 0;     //<-- global offset
			public int      b = 0;     //<-- begin of section
			public int      x = 1;     //<-- parse section
			public boolean in = false; //<-- in section?

			public boolean take(byte[] buf, int off, int s)
			{
				//c: scan depending on the state
				for(int i = 0;(i < s);i++) switch(buf[off + i] & 0xFF)
				{
					case ' ':
					{
						//~: skip spaces
						if(!in) break;

						if(x != 4)
							in = false;

						if(x == 1) //?: {method ends}
						{
							x = 2; //<-- path section
							method = new Pair(b, o + i);
						}
						else if(x == 2) //?: {path}
						{
							x = 4; //<-- no params
							path = new Pair(b, o + i);
						}
						else if(x == 3) //?: {parameters}
						{
							x = 4; //<-- protocol section
							params = new Pair(b, o + i);
						}

						break;
					}

					case '?':
					{
						if(x != 2) //?: {not in path}
							return false;

						path = new Pair(b, o + i);

						x = 3; //<-- set to params
						b = o + i + 1; //<-- after '?'
						break;
					}

					case '\r': case '\n':
				{
					if(x == 4) //?: {in protocol}
					{
						protocol = new Pair(b, o + i);
						first = new Pair(0, o + i);
						x = 5; //<-- final state
						return false;
					}

					return false;
				}

					default:
					{
						if(!in) //?: {start section}
						{
							b = o + i;
							in = true;
						}
					}
				}

				o += s; //~: advance the offset
				return true;
			}

			public Object  result()
			{
				//?: {reached final state}
				return (x == 5)?(0):(x);
			}
		}

		/**
		 * Parses the headers following the first line.
		 * Returns 0 when everything was fine, or (i + 1)
		 * for wrong header number i.
		 */
		public int  headers()
		  throws IOException
		{
			EX.assertn(whole);
			EX.assertn(first);

			//~: parse after the first line
			TakeHeaders take = new TakeHeaders();
			bytes.each(first.e, whole.e - first.e, take);

			//?: {positive} set the pairs
			if((Integer) take.result() == 0)
				headers = take.pairs.toArray(new Pair[take.pairs.size()]);

			return (Integer) take.result();
		}

		protected class TakeHeaders implements TakeBytesResult
		{
			//~: parse states
			public final int OO = 0;
			public final int OR = 1;
			public final int HN = 2; //<-- header name
			public final int HV = 3; //<-- header value
			public final int HR = 4; //<-- header value + CR
			public final int HO = 5; //<-- header open
			public final int VO = 6; //<-- value open
			public final int ER = 7; //<-- ending CR
			public final int FS = 8; //<-- final state

			public int o = first.e; //<-- global offset
			public int b = first.e; //<-- begin of the line section
			public int x = 0;       //<-- parse state
			public int n = 0;       //<-- header line index

			public final ArrayList<Pair> pairs =
			  new ArrayList<Pair>(16);

			public boolean take(byte[] buf, int off, int s)
			{
				//c: scan depending on the state
				for(int i = 0;(i < s);i++) switch(buf[off + i] & 0xFF)
				{
					case '\r':
					{
						if(x == OO)
							x = OR;
						else if(x == HV)
							x = HR;
						else if(x == HO)
							x = ER;
						else
							return false;

						break;
					}

					case '\n':
					{
						if(x == OR || x == OO)
						{
							if(n != 0)
								return false;
							x = HO;
						}
						else if(x == HV || x == HR)
						{
							pairs.add(new Pair(b, o + i - (x == HR?1:0)));
							x = HO;
						}
						else if(x == HO || x == ER)
						{
							x = FS;
							return false;
						}
						else
							return false;

						break;
					}

					case ':':
					{
						if(x == HN)
						{
							pairs.add(new Pair(b, o + i));
							x = VO;
							b = o + i + 1;
							break;
						}

						if(x != HV)
							return false;

						//--> no break
					}

					case ' ':
					{
						if(x == VO)
							break;

						//--> no break
					}

					default:
					{
						if(x == HO)
						{
							b = o + i;
							x = HN;
						}

						else if(x == VO)
						{
							b = o + i;
							x = HV;
						}
					}
				}

				o += s; //~: advance the offset
				return true;
			}

			public Object  result()
			{
				//?: {reached final state}
				return (x == FS)?(0):(n);
			}
		}
	}


	/* URL Parameters Parser */

	/**
	 * Parses the URL encoded string invoking the
	 * callback on each parameter name-value pair.
	 */
	public static void params(Reader r, Callback take)
	  throws Exception
	{
		StringBuilder s = new StringBuilder(64);
		final char[]  b = new char[16];
		String        n = null; //<-- cached name
		int           x = 0;    //<-- the state

		while(true)
		{
			//~: fill the buffer
			int sz = r.read(b);
			if(sz <= 0) break;

			for(int i = 0;(i < sz);i++)
			{
				char c = b[i];

				//?: {reading name}
				if(x == 0)
				{
					//?: {=} end name
					if(c == '=')
					{
						n = URLDecoder.decode(s.toString(), "UTF-8");
						s.delete(0, s.length());
						x = 1;
						continue;
					}

					//?: {&} no value
					if(c == '&')
					{
						take.act(URLDecoder.decode(s.toString(), "UTF-8"));
						s.delete(0, s.length());
						x = 0;
						continue;
					}

					//~: append name
					s.append(c);
				}

				//?: {reading value}
				if(x == 1)
				{
					//?: {&} end value
					if(c == '&')
					{
						take.act(n, URLDecoder.decode(s.toString(), "UTF-8"));
						s.delete(0, s.length());
						x = 0;
						continue;
					}

					//~: append value
					s.append(c);
				}
			}
		}

		//?: {finish the last}
		if(x == 1)
			take.act(n, URLDecoder.decode(s.toString(), "UTF-8"));
	}

	@SuppressWarnings("unchecked")
	public static void params(Reader r, final Map<String, Object> ps)
	  throws Exception
	{
		//~: parse
		Requests.params(r, new Callback()
		{
			public void act(Object... args)
			{
				String n = (String) args[0];
				String v = (args.length == 1)?(""):((String) args[1]);
				Object x = ps.get(n);

				if(x == null)
					ps.put(n, v);
				else if(x instanceof String)
				{
					List<String> l = new ArrayList<String>(2);

					l.add((String) x);
					l.add(v);

					ps.put(n, l);
				}
				else
					((List<String>) x).add(v);
			}
		});
	}
}