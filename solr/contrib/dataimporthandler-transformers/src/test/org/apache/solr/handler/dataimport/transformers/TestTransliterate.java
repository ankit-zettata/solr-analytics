package org.apache.solr.handler.dataimport.transformers;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;

import com.ibm.icu.text.Transliterator;

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

public class TestTransliterate {
  
  private Map<String, Transliterator> cache = new HashMap<String,Transliterator>();
  
  /**
   * Grabs a cached instance of the transliterator.
   * 
   * @param arg The text expression to run through the transliterator.
   * @return The transliterator for the specified expression.
   */
  private Transliterator getInstance(String arg) {
    Transliterator transliterator = cache.get(arg);
    if (null != transliterator) return transliterator;
    
    transliterator = Transliterator.getInstance(arg);
    cache.put(arg, transliterator);
    return transliterator;
  }
  
  @Test
  public void testTrans() {
    String txt = "東京,とうきょう,コーヒー,ｺｰﾋｰ,Cat,ＣＡＴ,［＝＋＄＃，］";
    
    String[] chain = "[:^Hiragana:];Upper;Hiragana-Katakana;|[:^Katakana:];Upper;Fullwidth-Halfwidth;".split("\\|");
    for (String expression : chain) {
      Transliterator transliterator = getInstance(expression);
      txt = transliterator.transliterate(txt);
    }
    
    Assert.assertEquals("東京,とうきょう,コｰヒｰ,コｰヒｰ,CAT,CAT,[=+$#,]", txt);
  }
  
}
