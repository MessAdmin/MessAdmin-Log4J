This MessAdmin plugin gives you a Log4J Appender that will display log events in the MessAdmin administration webapp.

To install, simply drop MessAdmin-Log4J.jar next to MessAdmin.jar or in WEB-INF/lib/,
and use the clime.messadmin.providers.log4j.Log4JAppender appender in your Log4J configuration:

<appender name="messadmin" class="clime.messadmin.providers.log4j.Log4JAppender">
	<param name="threshold" value="INFO"/>
	<layout class="org.apache.log4j.PatternLayout">
		<param name="ConversionPattern" value="%d{ISO8601} [%-5p] %c %x - %m%n"/>
	</layout>
</appender>
<!-- example logger -->
<logger name="com.myapp.something">
	<level value="warn"/>
	<appender-ref ref="messadmin"/>
</logger>

or

log4j.appender.MESSADMIN=clime.messadmin.providers.log4j.Log4JAppender
log4j.appender.MESSADMIN.layout=org.apache.log4j.PatternLayout
log4j.appender.MESSADMIN.layout.ConversionPattern=%d{ISO8601} [%-5p] %c %x - %m%n
log4j.appender.MESSADMIN.Threshold=INFO
# Example logger
log4j.logger.com.myapp.something=MESSADMIN, WARN
