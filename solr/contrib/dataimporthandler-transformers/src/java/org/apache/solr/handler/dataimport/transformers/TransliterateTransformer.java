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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ibm.icu.text.Transliterator;
import org.apache.solr.handler.dataimport.*;

/**
 * <p>
 * A {@link Transformer} which can transliterate a column into a normalized form using the ICU4J
 * library.
 * </p>
 * <p/>
 * <p>
 * For example:<br />
 * &lt;field column="name" transliterate="Hiragana-Katakana" /&gt; will produce the
 * transliterated value of that column as it maps directly to:
 * <br/>
 * com.ibm.icu.text.Transliterator transliterator = com.ibm.icu.text.Transliterator.getInstance("Hiragana-Katakana");
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
public class TransliterateTransformer extends Transformer {

  private static final Logger LOG = LoggerFactory.getLogger(TransliterateTransformer.class);

  @Override
  @SuppressWarnings("unchecked")
  public Object transformRow(Map<String, Object> row, Context context) {

    for (Map<String, String> map : context.getAllEntityFields()) {
      String expr = map.get(TEMPLATE);
      if (expr == null)
        continue;
      
      Transliterator transliterator = Transliterator.getInstance(expr);

      String columnName = map.get(DataImporter.COLUMN);
      Object value = row.get(columnName);
      
      if (value != null) {
        row.put(columnName, transliterator.transliterate(value.toString().trim()));
      }

    }


    return row;
  }

  public static final String TEMPLATE = "transliterate";
}
