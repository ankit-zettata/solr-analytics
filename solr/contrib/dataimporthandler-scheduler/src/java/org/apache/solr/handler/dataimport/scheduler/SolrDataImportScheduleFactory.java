package org.apache.solr.handler.dataimport.scheduler;

import java.util.ArrayList;
import java.util.List;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.apache.solr.core.SolrResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.IOUtils;

public class SolrDataImportScheduleFactory {

        private static final Logger logger = LoggerFactory.getLogger(SolrDataImportScheduleFactory.class);

        public static SolrDataImportSchedule load(){
                FileInputStream stream = null;
            
                try{
                        SolrResourceLoader loader = new SolrResourceLoader(null);
                        logger.info("[DataImportScheduler] Solr Instance Directory = " + loader.getInstanceDir());

                        // String configDir = loader.getConfigDir();
                        // configDir = SolrResourceLoader.normalizeDir(configDir);
                        // logger.info("[DataImportScheduler] Solr Configuration Directory = " + loader.getConfigDir());
                    
                        String dataImportPropertiesPath = loader.getInstanceDir() + "schedule.xml";
                        logger.info("[DataImportScheduler] Loading XML File = " + dataImportPropertiesPath);
                    
                        stream = new FileInputStream(dataImportPropertiesPath);
                    
                        String xml = IOUtils.toString(stream);
                        logger.info("[DataImportScheduler] XML File = " + xml);
                    
                        return XmlSerializationHelper.fromXml(SolrDataImportSchedule.class, xml);
                    
                }catch(FileNotFoundException fnfe){
                        logger.error("[DataImportScheduler] Error locating schedule.xml file" + fnfe, fnfe);
                }catch(IOException ioe){
                        logger.error("[DataImportScheduler] Error reading schedule.xml file" + ioe, ioe);
                }catch(Exception e){
                        logger.error("[DataImportScheduler] Error loading schedule.xml " + e, e);
                } finally {
                    if (stream != null) {
                        IOUtils.closeQuietly(stream);
                    }
                }
                
                return null;
        }
}