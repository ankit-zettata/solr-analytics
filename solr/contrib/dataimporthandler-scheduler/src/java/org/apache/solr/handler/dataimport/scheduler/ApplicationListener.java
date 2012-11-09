package org.apache.solr.handler.dataimport.scheduler;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplicationListener implements ServletContextListener {

        private static final Logger logger = LoggerFactory.getLogger(ApplicationListener.class);
        private static final String SCHEDULER_RUNNER_ID = "SolrDataImportScheduleRunner";

        @Override
        public void contextDestroyed(ServletContextEvent servletContextEvent) {
                ServletContext servletContext = servletContextEvent.getServletContext();

                // get our Scheduler from the context
                SolrDataImportScheduleRunner runner = (SolrDataImportScheduleRunner)servletContext.getAttribute(SCHEDULER_RUNNER_ID);

                // cancel all active tasks in the Scheduler queue
                if (runner != null) {
                    runner.stop();
                }

                // remove the Scheduler from the context
                servletContext.removeAttribute(SCHEDULER_RUNNER_ID);
        }

        @Override
        public void contextInitialized(ServletContextEvent servletContextEvent) {
                ServletContext servletContext = servletContextEvent.getServletContext();
                try{
                    SolrDataImportSchedule dataImportSchedule = SolrDataImportScheduleFactory.load();
                    // if no schedules have been defined exit out cleanly
                    if (dataImportSchedule == null) return;
                
                    // start the specified schedule
                    SolrDataImportScheduleRunner runner = new SolrDataImportScheduleRunner(dataImportSchedule);
                    runner.start();

                    // save the timer in context
                    servletContext.setAttribute(SCHEDULER_RUNNER_ID, runner);

                } catch (Exception e) {
                    logger.error("[DataImportScheduler] Problem initializing the scheduled task: ", e);
                }
        }

}