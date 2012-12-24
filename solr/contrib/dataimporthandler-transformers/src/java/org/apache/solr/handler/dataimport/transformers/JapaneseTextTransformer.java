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
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ibm.icu.text.Transliterator;
import org.apache.solr.handler.dataimport.*;

/**
 * <p>
 * A {@link Transformer} which can take dirty japanese (half width katakana, full width romanji) and
 * cleans it for more human searchable text entry. Also deals with inconsistences in half width katana dashes
 * for vowel elongation and normalizes them to provide a more human based search field.
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
public class JapaneseTextTransformer extends Transformer {
  
  private static Transliterator halfWidthKatakanaToFull = Transliterator.getInstance("[:^Hiragana:];Upper;Hiragana-Katakana;");
  private static Transliterator fullWidthRomanjiToHalfWidth = Transliterator.getInstance("[:^Katakana:];Upper;Fullwidth-Halfwidth;");
  
  public JapaneseTextTransformer() {
    
  }
 

  @Override
  public Object transformRow(Map<String, Object> row, Context context) {

    for (Map<String, String> map : context.getAllEntityFields()) {
      
      String columnName = map.get(DataImporter.COLUMN);
      Object value = row.get(columnName);
      if (value == null) continue;
      if (false == (value instanceof String)) continue;
      
      String txt = value.toString().trim();
      
      // convert from half width forms to full width forms when katakana
      // also converts full width ascii to half width ascii for normalization
      txt = halfWidthKatakanaToFull.transliterate(txt);
      txt = fullWidthRomanjiToHalfWidth.transliterate(txt);
      
      // replace "シャンプｰ" with "シャンプー" (dashes matter, look closely)
      txt = txt.replaceAll("([ァ-ン])(-)", "$1ー");
      txt = txt.replaceAll("(ｰ)", "ー");
      
      row.put(columnName, txt);
    }

    return row;
  }

}
