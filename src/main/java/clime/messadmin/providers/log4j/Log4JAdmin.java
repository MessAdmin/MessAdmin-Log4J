/**
 *
 */
package clime.messadmin.providers.log4j;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

import clime.messadmin.admin.AdminActionProvider;
import clime.messadmin.admin.BaseAdminActionWithContext;
import clime.messadmin.i18n.I18NSupport;
import clime.messadmin.model.Server;
import clime.messadmin.providers.spi.ApplicationDataProvider;
import clime.messadmin.utils.Shorts;
import clime.messadmin.utils.StringUtils;

/**
 * Displays and set Log4J Logger levels.
 * Inherited log levels are displayed in a smaller font size.
 *
 * TODO show more Appender information?
 * TODO set Appender threshold
 * TODO AJAX filter on logger name
 * @author C&eacute;drik LIME
 * @since 4.2
 */
public class Log4JAdmin extends BaseAdminActionWithContext implements ApplicationDataProvider, AdminActionProvider {
	private static final String BUNDLE_NAME = Log4JAdmin.class.getName();

	public static final String ACTION_ID = "Log4J";//$NON-NLS-1$
	public static final String LOGGER_ID = "logger";//$NON-NLS-1$
	public static final String LEVEL_ID  = "newLogLevel";//$NON-NLS-1$

	private static final Level[] LOG_LEVELS;// = { Level.ALL, Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR, Level.FATAL, Level.OFF };
	private static final String DELETE_LEVEL = "messadmin_no_level";//$NON-NLS-1$

	static {
		List logLevels = new ArrayList(8);
		logLevels.add(Level.ALL);
		// TRACE is since Log4J 1.2.12
		//logLevels.add(Level.TRACE);
		Level trace = Level.toLevel("TRACE");//$NON-NLS-1$
		if ("TRACE".equalsIgnoreCase(trace.toString())) {//$NON-NLS-1$
			logLevels.add(trace);
		}
		logLevels.add(Level.DEBUG);
		logLevels.add(Level.INFO);
		logLevels.add(Level.WARN);
		logLevels.add(Level.ERROR);
		logLevels.add(Level.FATAL);
		logLevels.add(Level.OFF);
		LOG_LEVELS = (Level[]) logLevels.toArray(new Level[logLevels.size()]);
	}

	public Log4JAdmin() {
		super();
	}

	/** {@inheritDoc} */
	public String getActionID() {
		return ACTION_ID;
	}

	/** {@inheritDoc} */
	public void serviceWithContext(HttpServletRequest request, HttpServletResponse response, String context) throws ServletException, IOException {
		String targetLogger   = request.getParameter(LOGGER_ID);
		String targetLogLevelStr = request.getParameter(LEVEL_ID);
		if (StringUtils.isBlank(targetLogger) || StringUtils.isBlank(targetLogLevelStr)) {
			// ensure we get a GET
			if (METHOD_POST.equals(request.getMethod())) {
				sendRedirect(request, response);
				return;
			}
			// display a listing of all Loggers
			String data = getXHTMLApplicationData(getServletContext(context));
			setNoCache(response);
			PrintWriter out = response.getWriter();
			out.print(data);
			out.flush();
			out.close();
			return;
		} // else
		// get Logger to change Level, and change its Level
		Logger logger = (Logger) getLoggers().get(targetLogger);
		if (logger != null) {
			Level targetLogLevel = Level.toLevel(targetLogLevelStr, null);
			logger.setLevel(targetLogLevel);
		}
		sendRedirect(request, response);
	}

	/**
	 * @return all known Loggers, sorted by name
	 */
	protected SortedMap/*<String, Logger>*/ getLoggers() {
		SortedMap/*<String, Logger>*/ loggersMap = new TreeMap(String.CASE_INSENSITIVE_ORDER);
		Enumeration loggers = LogManager.getCurrentLoggers();
		while (loggers.hasMoreElements()) {
			// eventually filter elements to display here
			Logger logger = (Logger) loggers.nextElement();
			loggersMap.put(logger.getName(), logger);
		}
		Logger rootLogger = LogManager.getRootLogger();
		// Root logger is named "root", but you can only get it via LogManager.getRootLogger(), not via LogManager.getLogger("root").
		// Thus don't override a user "root" logger with the root logger!
		if (! loggersMap.containsKey(rootLogger.getName())) {
			loggersMap.put(rootLogger.getName(), rootLogger);
		}
		return loggersMap;
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	public int getPriority() {
		return 4999;
	}

	/** {@inheritDoc} */
	public String getApplicationDataTitle(ServletContext context) {
		final ClassLoader cl = Server.getInstance().getApplication(context).getApplicationInfo().getClassLoader();
		return I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "title");//$NON-NLS-1$
	}

