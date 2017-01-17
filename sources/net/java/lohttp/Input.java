package net.java.lohttp;

/**
 * Functional input stream.
 *
 * @author anton.baukin@gmail.com
 */
public interface Input
{
	/* Input Stream */

	public int read(byte[] buf, int off, int len);
}