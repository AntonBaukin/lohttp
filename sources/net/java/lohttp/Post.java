package net.java.lohttp;

/* Java */

import java.io.InputStream;


/**
 * HTTP POST or related requests.
 *
 * @author anton.baukin@gmail.com.
 */
public interface Post extends Request
{
	/* POST Request */

	/**
	 * Returns POST, but this is not fixed.
	 * Subclasses may return any string having
	 * semantics of POST, such as PUT, but
	 * GET is not allowed for this class as
	 * get-requests have no body.
	 */
	String      getMethod();

	/**
	 * In general, decodes the request input
	 * into the parameters. Does nothing if
	 * the request type is not recognized.
	 *
	 * In practice, if request type if URL
	 * encoded web form, does decode adding
	 * the parameters.
	 *
	 * Returns false if nothing happened.
	 */
	boolean     decode();

	/**
	 * Input stream available if no true decode.
	 * Returns the body of the request.
	 */
	InputStream input();
}