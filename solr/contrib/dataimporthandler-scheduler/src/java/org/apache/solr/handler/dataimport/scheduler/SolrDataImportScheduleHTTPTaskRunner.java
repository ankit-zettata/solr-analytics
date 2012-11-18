package org.apache.solr.handler.dataimport.scheduler;

import java.util.ArrayList;
import java.util.List;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.io.BufferedReader;

public class SolrDataImportScheduleHTTPTaskRunner extends TimerTask {

        private SolrDataImportScheduleHTTPTask task;

        private static final Logger logger = LoggerFactory.getLogger(SolrDataImportScheduleHTTPTaskRunner.class);

        public SolrDataImportScheduleHTTPTaskRunner(SolrDataImportScheduleHTTPTask task) {
            this.task = task;
            logger.info(String.format("[DataImportScheduler] Scheduled a job for '%s'", task.getUrl()));
        }

        public void run() {
                try {
                        sendHttpPost(task.getUrl(), task.getStatusUrl());
                } catch(Exception e) {
                        logger.error("[DataImportScheduler] Failed to prepare for sendHttpPost", e);
                }
        }
        
        private boolean checkIsBusy(String statusUrl) {
            
            // no status url equates to no checking...
            if (null == statusUrl) return false;
            
            logger.info("[DataImportScheduler] Checking if [" + statusUrl + "] is currently busy");
            try {
                URL url = new URL(statusUrl);
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.setRequestMethod("GET");
                conn.setDoOutput(true);
                conn.connect();
                
                // if 200 is NOT returned we assume it is busy (500,404,301,etc)
                if(conn.getResponseCode() != 200){
                    logger.error("[DataImportScheduler] Could not get response from [" + statusUrl + "] status code was not 200");
                    return true;
                }
                
                InputStreamReader sr = null;
                BufferedReader in = null;
                
                try {
                    sr = new InputStreamReader(conn.getInputStream());
                    in = new BufferedReader(sr);
                    
                    StringBuilder sb = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        sb.append(inputLine);
                    }
                    String xml = sb.toString();
                    return xml.matches(".*idle.*");
                } finally {
                    try { if (sr !=null) sr.close();  } catch (Exception e) { /* ignore */ }
                    try { if (in != null) in.close();  } catch (Exception e) { /* ignore */ }
                }

            } catch (Exception ex) {
                logger.error("[DataImportScheduler] SEVERE: Could not check status of URL [" + statusUrl + "]", ex);
                return true;
            }
        }

        private void sendHttpPost(String importUrl, String statusUrl){
                logger.info("[DataImportScheduler] Index request started for [" + importUrl + "]");
            
            // do not engage if the url resource is busy
            // small chance of race condition here due to the fact
            // we could check, and by the time we get done checking someone
            // else could of triggered... this is few and far between that
            // is likely already handled internall in SOLR
            if (checkIsBusy(statusUrl)) {
                return;
            }
            
                try{

                    URL url = new URL(importUrl);
                    HttpURLConnection conn = (HttpURLConnection)url.openConnection();

                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("type", "submit");
                    conn.setDoOutput(true);

                    // Send HTTP POST
                    conn.connect();

                    if(conn.getResponseCode() != 200){
                        logger.error("[DataImportScheduler] Invalid response returned from URL [" + importUrl + "]");
                    } else {
                        logger.info("[DataImportScheduler] Index request completed successfully for [" + importUrl + "]");
                    }
                    
                    conn.disconnect();
                }catch(MalformedURLException mue){
                        logger.error("[DataImportScheduler] Failed to assemble URL for HTTP POST", mue);
                }catch(IOException ioe){
                        logger.error("[DataImportScheduler] Failed to connect to the specified URL while trying to send HTTP POST", ioe);
                }catch(Exception e){
                        logger.error("[DataImportScheduler] Failed to send HTTP POST", e);
                }
        }
}