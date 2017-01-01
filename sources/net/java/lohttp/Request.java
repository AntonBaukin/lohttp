package net.java.lohttp;

/**
 * Represents HTTP request with minimum
 * subset of available things.
 *
 * Request is not thread-safe!
 *
 * @author anton.baukin@gmail.com.
 */
public interface Request
{
	/* HTTP Request */

	String   getMethod();

	/**
	 * Returns the query path before the query.
	 */
	String   getPath();

	/**
	 * Returns parameter from the query path.
	 * Multiple values for the same name are
	 * not supported in this call, only the
	 * first value is always returned.
	 */
	String   getParam(String name);

	/**
	 * Returns the names of the parameters.
	 */
	String[] getParams();

	/**
	 * Takes all values of the parameter.
	 * Returns the number of values added.
	 */
	int      takeParams(String name, Take take);

	/**
	 * Returns the header named regardless of
	 * the letters case of the name.
	 */
	String   getHeader(String name);

	/**
	 * Returns the names of the headers.
	 * All values are in lower case.
	 */
	String[] getHeaders();

	/**
	 * Takes all values of the header.
	 * Returns the number of values added.
	 */
	int      takeHeaders(String name, Take take);
}