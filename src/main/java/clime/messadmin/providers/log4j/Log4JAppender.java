/**
 *
 */
package clime.messadmin.providers.log4j;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.MDC;
import org.apache.log4j.helpers.Transform;
import org.apache.log4j.spi.LoggingEvent;

import clime.messadmin.model.Server;
import clime.messadmin.providers.spi.ApplicationLifeCycleProvider;
import clime.messadmin.providers.spi.RequestExceptionProvider;
import clime.messadmin.providers.spi.RequestLifeCycleProvider;

/**
 * Record Log4J logs for future display in MessAdmin.<br />
 * Usage: define (and use!) an Appender in your Log4J configuration:
 * <pre>
	&lt;appender name="messadmin" class="clime.messadmin.providers.log4j.Log4JAppender"&gt;
		&lt;param name="threshold" value="INFO"/&gt;
		&lt;layout class="org.apache.log4j.PatternLayout"&gt;
			&lt;param name="ConversionPattern" value="%d{ISO8601} [%-5p] %c %x - %m%n"/&gt;
		&lt;/layout&gt;
	&lt;/appender&gt;
 * </pre>
 * or
 * <pre>
log4j.appender.MESSADMIN=clime.messadmin.providers.log4j.Log4JAppender
log4j.appender.MESSADMIN.layout=org.apache.log4j.PatternLayout
log4j.appender.MESSADMIN.layout.ConversionPattern=%d{ISO8601} [%-5p] %c %x - %m%n
log4j.appender.MESSADMIN.Threshold=INFO
 * </pre>
 *
 * @author C&eacute;drik LIME
 */
// TODO externalize Log4JAppender.MAX_LOGS_SIZE to configuration?
public class Log4JAppender extends AppenderSkeleton implements Appender, ApplicationLifeCycleProvider, RequestLifeCycleProvider, RequestExceptionProvider {
	private static final String APPENDER_NAME = "MessAdmin_Appender";//$NON-NLS-1$
	private static final String MDC_CONTEXT_KEY = "MessAdmin_Log4J_Appender_Context";//$NON-NLS-1$
	static final String APP_DATA_KEY = "MessAdmin_Log4J";//$NON-NLS-1$
	protected static volatile int MAX_LOGS_SIZE = 100;
	private final String baseHTMLid = this.getClass().getName() + '-';

	/**
	 *
	 */
	public Log4JAppender() {
		super();
		setName(APPENDER_NAME);
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	public void requestInitialized(HttpServletRequest request, HttpServletResponse response, ServletContext servletContext) {
		MDC.put(Log4JAppender.MDC_CONTEXT_KEY, servletContext);
	}

	/** {@inheritDoc} */
	public void requestDestroyed(HttpServletRequest request, HttpServletResponse response, ServletContext servletContext) {
		MDC.remove(Log4JAppender.MDC_CONTEXT_KEY);
	}

	/** {@inheritDoc} */
	public void requestException(Exception e, HttpServletRequest request, HttpServletResponse response, ServletContext servletContext) {
		MDC.remove(Log4JAppender.MDC_CONTEXT_KEY);
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	public int getPriority() {
		return -5000;//whatever
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	public void contextInitialized(ServletContext servletContext) {
		Map userData = Server.getInstance().getApplication(servletContext).getUserData();
		userData.put(APP_DATA_KEY, new LinkedList/*<String>*/());
	}
	/** {@inheritDoc} */
	public void contextDestroyed(ServletContext servletContext) {
		Map userData = Server.getInstance().getApplication(servletContext).getUserData();
		List/*<String>*/ logs = (List/*<String>*/) userData.get(APP_DATA_KEY);
		logs.clear();
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	protected void append(LoggingEvent event) {
		if (MAX_LOGS_SIZE <= 0) {
			return;
		}
		ServletContext context = (ServletContext) MDC.get(MDC_CONTEXT_KEY);
		if (context == null) {
			//TODO log at Server level instead of dropping?
//			LogManager.getRootLogger().debug("MessAdminAppender: Can not record LoggingEvent as ServletContext is undefined");
			return;
		}
		// compute this LogEvent display
		StringBuffer out = new StringBuffer(32);
		String loggingEventStr = Transform.escapeTags(this.layout.format(event));
		if (layout.ignoresThrowable() && event.getThrowableInformation() != null) {
			String htmlId = baseHTMLid + Long.toString(event.hashCode())+Long.toString(Math.round(100000*Math.random()));
			out.append("<span id=\"").append(htmlId).append("\" class=\"infoballoonable\">");
			out.append(loggingEventStr);
			out.append("</span>\n");
			out.append("<div id=\"").append(htmlId).append("-infoballoon\" class=\"infoballoon\">");
			//FIXME put this in collapsible block instead? class=\"collapsible\", id=\"-target\"
			String[] throwableStrRep = event.getThrowableStrRep();
			if (throwableStrRep != null) {
				int len = throwableStrRep.length;
				for (int i = 0; i < len; ++i) {
					out.append(Transform.escapeTags(throwableStrRep[i]));
					out.append("<br />");
				}
			}
			out.append("</div>");
		} else {
			out.append(loggingEventStr);
		}
		// append formatted LogEvent to log-list
		LinkedList/*<String>*/ logs = (LinkedList/*<String>*/) Server.getInstance().getApplication(context).getUserData().get(APP_DATA_KEY);
		//assert logs != null;
		synchronized (logs) {
			while (logs.size() >= MAX_LOGS_SIZE) {
				logs.removeFirst();
			}
			logs.addLast(out.toString());
		}
	}

	/** {@inheritDoc} */
	public boolean requiresLayout() {
		return true;
	}

	/** {@inheritDoc} */
	public void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
	}

}
