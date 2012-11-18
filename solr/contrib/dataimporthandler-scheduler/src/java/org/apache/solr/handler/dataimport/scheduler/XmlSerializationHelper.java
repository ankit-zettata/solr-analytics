package org.apache.solr.handler.dataimport.scheduler;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.IOUtils;

/**
 * Provides the XML Object serialization.
 */
public class XmlSerializationHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(SolrDataImportScheduleFactory.class);

	/**
	 * Takes the specified object and converts it to XML.
	 * 
	 * @param <T>
	 *            The type of object to serialize.
	 * @param obj
	 *            The object to serialize.
	 * @return The xml representing the specified object.
	 * @throws Exception
	 *             the exception
	 */
	public static <T> String toXml(T obj) {
		StringWriter stringWriter = new StringWriter();
		try {
			JAXBContext context = JAXBContext.newInstance(obj.getClass());
			Marshaller marshaller = context.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

			marshaller.marshal(obj, stringWriter);
			return stringWriter.toString();
		} catch (Exception ex) {
            logger.error("[DataImportScheduler] Error writing object to XML " + ex, ex);
			throw new RuntimeException(ex.getMessage(), ex);
		} finally {
            IOUtils.closeQuietly(stringWriter);
        }
	}

	@SuppressWarnings("rawtypes")
	private static volatile Map<Class, Unmarshaller> serializerCache = new HashMap<Class, Unmarshaller>();
	private static Object lockObject = new Object();

	/**
	 * Takes the specified XML and converts it into the specified object.
	 * 
	 * @param <T>
	 *            The type of object to convert to.
	 * @param clazz
	 *            The type of object to convert to.
	 * @param xml
	 *            The xml to use to deserialize into an object.
	 * @return The object that was deserialized.
	 * @throws Exception
	 *             the exception if an error occured.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T fromXml(Class<T> clazz, String xml) {
		StringReader sr = new StringReader(xml);
		try {
			if (false == serializerCache.containsKey(clazz)) {
				synchronized (lockObject) {
					if (false == serializerCache.containsKey(clazz)) {
						JAXBContext context = JAXBContext.newInstance(clazz);
						serializerCache.put(clazz, context.createUnmarshaller());
					}
				}
			}

			T obj = ((T) serializerCache.get(clazz).unmarshal(sr));
			return obj;
		} catch (Exception ex) {
            logger.error("[DataImportScheduler] Error reading XML and converting to object " + ex, ex);
			throw new RuntimeException(ex.getMessage(), ex);
		} finally {
			if (null != sr) {
				IOUtils.closeQuietly(sr);
			}
		}
	}
}
