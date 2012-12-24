/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.handler.dataimport.transformers;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.ibm.icu.text.Transliterator;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.solr.handler.dataimport.*;

/**
 * <p>
 * A {@link Transformer} which will allow a data import handler to perform
 * a MD5 checksum of all data contained in the row so that it can be used
 * to generate a unique identifier for the data contained. This checksum
 * when appended to the primary key of the source document will then
 * be able to natively inject slowly changing data semantics.
 * </p>
 * <p>
 * Note: The slowly changing dimension information is mainly managed
 * by the checksum and does not take into account any date information. It is
 * up to the implementor to always include a "freshness" date or "effective" date
 * to allow a user to associated a query with a time slice. Merely having the MD5
 * checksum only allows two documents with the same key to exist in the index because
 * the key is actually moved to an MD5 checksum.
 * </p>
 * <p>
 * For example:<br />
 * &lt;field column="md5" checksum="md5" /&gt; will produce the checksum of the row in md5 format
 * and add that checksum to the document with the specified field.
 * </p>
 * <p/>
 * <p>
 * Refer to <a
 * href="http://wiki.apache.org/solr/DataImportHandler">http://wiki.apache.org/solr/DataImportHandler</a>
 * for more details.
 * </p>
 * <p/>
 * <b>This API is experimental and may change in the future.</b>
 *
 *
 * @since solr 4.0
 */
public class ChecksumTransformer extends Transformer {

  @Override
  public Object transformRow(Map<String, Object> row, Context context) {

    for (Map<String, String> map : context.getAllEntityFields()) {
      String expr = map.get(TEMPLATE);
      if (expr == null)
        continue;
      
      
      // to generate our md5 hash we take all keys, and sort them in order
      List<String> sortedKeys = Lists.newArrayList(map.keySet());
      Collections.sort(sortedKeys);
      
      // we then take the string values of all keys and append them together
      // creating our unique rows
      StringBuilder sb = new StringBuilder();
      Object o = null;
      for (String key : sortedKeys) {
        o = map.get(key);
        if (o != null)
          sb.append(o);
      }
      
      // now with our canonical row of data we md5 checksum it to generate
      // our unique key
      String md5Hex = DigestUtils.md5Hex(sb.toString());
      
      String columnName = map.get(DataImporter.COLUMN);
      row.put(columnName, md5Hex);
    }

    return row;
  }

  public static final String TEMPLATE = "checksum";
}
