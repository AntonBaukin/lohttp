package net.java.lohttp;

/**
 * HTTP server controls.
 *
 * The callbacks are always invoked in the critical
 * section of the server controls. They are optional.
 *
 * @author anton.baukin@gmail.com
 */
public interface Server
{
	/* HTTP Server */

	/**
	 * Invoked to start the server with the given setup.
	 * Binds the accepting TCP socket here.
	 */
	void start(Setup setup, Callback done);

	/**
	 * Stops accepting any incoming requests
	 * waiting for execution of the current
	 * and invoking the callback at that moment.
	 *
	 * The callback is also invoked when the server
	 * was resumed (with false argument) while waiting.
	 */
	void hangup(Callback done);

	/**
	 * Resumes the server hanged up.
	 */
	void resume();

	/**
	 * Closes the server by unbinding the socket.
	 * The server may be started again.
	 *
	 * This operation is synchronous. All active workers
	 * are left as-is: terminate the execution pool of
	 * the setup to finish them.
	 *
	 * The best way to invoke this method is within
	 * the hangup callback as the callback is invoked
	 * in the same critical section.
	 */
	void close();
}