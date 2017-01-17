package net.java.lohttp;

/**
 * Represents HTTP response capabilities.
 * Response is not thread-safe!
 *
 * @author anton.baukin@gmail.com
 */
public interface Response
{
	/* HTTP Response */

	/**
	 * Current value of HTTP status code.
	 * By default is 200.
	 */
	public int  getStatus();

	public void setStatus(int status);

	/**
	 * Adds header to the response.
	 * Must be invoked before writing the body.
	 */
	public void addHeader(String name, String value);

	/**
	 * Write the content of the stream to the body.
	 * Multiple calls are allowed, but the status and
	 * the headers must be set before the first call!
	 *
	 * Passing null has no effect, except for the
	 * first call when status line and the headers
	 * are streamed out.
	 */
	public void write(Input body);
}