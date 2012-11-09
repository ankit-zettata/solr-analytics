package org.apache.solr.handler.dataimport.scheduler;

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
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import java.util.TimerTask;

@XmlRootElement(name = "task")
@XmlAccessorType(XmlAccessType.FIELD)
public class SolrDataImportScheduleHTTPTask {
    
    @XmlAttribute(required = true, name = "url")
    private String url;
    @XmlAttribute(required = false, name = "seconds")
    private Integer seconds;
    @XmlAttribute(required = false, name = "cron")
    private String cron;
    @XmlAttribute(required = false, name = "statusUrl")
    private String statusUrl;
    
    /**
     * Gets the value of the cron property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public Integer getSeconds() {
        return seconds;
    }

    /**
     * Sets the value of the url property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSeconds(Integer value) {
        this.seconds = value;
    }
    
    /**
     * Gets the value of the cron property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCron() {
        return cron;
    }

    /**
     * Sets the value of the url property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCron(String value) {
        this.cron = value;
    }

    /**
     * Gets the value of the url property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the value of the url property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUrl(String value) {
        this.url = value;
    }
    
    public String getStatusUrl() {
        return this.statusUrl;
    }
    
    public void setStatusUrl(String value) {
        this.statusUrl = value;
    }
}