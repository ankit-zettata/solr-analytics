package org.apache.solr.handler.component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.FieldCache;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.params.StatsParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.UnInvertedField;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.schema.TrieField;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocSet;
import org.apache.solr.search.SolrIndexSearcher;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

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

public class SimpleStats {

  /** The main set of documents */
  protected DocSet docs;
  /** Searcher to use for all calculations */
  protected SolrIndexSearcher searcher;
  /** The fields to do statistics against */
  protected String[] statFields;
  /** If this request is sharded */
  protected boolean isShard;
  /** If this request should include only basic results */
  protected boolean returnBasicStats;
  /** If this should return stats based on facets */
  protected Map<String,String[]> fieldFacets;

  public SimpleStats(SolrIndexSearcher searcher,
                      DocSet docs, 
                      String[] statFields, 
                      Map<String,String[]> fieldFacets, 
                      boolean isShard, 
                      boolean returnBasicStats) {
    this.searcher = searcher;
    this.statFields = statFields;
    this.docs = docs;
    this.returnBasicStats = returnBasicStats;
    this.isShard = isShard;
    this.fieldFacets= fieldFacets;
  }
  
  /**
   * Returns only the facets for ratios to compute against. NamedList is
   * not an optimal data structure for accessing by name 
   * (see docs: http://lucene.apache.org/solr/api-4_0_0-ALPHA/org/apache/solr/common/util/NamedList.html)
   * "A NamedList provides fast access by element number, but not by name."
   * 
   * @return
   * @throws IOException
   */
  public Map<String, Map<String, Double>> getStatFacets() throws IOException {
    return null;
  }

  public NamedList<Object> getStatsCounts() throws IOException {
    NamedList<Object> res = new SimpleOrderedMap<Object>();
    res.add("stats_fields", getStatsFields());
    return res;
  }

  public NamedList<Object> getStatsFields() throws IOException {
    NamedList<Object> res = new SimpleOrderedMap<Object>();
    if (null != statFields) {
      for (String statFieldName : statFields) {
        String[] statFacetFields = fieldFacets != null && fieldFacets.containsKey(statFieldName) ? fieldFacets.get(statFieldName) : new String[0];// params.getFieldParams(f, StatsParams.STATS_FACET)
        SchemaField statFieldSchema = searcher.getSchema().getField(statFieldName);
        FieldType statFieldType = statFieldSchema.getType();
        NamedList<?> stv;

        // Currently, only UnInvertedField can deal with multi-part trie fields
        String prefix = TrieField.getMainValuePrefix(statFieldType);

        if (statFieldSchema.multiValued() || statFieldType.multiValuedFieldCache() || prefix!=null) {
          //use UnInvertedField for multivalued fields
          UnInvertedField uif = UnInvertedField.getUnInvertedField(statFieldName, searcher);
          stv = uif.getStats(searcher, docs, statFacetFields, this.returnBasicStats).getStatsValues();
        } else {
          stv = getFieldCacheStats(statFieldName, statFacetFields).getStatsValues();
        }
        if (isShard == true || (Long) stv.get("count") > 0) {
          res.add(statFieldName, stv);
        } else {
          res.add(statFieldName, null);
        }
      }
    }
    return res;
  }

  
  // why does this use a top-level field cache?
  public StatsValues getFieldCacheStats(String fieldName, String[] facet ) {
    SchemaField statsFieldSchema = searcher.getSchema().getField(fieldName);
    
    FieldCache.DocTermsIndex statsTermIndex;
    try {
      statsTermIndex = FieldCache.DEFAULT.getTermsIndex(searcher.getAtomicReader(), fieldName);
    } 
    catch (IOException e) {
      throw new RuntimeException( "failed to open field cache for: "+fieldName, e );
    }
    
    StatsValues allstats = StatsValuesFactory.createStatsValues(statsFieldSchema, this.returnBasicStats);
    final int nTerms = statsTermIndex.numOrd();
    // don't worry about faceting if no documents match...
    if ( nTerms <= 0 || docs.size() <= 0 ) return allstats;

    List<FieldFacetStats> facetStats = new ArrayList<FieldFacetStats>();
    FieldCache.DocTermsIndex facetTermsIndex;
    for( String facetField : facet ) {
      SchemaField facetFieldSchema = searcher.getSchema().getField(facetField);
      FieldType facetFieldType = facetFieldSchema.getType();

      if (facetFieldType.isTokenized() || facetFieldType.isMultiValued()) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
          "Stats can only facet on single-valued fields, not: " + facetField
          + "[" + facetFieldType + "]");
        }
      try {
        facetTermsIndex = FieldCache.DEFAULT.getTermsIndex(searcher.getAtomicReader(), facetField);
      }
      catch (IOException e) {
        throw new RuntimeException( "failed to open field cache for: "
          + facetField, e );
      }
      facetStats.add(new FieldFacetStats(facetField, facetTermsIndex, statsFieldSchema, facetFieldSchema, nTerms, this.returnBasicStats));
    }
    
    final BytesRef tempBR = new BytesRef();
    DocIterator iter = docs.iterator();
    while (iter.hasNext()) {
      int docID = iter.nextDoc();
      BytesRef raw = statsTermIndex.lookup(statsTermIndex.getOrd(docID), tempBR);
      if( raw.length > 0 ) {
        allstats.accumulate(raw);
      } else {
        allstats.missing();
      }

      // now update the facets
      for (FieldFacetStats f : facetStats) {
        f.facet(docID, raw);
      }
    }

    for (FieldFacetStats f : facetStats) {
      allstats.addFacet(f.name, f.facetStatsValues);
    }
    return allstats;
  }


}