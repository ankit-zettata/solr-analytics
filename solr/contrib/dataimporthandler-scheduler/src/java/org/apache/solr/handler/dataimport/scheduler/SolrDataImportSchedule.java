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

@XmlRootElement(name = "schedule")
@XmlAccessorType(XmlAccessType.FIELD)
public class SolrDataImportSchedule {
    
	@XmlElementWrapper(name="tasks", required=true)
	@XmlElement(name = "task", required = true)
	private List<SolrDataImportScheduleHTTPTask> tasks;
    
    /**
     * Gets the tasks to execute.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public List<SolrDataImportScheduleHTTPTask> getTasks() {
        return tasks;
    }

    /**
     * Sets the tasks to execute.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTasks(List<SolrDataImportScheduleHTTPTask> values) {
        this.tasks = values;
    }
}