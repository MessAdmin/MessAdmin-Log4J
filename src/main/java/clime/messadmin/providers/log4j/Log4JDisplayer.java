/**
 *
 */
package clime.messadmin.providers.log4j;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import clime.messadmin.admin.AdminActionProvider;
import clime.messadmin.admin.BaseAdminActionWithContext;
import clime.messadmin.i18n.I18NSupport;
import clime.messadmin.model.Application;
import clime.messadmin.model.Server;
import clime.messadmin.providers.spi.ApplicationDataProvider;
import clime.messadmin.providers.spi.SizeOfProvider;
import clime.messadmin.utils.BytesFormat;
import clime.messadmin.utils.StringUtils;

/**
 * Displays Log4J logs.
 *
 * @author C&eacute;drik LIME
 */
public class Log4JDisplayer extends BaseAdminActionWithContext implements ApplicationDataProvider, AdminActionProvider {
	private static final String BUNDLE_NAME = Log4JDisplayer.class.getName();
	public static final String ACTION_ID = "Log4JAppender";//$NON-NLS-1$
	protected static final String MAX_SIZE_ID = "maxLogs";//$NON-NLS-1$

	/**
	 *
	 */
	public Log4JDisplayer() {
		super();
	}

	/** {@inheritDoc} */
	public String getActionID() {
		return ACTION_ID;
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	public int getPriority() {
		return 5000;
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	public String getApplicationDataTitle(ServletContext context) {
		NumberFormat numberFormatter = NumberFormat.getNumberInstance(I18NSupport.getAdminLocale());
		NumberFormat bytesFormatter = BytesFormat.getBytesInstance(I18NSupport.getAdminLocale(), true);
		Application application = Server.getInstance().getApplication(context);
		List/*<String>*/ logs = (List/*<String>*/) application.getUserData().get(Log4JAppender.APP_DATA_KEY);
		List/*<String>*/ data = new ArrayList/*<String>*/(logs);
		ClassLoader cl = application.getApplicationInfo().getClassLoader();
		long currentItemSize = SizeOfProvider.Util.getObjectSize(data, cl);
		String result = I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "title",//$NON-NLS-1$
				new Object[] {bytesFormatter.format(currentItemSize), numberFormatter.format(data.size())});
		return result;
	}

	/** {@inheritDoc} */
	public String getXHTMLApplicationData(ServletContext context) {
		Application application = Server.getInstance().getApplication(context);
		ClassLoader cl = application.getApplicationInfo().getClassLoader();
		List/*<String>*/ logs = (List/*<String>*/) application.getUserData().get(Log4JAppender.APP_DATA_KEY);
		logs = new ArrayList/*<String>*/(logs);
		final StringBuffer out = new StringBuffer(256*(logs.size()+1));
		String urlPrefix = new StringBuffer().append('?').append(ACTION_PARAMETER_NAME).append('=').append(getActionID())
			.append('&').append(CONTEXT_KEY).append('=').append(urlEncodeUTF8(Server.getInstance().getApplication(context).getApplicationInfo().getInternalContextPath()))
			.append('&').append(MAX_SIZE_ID).append('=').toString();
		out.append(I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "log.buffer.capacity.set"));//$NON-NLS-1$
		out.append("[ ");
		appendLogCapacity(out, 0, urlPrefix);
		out.append(" | ");
		appendLogCapacity(out, 10, urlPrefix);
		out.append(" | ");
		appendLogCapacity(out, 100, urlPrefix);
		out.append(" | ");
		appendLogCapacity(out, 300, urlPrefix);
		out.append(" | ");
		appendLogCapacity(out, 1000, urlPrefix);
		out.append(" | ");
		appendLogCapacity(out, 5000, urlPrefix);
		out.append(" | ");
		appendLogCapacity(out, 10000, urlPrefix);
		out.append(" | ");
		appendLogCapacity(out, 20000, urlPrefix);
		out.append(" | ");
		appendLogCapacity(out, 50000, urlPrefix);
		out.append(" ]\n");

		out.append("<hr/>");

		Iterator/*<String>*/ iter = logs.iterator();
		out.append("<ul>\n");
		while (iter.hasNext()) {
			String loggingEventStr = (String) iter.next();
			out.append("<li>").append(loggingEventStr).append("</li>\n");
		}
		out.append("</ul>\n");

		return out.toString();
	}
	private void appendLogCapacity(StringBuffer out, int capacity, String urlPrefix) {
		NumberFormat numberFormatter = NumberFormat.getNumberInstance(I18NSupport.getAdminLocale());
		if (capacity == Log4JAppender.MAX_LOGS_SIZE) {
			out.append(numberFormatter.format(capacity));
		} else {
			String url = StringUtils.escapeXml(urlPrefix+capacity);
			out.append(buildActionLink(url, numberFormatter.format(capacity), this));
		}
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	public void serviceWithContext(HttpServletRequest request, HttpServletResponse response, String context) throws ServletException, IOException {
		String maxLogsStr = request.getParameter(MAX_SIZE_ID);
		if (StringUtils.isBlank(maxLogsStr)) {
			// ensure we get a GET
			if (METHOD_POST.equals(request.getMethod())) {
				sendRedirect(request, response);
				return;
			}
			String data = getXHTMLApplicationData(getServletContext(context));
			setNoCache(response);
			PrintWriter out = response.getWriter();
			out.print(data);
			out.flush();
			out.close();
			return;
		}
		int maxLogs;
		try {
			maxLogs = Integer.parseInt(maxLogsStr);
		} catch (NumberFormatException nfe) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad value for parameter " + MAX_SIZE_ID + ": " + nfe.getMessage());
			return;
		}
		Log4JAppender.MAX_LOGS_SIZE = Math.max(0, maxLogs);
		Application application = Server.getInstance().getApplication(context);
		LinkedList/*<String>*/ logs = (LinkedList/*<String>*/) application.getUserData().get(Log4JAppender.APP_DATA_KEY);
		//assert logs != null;
		synchronized (logs) {
			while (logs.size() > Log4JAppender.MAX_LOGS_SIZE) {
				logs.removeFirst();
			}
		}
		sendRedirect(request, response);
	}

}
