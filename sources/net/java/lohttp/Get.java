package net.java.lohttp;

/**
 * HTTP GET request.
 *
 * @author anton.baukin@gmail.com.
 */
public interface Get extends Request
{
	/* GET Request */

	/**
	 * Always returns GET string.
	 */
	String getMethod();
}