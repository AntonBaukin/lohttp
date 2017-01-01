package net.java.lohttp;

/* Java */

import java.util.Collection;


/**
 * Exception and assertions handling support.
 *
 * @author anton.baukin@gmail.com.
 */
public class EX
{
	/* Assertions */

	public static void   assertx(boolean x, Object... msg)
	{
		if(x == false)
			throw ass(msg);
	}

	public static <T> T  assertn(T x, Object... msg)
	{
		if(x == null)
			throw ass(msg);
		return x;
	}

	public static String asserts(String s, Object... msg)
	{
		if((s == null) || (s.length() != s.trim().length()))
			throw ass(msg);
		return s;
	}

	@SuppressWarnings("unchecked")
	public static void   asserte(Collection c, Object... msg)
	{
		if((c == null) || c.isEmpty())
			throw ass(msg);
	}


	/* Exceptions */

	public static AssertionError   ass(Object... msg)
	{
		StringBuilder sb = new StringBuilder(32);
		cat(sb, msg);
		String        s  = sb.toString().trim();

		if(s.isEmpty())
			return new AssertionError();
		else
			return new AssertionError(s);
	}

	public static RuntimeException wrap(Throwable cause, Object... msg)
	{
		StringBuilder sb = new StringBuilder(32);
		cat(sb, msg);
		String        s  = sb.toString().trim();

		//?: {has no own message}
		if(s.length() == 0)
			//?: {is runtime itself} do not wrap
			if(cause instanceof RuntimeException)
				return (RuntimeException) cause;
			//~: just take it's message
			else
				s = e2en(cause);

		return new RuntimeException(s, cause);
	}

	/**
	 * Finds the text of the exception. Useful
	 * when original exception is wrapped in
	 * exceptions without text.
	 */
	public static String           e2en(Throwable e)
	{
		String r = null;

		while((r == null) && (e != null))
		{
			r = e.getMessage();
			if((r != null) && (r = r.trim()).isEmpty()) r = null;
			if(r == null) e = e.getCause();
		}

		return r;
	}

	/**
	 * Adds suppressed exception if supported.
	 * Returns the original exception {@code e}.
	 */
	public static Throwable        sup(Throwable e, Throwable x)
	{
		if(e == null) return x;
		if(x == null) return e;

		try //~: add suppressed via the reflection
		{
			e.getClass().getMethod("addSuppressed", Throwable.class).invoke(e, x);
		}
		catch(Throwable ignore)
		{}

		return e;
	}

	/**
	 * Removes the {@link RuntimeException} wrappers
	 * having no own message.
	 */
	public static Throwable xrt(Throwable e)
	{
		while((e != null) && RuntimeException.class.equals(e.getClass()))
			if(e.getCause() == null)
				return e;
			else
			{
				String  a = e.getMessage();
				String  b = e.getCause().getMessage();

				//?: {message is not set}
				boolean x = (a == null);

				//?: {not null -> messages are the same}
				if(!x)  x = EX.eq(a, b);

				//?: {not -> check as toString()}
				if(!x)  x = EX.eq(a, e.getCause().toString());

				//?: {remove wrapper}
				if(x) e = e.getCause();
				else  return e;
			}

		return e;
	}

	@SuppressWarnings("unchecked")
	public static <E> E     search(Throwable e, Class<E> eclass)
	{
		while(e != null)
			if(eclass.isAssignableFrom(e.getClass()))
				return (E)e;
			else
				e = e.getCause();

		return null;
	}


	/* Support */

	public static boolean eq(Object a, Object b)
	{
		return ((a == null) && (b == null)) ||
		  ((a != null) && a.equals(b));
	}

	public static String  cat(Object... objs)
	{
		StringBuilder s = new StringBuilder(64);

		cat(s, objs);
		return s.toString();
	}

	@SuppressWarnings("unchecked")
	private static void   cat(StringBuilder s, Collection objs)
	{
		for(Object o : objs)
			if(o instanceof Collection)
				cat(s, (Collection) o);
			else if(o instanceof Object[])
				cat(s, (Object[]) o);
			else if(o != null)
				s.append(o);
	}

	private static void   cat(StringBuilder s, Object[] objs)
	{
		for(Object o : objs)
			if(o instanceof Collection)
				cat(s, (Collection)o);
			else if(o instanceof Object[])
				cat(s, (Object[]) o);
			else if(o != null)
				s.append(o);
	}
}