	/** {@inheritDoc} */
	public String getXHTMLApplicationData(ServletContext context) {
		StringBuffer xhtml = new StringBuffer(131072);
		xhtml.append("<table border=\"0\" style=\"font-size: smaller;\">\n");// big table, lots of screen space...
		xhtml.append("	<tr><th>Logger</th><th>Level</th></tr>\n");
		Iterator iter = getLoggers().values().iterator();
		while (iter.hasNext()) {
			Logger logger = (Logger) iter.next();
			String urlPrefix = new StringBuffer().append('?').append(ACTION_PARAMETER_NAME).append('=').append(getActionID())
			.append('&').append(CONTEXT_KEY).append('=').append(urlEncodeUTF8(Server.getInstance().getApplication(context).getApplicationInfo().getInternalContextPath()))
			.append('&').append(LOGGER_ID).append('=').append(urlEncodeUTF8(logger.getName()))
			.append('&').append(LEVEL_ID).append('=').toString();
			appendLogger(xhtml, logger, urlPrefix);
		}
		xhtml.append("</table>\n");
		return xhtml.toString();
	}

	protected void appendLogger(StringBuffer xhtml, Logger logger, String urlPrefix) {
		xhtml.append("	<tr>");
		String tdTitle = "";
		//logger.getAdditivity();
		Enumeration appenders = logger.getAllAppenders();
		short nAppenders = 0;
		String appendersStr = "";
		while (appenders.hasMoreElements()) {
			Appender appender = (Appender) appenders.nextElement();
			Priority threshold = null;
			if (appender instanceof AppenderSkeleton) {
				threshold = ((AppenderSkeleton)appender).getThreshold();
			}
			appendersStr += ", <code title=\""+appender.getClass().getName()+"\">" + StringUtils.escapeXml(appender.getName());
			if (threshold != null) {
				appendersStr += " [" + threshold.toString() + ']';
			}
			appendersStr += "</code>";
			++nAppenders;
		}
		if (logger.getParent() != null) {
			tdTitle += I18NSupport.getLocalizedMessage(BUNDLE_NAME, "logger.parent", new Object[] {StringUtils.escapeXml(logger.getParent().getName())});//$NON-NLS-1$
		}
		//logger.getResourceBundle();
		xhtml.append("<td");
		if (StringUtils.isNotBlank(tdTitle)) {
			xhtml.append(" title=\"").append(tdTitle).append('"');
		}
		xhtml.append('>')
			.append("<a name=\"").append(StringUtils.escapeXml(logger.getName())).append("\"></a>")
			.append(StringUtils.escapeXml(logger.getName()));
		if (logger.getParent() != null && logger.getParent() != LogManager.getRootLogger()) {
			// append link to parent logger
			xhtml.append("<a style=\"font-size: smaller; font-style: normal; font-weight: lighter; margin-left: 0.25em; position: relative; top: -1ex;\" title=\"")
				.append(I18NSupport.getLocalizedMessage(BUNDLE_NAME, "logger.parent.link.title", new Object[] {StringUtils.escapeXml(logger.getParent().getName())}))
				.append("\" href=\"#").append(StringUtils.escapeXml(logger.getParent().getName())).append("\">&uarr;</a>");
		}
		// append appenders names
		if (nAppenders > 0) {
			appendersStr = appendersStr.substring(2); // remove leading ", "
			xhtml.append(I18NSupport.getLocalizedMessage(BUNDLE_NAME, "logger.appenders",//$NON-NLS-1$
					new Object[] {Shorts.valueOf(nAppenders), appendersStr})
			);
		}
		xhtml.append("</td>");

		xhtml.append("<td style=\"white-space: nowrap;");
		if (logger.getLevel() == null) {
			xhtml.append(" font-size: smaller;");
		}
		xhtml.append("\">");
		for (int i = 0; i < LOG_LEVELS.length; ++i) {
			Level displayLevel = LOG_LEVELS[i];
			if (displayLevel.equals(logger.getLevel()) || displayLevel.equals(logger.getEffectiveLevel())) {
				xhtml.append('[').append(displayLevel).append(']');
			} else {
				// AJAX call
				String url = StringUtils.escapeXml(urlPrefix+displayLevel);
				xhtml.append(buildActionLink(url, displayLevel.toString(), this));
			}
			if (i < LOG_LEVELS.length) {
				xhtml.append("&nbsp;");
			}
		}
		if (logger.getLevel() != null) {
			// AJAX call for remove level
			xhtml.append("&nbsp;");
			String url = StringUtils.escapeXml(urlPrefix+DELETE_LEVEL);
			xhtml.append('[')
				.append(buildActionLink(url, I18NSupport.getLocalizedMessage(BUNDLE_NAME, "action.delete"), this))////$NON-NLS-1$
				.append(']');
		}
		xhtml.append("</td></tr>\n");
	}
}
