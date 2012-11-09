package org.apache.solr.handler.dataimport.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import java.util.TimerTask;
import it.sauronsoftware.cron4j.Scheduler;

public class SolrDataImportScheduleRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(SolrDataImportScheduleRunner.class);
    private Scheduler cron;    
    private List<Timer> timers = new ArrayList<Timer>();
    private SolrDataImportSchedule schedule;

    public SolrDataImportScheduleRunner(SolrDataImportSchedule schedule) {
        this.schedule = schedule;
    }    
    
    /**
     * Starts the schedule specified
     */
    public void start() {
        // ensure previous runs are cleared out just in case
        this.stop();
        
        logger.info("[DataImportScheduler] Starting Scheduler...");
        
        this.cron = new Scheduler();
        
		for (SolrDataImportScheduleHTTPTask task : this.schedule.getTasks()) {
            // if cron is specified use the scheduler (cron4j)
            if (task.getCron() != null && task.getCron().isEmpty() == false) {
                logger.info("[DataImportScheduler] Creating CRON Based DataImportHandler");                
                cron.schedule(task.getCron(), new SolrDataImportScheduleHTTPTaskRunner(task));                
            } else if (task.getSeconds() > 0) {
                logger.info("[DataImportScheduler] Creating Timer Based DataImportHandler");                
                // schedule via timer for seconds
                timers.add(createTaskTimer(task));
            } else {
                logger.error("[DataImportScheduler] Could not create DataImportHandler scheduler, does not have cron or second attribute defined!");
            }
		}

        // start cron job
        this.cron.start();        
    }
    
    /**
     * Stops all running jobs and request they be canceled
     */
    public void stop() {
        if (this.cron != null) {
            this.cron.stop();
            this.cron = null;
        }
        
        for (Timer t : this.timers) {
            if (t != null) {
                t.cancel();
            }
        }
        this.timers = new ArrayList<Timer>();
    }
    
    /**
     * Creates a new timer based job capable of executing every X number of seconds
     */
    private static Timer createTaskTimer(SolrDataImportScheduleHTTPTask task) {
        Timer timer = new Timer();
        TimerTask r = new SolrDataImportScheduleHTTPTaskRunner(task);
        int interval = task.getSeconds();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, interval);
        Date startTime = calendar.getTime();
        timer.scheduleAtFixedRate(r, startTime, 1000 * interval);
        return timer;
    }
}