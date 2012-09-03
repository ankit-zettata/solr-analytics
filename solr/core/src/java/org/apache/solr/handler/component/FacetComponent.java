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

package org.apache.solr.handler.component;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.util.OpenBitSet;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.RequiredSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.handler.component.PivotFacetHelper.PivotLimitInfo;
import org.apache.solr.request.FacetPercentiles;
import org.apache.solr.request.SimpleFacets;
import org.apache.solr.schema.FieldType;
import org.apache.solr.search.QueryParsing;
import org.apache.solr.util.NamedListHelper;
import org.apache.solr.util.PivotListEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * TODO!
 *
 *
 * @since solr 1.3
 */
public class FacetComponent extends SearchComponent
{
  public static Logger log = LoggerFactory.getLogger(FacetComponent.class);

  public static final String COMPONENT_NAME = "facet";

  static final String PIVOT_KEY = "facet_pivot";
  
  PivotFacetHelper pivotHelper;

  protected NamedListHelper namedListHelper = NamedListHelper.INSTANCE;
  
  @Override
  public void init( NamedList args )
  {
    pivotHelper = new PivotFacetHelper(); // Maybe this would configurable?
  }
  
  @Override
  public void prepare(ResponseBuilder rb) throws IOException
  {
    if (rb.req.getParams().getBool(FacetParams.FACET, false)) {
      rb.setNeedDocSet(true);
      rb.doFacets = true;
      rb.doPercentiles = rb.req.getParams().getBool(FacetParams.PERCENTILE, false);      
    }
  }
  
  /**
   * Actually run the query
   * @param rb
   */
  @Override
  public void process(ResponseBuilder rb) throws IOException
  {
    if (rb.doFacets) {
      SolrParams params = rb.req.getParams();
      SimpleFacets f = new SimpleFacets(rb.req,
              rb.getResults().docSet,
              params,
              rb );
      
      NamedList<Object> counts = f.getFacetCounts();
      String[] pivots = params.getParams(FacetParams.FACET_PIVOT);
      if( pivots != null && pivots.length > 0 ) {
        NamedList v = pivotHelper.process(rb, params, pivots);
        if( v != null ) {
          counts.add(PIVOT_KEY, v);
        }
      }
      
      // TODO ???? add this directly to the response, or to the builder?
      rb.rsp.add("facet_counts", counts);
    }
  }
  
  private static final String commandPrefix = "{!" + CommonParams.TERMS + "=$";
  
  @Override
  public int distributedProcess(ResponseBuilder rb) throws IOException {
    if (!rb.doFacets) {
      return ResponseBuilder.STAGE_DONE;
    }
    
    if (rb.stage == ResponseBuilder.STAGE_GET_FIELDS) {
      // overlap facet refinement requests (those shards that we need a count for
      // particular facet values from), where possible, with
      // the requests to get fields (because we know that is the
      // only other required phase).
      // We do this in distributedProcess so we can look at all of the
      // requests in the outgoing queue at once.



      for (int shardNum = 0; shardNum < rb.shards.length; shardNum++) {
        List<String> refinements = null;
        
        for (DistribFieldFacet dff : rb._facetInfo.facets.values()) {
          if (!dff.needRefinements) continue;
          List<String> refList = dff._toRefine[shardNum];
          if (refList == null || refList.size() == 0) continue;
          
          String key = dff.getKey();  // reuse the same key that was used for the main facet
          String termsKey = key + "__terms";
          String termsVal = StrUtils.join(refList, ',');
          
          String facetCommand;
          // add terms into the original facet.field command
          // do it via parameter reference to avoid another layer of encoding.
          
          String termsKeyEncoded = QueryParsing.encodeLocalParamVal(termsKey);
          if (dff.localParams != null) {
            facetCommand = commandPrefix+termsKeyEncoded + " " + dff.facetStr.substring(2);
          } else {
            facetCommand = commandPrefix + termsKeyEncoded + '}' + dff.field;
          }
          
          if (refinements == null) {
            refinements = new ArrayList<String>();
          }
          
          refinements.add(facetCommand);
          refinements.add(termsKey);
          refinements.add(termsVal);
        }
        
        if (refinements == null) continue;
        

        String shard = rb.shards[shardNum];
        ShardRequest refine = null;
        boolean newRequest = false;
        
        // try to find a request that is already going out to that shard.
        // If nshards becomes to great, we way want to move to hashing for better
        // scalability.
        for (ShardRequest sreq : rb.outgoing) {
          if ((sreq.purpose & ShardRequest.PURPOSE_GET_FIELDS)!=0
                  && sreq.shards != null
                  && sreq.shards.length==1
                  && sreq.shards[0].equals(shard))
          {
            refine = sreq;
            break;
          }
        }
        
        if (refine == null) {
          // we didn't find any other suitable requests going out to that shard, so
          // create one ourselves.
          newRequest = true;
          refine = new ShardRequest();
          refine.shards = new String[] {rb.shards[shardNum]};
          refine.params = new ModifiableSolrParams(rb.req.getParams());
          // don't request any documents
          refine.params.remove(CommonParams.START);
          refine.params.set(CommonParams.ROWS, "0");
        }
        
        refine.purpose |= ShardRequest.PURPOSE_REFINE_FACETS;
        refine.params.set(FacetParams.FACET, "true");
        refine.params.remove(FacetParams.FACET_FIELD);
        refine.params.remove(FacetParams.FACET_QUERY);
        
        for (int i = 0; i < refinements.size();) {
          String facetCommand = refinements.get(i++);
          String termsKey = refinements.get(i++);
          String termsVal = refinements.get(i++);
          
          refine.params.add(FacetParams.FACET_FIELD, facetCommand);
          refine.params.set(termsKey, termsVal);
        }
        
        if (newRequest) {
          rb.addRequest(this, refine);
        }
      }      
    }
    
    return ResponseBuilder.STAGE_DONE;
  }
  
  @Override
  public void modifyRequest(ResponseBuilder rb, SearchComponent who, ShardRequest sreq) {
    if (!rb.doFacets) return;
    
    if ((sreq.purpose & ShardRequest.PURPOSE_GET_TOP_IDS) != 0) {
      sreq.purpose |= ShardRequest.PURPOSE_GET_FACETS;
      
      if(rb.isDistrib && rb.doPercentiles) {
  		sreq.params.set(FacetParams.PERCENTILE_DISTRIBUTED, "true");  		
      }
      
      FacetInfo fi = rb._facetInfo;
      if (fi == null) {
        rb._facetInfo = fi = new FacetInfo();
        fi.parse(rb.req.getParams(), rb);
        // should already be true...
        // sreq.params.set(FacetParams.FACET, "true");
      }
      
      sreq.params.remove(FacetParams.FACET_MINCOUNT);
      sreq.params.remove(FacetParams.FACET_OFFSET);
      sreq.params.remove(FacetParams.FACET_LIMIT);
      
      for (DistribFieldFacet dff : fi.facets.values()) {
        String paramStart = "f." + dff.field + '.';
        sreq.params.remove(paramStart + FacetParams.FACET_MINCOUNT);
        sreq.params.remove(paramStart + FacetParams.FACET_OFFSET);
        
        dff.initialLimit = dff.limit <= 0 ? dff.limit : dff.offset + dff.limit;
        
        if (dff.sort.equals(FacetParams.FACET_SORT_COUNT)) {
          if (dff.limit > 0) {
            // set the initial limit higher to increase accuracy
            dff.initialLimit = (int) (dff.initialLimit * 1.5) + 10;
              dff.initialMincount = 0;      // TODO: we could change this to 1, but would then need more refinement for small facet result sets?
            } else {
              // if limit==-1, then no need to artificially lower mincount to 0 if it's 1
            dff.initialMincount = Math.min(dff.minCount, 1);
          }
          } else {
            // we're sorting by index order.
            // if minCount==0, we should always be able to get accurate results w/o over-requesting or refining
            // if minCount==1, we should be able to get accurate results w/o over-requesting, but we'll need to refine
            // if minCount==n (>1), we can set the initialMincount to minCount/nShards, rounded up.
            // For example, we know that if minCount=10 and we have 3 shards, then at least one shard must have a count of 4 for the term
            // For the minCount>1 case, we can generate too short of a list (miss terms at the end of the list) unless limit==-1
            // For example: each shard could produce a list of top 10, but some of those could fail to make it into the combined list (i.e.
            //   we needed to go beyond the top 10 to generate the top 10 combined).  Overrequesting can help a little here, but not as
            //   much as when sorting by count.
            if (dff.minCount <= 1) {
              dff.initialMincount = dff.minCount;
            } else {
              dff.initialMincount = (int)Math.ceil((double)dff.minCount / rb.slices.length);
              // dff.initialMincount = 1;
            }
          }

          if (dff.initialMincount != 0) {
            sreq.params.set(paramStart + FacetParams.FACET_MINCOUNT, dff.initialMincount);
          }

          // Currently this is for testing only and allows overriding of the
          // facet.limit set to the shards
          dff.initialLimit = rb.req.getParams().getInt("facet.shard.limit", dff.initialLimit);

        sreq.params.set(paramStart + FacetParams.FACET_LIMIT, dff.initialLimit);
      }
    } else {
      // turn off faceting on other requests
      sreq.params.set(FacetParams.FACET, "false");
      // we could optionally remove faceting params
    }
  }
  
  @Override
  public void handleResponses(ResponseBuilder rb, ShardRequest sreq) {
    if (!rb.doFacets) return;
    
    if ((sreq.purpose & ShardRequest.PURPOSE_GET_FACETS) != 0) {
      countFacets(rb, sreq);
    } else if ((sreq.purpose & ShardRequest.PURPOSE_REFINE_FACETS) != 0) {
      refineFacets(rb, sreq);
    }
  }




  private void countFacets(ResponseBuilder rb, ShardRequest sreq) {
    FacetInfo fi = rb._facetInfo;    
    SimpleOrderedMap<Map<Object,NamedList<Object>>> pivotFacetsMap = null;
    SimpleOrderedMap<Map<Integer,Map<Object,Integer>>> fieldCountsMap = null;
    boolean pivoting = false;
    PivotLimitInfo limitInfo = null;
    List<String> facetLimitIgnoreFieldList = null;
    SimpleOrderedMap<List<Integer>> facetLimitIgnoreIndexsMap = null;    
    boolean sortPivotsByCount = true;
    
    for (ShardResponse srsp: sreq.responses) {
      int shardNum = rb.getShardNum(srsp.getShard());
      NamedList facet_counts = null;
      try {
        facet_counts = (NamedList)srsp.getSolrResponse().getResponse().get("facet_counts");
      }
      catch(Exception ex) {
        if(rb.req.getParams().getBool(ShardParams.SHARDS_TOLERANT, false)) {
          continue; // looks like a shard did not return anything
        }
        throw new SolrException(ErrorCode.SERVER_ERROR, "Unable to read facet info for shard: "+srsp.getShard(), ex);
      }
      
      // handle facet queries
      NamedList facet_queries = (NamedList) facet_counts.get("facet_queries");
      if (facet_queries != null) {
        for (int i = 0; i < facet_queries.size(); i++) {
          String returnedKey = facet_queries.getName(i);
          long count = ((Number) facet_queries.getVal(i)).longValue();
          QueryFacet qf = fi.queryFacets.get(returnedKey);
          qf.count += count;
        }
      }
      
      // step through each facet.field, adding results from this shard
      NamedList facet_fields = (NamedList) facet_counts.get("facet_fields");
      
      if (facet_fields != null) {
        for (DistribFieldFacet dff : fi.facets.values()) {
          dff.add(shardNum, (NamedList)facet_fields.get(dff.getKey()), dff.initialLimit);
        }
      }
      
      // Distributed facet_dates
      //
      // The implementation below uses the first encountered shard's
      // facet_dates as the basis for subsequent shards' data to be merged.
      // (the "NOW" param should ensure consistency)
      @SuppressWarnings("unchecked")
      SimpleOrderedMap<SimpleOrderedMap<Object>> facet_dates = 
        (SimpleOrderedMap<SimpleOrderedMap<Object>>) 
        facet_counts.get("facet_dates");
      
      if (facet_dates != null) {
        
        // go through each facet_date
        for (Map.Entry<String,SimpleOrderedMap<Object>> entry : facet_dates) {
          final String field = entry.getKey();
          if (fi.dateFacets.get(field) == null) {
            // first time we've seen this field, no merging
            fi.dateFacets.add(field, entry.getValue());
            
          } else {
            // not the first time, merge current field
            
            SimpleOrderedMap<Object> shardFieldValues 
              = entry.getValue();
            SimpleOrderedMap<Object> existFieldValues 
              = fi.dateFacets.get(field);
            
            for (Map.Entry<String,Object> existPair : existFieldValues) {
              final String key = existPair.getKey();
              if (key.equals("gap") || 
                  key.equals("end") || 
                  key.equals("start")) {
                // we can skip these, must all be the same across shards
                continue;
              }
              // can be null if inconsistencies in shards responses
              Integer newValue = (Integer) shardFieldValues.get(key);
              if (null != newValue) {
                Integer oldValue = ((Integer) existPair.getValue());
                existPair.setValue(oldValue + newValue);
              }
            }
          }
        }
      }
      
      // Distributed facet_ranges
      //
      // The implementation below uses the first encountered shard's
      // facet_ranges as the basis for subsequent shards' data to be merged.
      @SuppressWarnings("unchecked")
      SimpleOrderedMap<SimpleOrderedMap<Object>> facet_ranges = 
        (SimpleOrderedMap<SimpleOrderedMap<Object>>) 
        facet_counts.get("facet_ranges");
      
      if (facet_ranges != null) {
        
        // go through each facet_range
        for (Map.Entry<String,SimpleOrderedMap<Object>> entry : facet_ranges) {
          final String field = entry.getKey();
          if (fi.rangeFacets.get(field) == null) {
            // first time we've seen this field, no merging
            fi.rangeFacets.add(field, entry.getValue());
            
          } else {
            // not the first time, merge current field counts
            
            @SuppressWarnings("unchecked")
            NamedList<Integer> shardFieldValues 
              = (NamedList<Integer>) entry.getValue().get("counts");

            @SuppressWarnings("unchecked")
            NamedList<Integer> existFieldValues 
              = (NamedList<Integer>) fi.rangeFacets.get(field).get("counts");
            
            for (Map.Entry<String,Integer> existPair : existFieldValues) {
              final String key = existPair.getKey();
              // can be null if inconsistencies in shards responses
              Integer newValue = shardFieldValues.get(key);
              if (null != newValue) {
                Integer oldValue = existPair.getValue();
                existPair.setValue(oldValue + newValue);
              }
            }
          }
        }
      }
      
      // Distributed facet_pivots
      //
      // The implementation below uses the first encountered shard's
      // facet_pivots as the basis for subsequent shards' data to be merged.      
      @SuppressWarnings("unchecked")
      SimpleOrderedMap<List<NamedList<Object>>> facet_pivot = (SimpleOrderedMap<List<NamedList<Object>>>) facet_counts
          .get("facet_pivot");
      
      if (facet_pivot != null) {
        if (pivotFacetsMap == null) {
          pivoting = true;
          SolrParams params = rb.req.getParams();
          String facetSort = params.get(FacetParams.FACET_SORT);
          if (facetSort != null
              && facetSort.equals(FacetParams.FACET_SORT_INDEX)) {
            sortPivotsByCount = false;
          }
          limitInfo = new PivotLimitInfo();
          limitInfo.limit = params.getInt(FacetParams.FACET_LIMIT, 0);
          String facetLimitMethod = params
              .get(FacetParams.FACET_PIVOT_LIMIT_METHOD);
          if (facetLimitMethod != null
              && facetLimitMethod
                  .equals(FacetParams.COMBINED_PIVOT_FACET_LIMIT)) {
            limitInfo.combinedPivotLimit = true;
            fieldCountsMap = new SimpleOrderedMap<Map<Integer,Map<Object,Integer>>>();
            facetLimitIgnoreIndexsMap = new SimpleOrderedMap<List<Integer>>();
          }
          String facetLimitIgnores = params
              .get(FacetParams.FACET_PIVOT_LIMIT_IGNORE);
          if (facetLimitIgnores != null) {
            facetLimitIgnoreFieldList = Arrays.asList(facetLimitIgnores
                .split(","));
          }
          pivotFacetsMap = new SimpleOrderedMap<Map<Object,NamedList<Object>>>();
        }
        
        // go through each facet_pivot
        for (Map.Entry<String,List<NamedList<Object>>> pivot : facet_pivot) {
          final String pivotName = pivot.getKey();
          final Integer numberOfPivots;
          if (facetLimitIgnoreFieldList != null) {
            List<Integer> facetLimitIgnoreIndexList = new ArrayList<Integer>();
            List<String> pivotFields = Arrays.asList(pivotName.split(","));
            for (String facetLimitIgnore : facetLimitIgnoreFieldList) {
              int thisIndex = pivotFields.indexOf(facetLimitIgnore);
              if (thisIndex > -1) {
                // Add one here because pivots start from 1, whereas list indexs
                // start from 0
                facetLimitIgnoreIndexList.add(thisIndex + 1);
              }
            }
            facetLimitIgnoreIndexsMap.add(pivotName, facetLimitIgnoreIndexList);
            numberOfPivots = pivotFields.size();
          } else {
            numberOfPivots = 1 + StringUtils.countMatches(pivotName, ",");
          }
          Map<Integer,Map<Object,Integer>> fieldCounts = null;
          if (limitInfo.combinedPivotLimit) {
            fieldCounts = fieldCountsMap.get(pivotName);
            if (fieldCounts == null) {
              fieldCounts = new HashMap<Integer,Map<Object,Integer>>();
              fieldCountsMap.add(pivotName, fieldCounts);
            }
          }
          Map<Object,NamedList<Object>> pivotValues = pivotFacetsMap
              .get(pivotName);
          if (pivotValues == null) {
            // first time we've seen this pivot, no merging
            pivotFacetsMap.add(pivotName, pivotHelper.convertPivotsToMaps(
                pivot.getValue(), 1, numberOfPivots, fieldCounts));
            
          } else {
            // not the first time, merge
            
            @SuppressWarnings("unchecked")
            List<NamedList<Object>> shardPivotValues = (List<NamedList<Object>>) pivot
                .getValue();
            
            mergePivotFacet(pivotValues, shardPivotValues, 1, numberOfPivots,
                fieldCounts);
          }
        }
      }
      
    }
    
    // set pivot facets from map
    
    if (pivoting) {
      if (limitInfo.combinedPivotLimit) {
        Comparator<Entry<Object,Integer>> entryCountComparator = new EntryCountComparator();
        limitInfo.fieldLimitsMap = new SimpleOrderedMap<List<List<Object>>>();
        for (Entry<String,Map<Integer,Map<Object,Integer>>> fieldCountsEntry : fieldCountsMap) {
          List<Integer> facetLimitIgnoreIndexs = facetLimitIgnoreIndexsMap
              .get(fieldCountsEntry.getKey());
          List<List<Object>> limitedValuesForPivot = new ArrayList<List<Object>>();
          Integer pivot = 1;
          Map<Object,Integer> fieldCountsForPivot = fieldCountsEntry.getValue()
              .get(pivot);
          while (fieldCountsForPivot != null) {
            List<Object> limitedValuesForField = null;
            if ((facetLimitIgnoreIndexs == null || !facetLimitIgnoreIndexs
                .contains(pivot))
                && fieldCountsForPivot.size() > limitInfo.limit) {
              limitedValuesForField = new ArrayList<Object>();
              List<Entry<Object,Integer>> fieldCountsForPivotList = new ArrayList<Map.Entry<Object,Integer>>(
                  fieldCountsForPivot.entrySet());
              Collections.sort(fieldCountsForPivotList, entryCountComparator);
              for (int valueIndex = 0; valueIndex < limitInfo.limit; valueIndex++) {
                limitedValuesForField.add(fieldCountsForPivotList.get(
                    valueIndex).getKey());
              }
            }
            limitedValuesForPivot.add(limitedValuesForField);
            fieldCountsForPivot = fieldCountsEntry.getValue().get(++pivot);
          }
          limitInfo.fieldLimitsMap.add(fieldCountsEntry.getKey(),
              limitedValuesForPivot);
        }
      }
      fi.pivotFacets = pivotHelper.convertPivotMapsToList(pivotFacetsMap,
          limitInfo, sortPivotsByCount);
    }    
    //
    // This code currently assumes that there will be only a single
    // request ((with responses from all shards) sent out to get facets...
    // otherwise we would need to wait until all facet responses were received.
    //
    
    for (DistribFieldFacet dff : fi.facets.values()) {
      // no need to check these facets for refinement
      if (dff.initialLimit <= 0 && dff.initialMincount == 0) continue;
      
      // only other case where index-sort doesn't need refinement is if minCount==0
      if (dff.minCount == 0 && dff.sort.equals(FacetParams.FACET_SORT_INDEX)) continue;

      @SuppressWarnings("unchecked") // generic array's are annoying
      List<String>[] tmp = (List<String>[]) new List[rb.shards.length];
      dff._toRefine = tmp;
      
      ShardFacetCount[] counts = dff.getCountSorted();
      int ntop = Math.min(counts.length, dff.limit >= 0 ? dff.offset + dff.limit : Integer.MAX_VALUE);
      long smallestCount = counts.length == 0 ? 0 : counts[ntop - 1].count;
      
      for (int i = 0; i < counts.length; i++) {
        ShardFacetCount sfc = counts[i];
        boolean needRefinement = false;
        
        if (i < ntop) {
          // automatically flag the top values for refinement
          // this should always be true for facet.sort=index
          needRefinement = true;
        } else {
          // this logic should only be invoked for facet.sort=index (for now)
          
          // calculate the maximum value that this term may have
          // and if it is >= smallestCount, then flag for refinement
          long maxCount = sfc.count;
          for (int shardNum = 0; shardNum < rb.shards.length; shardNum++) {
            OpenBitSet obs = dff.counted[shardNum];
            if (obs!=null && !obs.get(sfc.termNum)) {  // obs can be null if a shard request failed
              // if missing from this shard, add the max it could be
              maxCount += dff.maxPossible(sfc, shardNum);
            }
          }
          if (maxCount >= smallestCount) {
            // TODO: on a tie, we could check the term values
            needRefinement = true;
          }
        }
        
        if (needRefinement) {
          // add a query for each shard missing the term that needs refinement
          for (int shardNum = 0; shardNum < rb.shards.length; shardNum++) {
            OpenBitSet obs = dff.counted[shardNum];
            if(obs!=null && !obs.get(sfc.termNum) && dff.maxPossible(sfc,shardNum)>0) {
              dff.needRefinements = true;
              List<String> lst = dff._toRefine[shardNum];
              if (lst == null) {
                lst = dff._toRefine[shardNum] = new ArrayList<String>();
              }
              lst.add(sfc.name);
            }
          }
        }
      }
    }
  }
  
  private void mergePivotFacet(Map<Object,NamedList<Object>> pivotValues,
      List<NamedList<Object>> shardPivotValues, int currentPivot,
      int numberOfPivots, Map<Integer,Map<Object,Integer>> fieldCounts) {
    Iterator<NamedList<Object>> shardPivotValuesIterator = shardPivotValues
        .iterator();
    boolean countFields = (fieldCounts != null);
    Map<Object,Integer> thisFieldCountMap = null;
    if (countFields) {
      thisFieldCountMap = pivotHelper.getFieldCountMap(fieldCounts,
          currentPivot);
    }
    while (shardPivotValuesIterator.hasNext()) {
      NamedList<Object> shardPivotValue = shardPivotValuesIterator.next();
      Object valueObj = namedListHelper.getFromPivotList(PivotListEntry.VALUE,
          shardPivotValue);
      Object shardCountObj = namedListHelper.getFromPivotList(
          PivotListEntry.COUNT, shardPivotValue);
      int shardCount = 0;
      if (shardCountObj instanceof Integer) {
        shardCount = (Integer) shardCountObj;
      }
      if (countFields) {
        pivotHelper.addFieldCounts(valueObj, shardCount, thisFieldCountMap);
      }
      NamedList<Object> pivotValue = pivotValues.get(valueObj);
      if (pivotValue == null) {
        // pivot value not found, add to existing values
        pivotValues.put(valueObj, shardPivotValue);
        if (currentPivot < numberOfPivots) {
          int pivotIdx = shardPivotValue.indexOf(
              PivotListEntry.PIVOT.getName(), 0);
          Object shardPivotObj = shardPivotValue.getVal(pivotIdx);
          if (shardPivotObj instanceof List) {
            shardPivotValue.setVal(pivotIdx, pivotHelper.convertPivotsToMaps(
                (List) shardPivotObj, currentPivot + 1, numberOfPivots,
                fieldCounts));
          }
        }
      } else {
        Object existingCountObj = namedListHelper.getFromPivotList(
            PivotListEntry.COUNT, pivotValue);        
        if (existingCountObj instanceof Integer) {
          int countIdx = pivotValue.indexOf(PivotListEntry.COUNT.getName(), 0);
          pivotValue
              .setVal(countIdx, ((Integer) existingCountObj) + shardCount);
        } else {
          StringBuffer errMsg = new StringBuffer(
              "Count value for pivot field: ");
          errMsg.append(namedListHelper.getFromPivotList(PivotListEntry.FIELD,
              pivotValue));
          errMsg.append(" with value: ");
          errMsg.append(namedListHelper.getFromPivotList(PivotListEntry.VALUE,
              pivotValue));
          errMsg
              .append(
                  " is not of type Integer. Cannot increment count for this pivot. Count is of type: ");
          errMsg.append(existingCountObj.getClass().getCanonicalName());
          errMsg.append(" and value: ").append(existingCountObj);
          log.error(errMsg.toString());
        }
        NamedList<NamedList<Object>> existingStatistics = (NamedList<NamedList<Object>>)namedListHelper.getFromPivotList(PivotListEntry.STATISTICS, pivotValue);
        NamedList<NamedList<Object>> shardStatistics = (NamedList<NamedList<Object>>)namedListHelper.getFromPivotList(PivotListEntry.STATISTICS, shardPivotValue);
        int statsIndex = pivotValue.indexOf(PivotListEntry.STATISTICS.getName(), 0);
        pivotValue.setVal(statsIndex, mergePivotStatistics(existingStatistics, shardStatistics));
        
        if (currentPivot < numberOfPivots) {
          Object shardPivotObj = shardPivotValue.get("pivot");
          Object pivotObj = pivotValue.get("pivot");
          if (shardPivotObj instanceof List) {
            if (pivotObj instanceof Map) {
              mergePivotFacet((Map) pivotObj, (List) shardPivotObj,
                  currentPivot + 1, numberOfPivots, fieldCounts);
            } else {
              pivotValue.add("pivot", pivotHelper.convertPivotsToMaps(
                  (List) shardPivotObj, currentPivot + 1, numberOfPivots,
                  fieldCounts));
            }
          }
        }
        shardPivotValuesIterator.remove();
      }
    }
  }
  
  private Object mergePivotStatistics(NamedList<NamedList<Object>> existingFields,
		  NamedList<NamedList<Object>> shardFields) {
	  
	  boolean haveExistingStats = existingFields != null;
	  boolean haveShardStats = shardFields != null;
	  if(haveExistingStats && !haveShardStats) 
		  return existingFields;
	  if(!haveExistingStats && haveShardStats) 
		  return shardFields;
	  if(!haveExistingStats && !haveShardStats) 
		  return null;  

	  //stats->fields->{buckets,totalCount}	  
	  
	  Iterator shardFieldsIterator = shardFields.iterator();
	  while(shardFieldsIterator.hasNext()) {
		  Entry<String,NamedList<Object>> shardSingleFieldStatistics = (Entry<String,NamedList<Object>>)shardFieldsIterator.next();
		  String fieldName = shardSingleFieldStatistics.getKey();
		  NamedList<Object> shardSingleFieldStatisticsData = shardSingleFieldStatistics.getValue();
		  int shardFieldBucketTotal = (Integer)shardSingleFieldStatisticsData.get(FacetParams.PERCENTILE_SHARD_TOTAL_COUNT);
		  if(shardFieldBucketTotal == 0) {
			  continue;
		  }
		  NamedList<Object> existingSingleFieldStatistics = (NamedList<Object>) existingFields.get(fieldName);
		  if (existingSingleFieldStatistics == null) {
			  existingFields.add(fieldName, shardSingleFieldStatisticsData);
			  continue;
		  }
		  else {
			  int fieldIndex = existingFields.indexOf(fieldName, 0);
			  existingFields.setVal(fieldIndex, mergeFieldStatistics(existingSingleFieldStatistics, shardSingleFieldStatisticsData));
		  }
		  
		  
	  }
	  
	  
	return existingFields;
}

private NamedList<Object> mergeFieldStatistics(
		NamedList<Object> existingSingleFieldStatistics,
		NamedList<Object> shardSingleFieldStatistics) {

	NamedList<Integer> existingBuckets = (NamedList<Integer>)existingSingleFieldStatistics.get(FacetParams.PERCENTILE_BUCKETS);
	NamedList<Integer> shardBuckets = (NamedList<Integer>)shardSingleFieldStatistics.get(FacetParams.PERCENTILE_BUCKETS);
	NamedList<Object> mergedBuckets = new NamedList<Object>();
	NamedList<Object> mergedStatistics = new NamedList<Object>();
	Double existingAverage;
	Double shardAverage;
  Double shardTotal = 0D;
	int existingPercentilesCount;
	int shardPercentilesCount;
	
	//left-padded numeric bucket names with 0's to size of upper fence, rely on string sorting between bucket lists
	//solr datetime format is lex sortable
	while(existingBuckets.size() > 0 && shardBuckets.size() > 0) {
		
		int comparison = shardBuckets.getName(0).compareTo(existingBuckets.getName(0));
		
		if(comparison > 0) {
			mergedBuckets.add(existingBuckets.getName(0), existingBuckets.remove(0));			
		}
		else if(comparison < 0) {
			mergedBuckets.add(shardBuckets.getName(0), shardBuckets.remove(0));
		}
		else if(comparison == 0) {
			mergedBuckets.add(shardBuckets.getName(0), shardBuckets.remove(0) + existingBuckets.remove(0));
		}		
	}
	while(existingBuckets.size() > 0) {
		mergedBuckets.add(existingBuckets.getName(0), existingBuckets.remove(0));
	}
	while(shardBuckets.size() > 0) {
		mergedBuckets.add(shardBuckets.getName(0), shardBuckets.remove(0));
	}
	mergedStatistics.add(FacetParams.PERCENTILE_BUCKETS, mergedBuckets);

	//don't forget the counts
	int shardFieldBucketTotal = (Integer)shardSingleFieldStatistics.get(FacetParams.PERCENTILE_SHARD_TOTAL_COUNT);
	int existingFieldBucketTotal = (Integer)existingSingleFieldStatistics.get(FacetParams.PERCENTILE_SHARD_TOTAL_COUNT);
	mergedStatistics.add(FacetParams.PERCENTILE_SHARD_TOTAL_COUNT, shardFieldBucketTotal + existingFieldBucketTotal);
	
	//check for and include averages	
	shardAverage = (Double)shardSingleFieldStatistics.get("percentiles_average");
	if(shardAverage == null) {
		//do nothing further, averages were not requested
	}
	else {
    shardTotal += (Double)shardSingleFieldStatistics.get("percentiles_sum");
		shardPercentilesCount = (Integer)shardSingleFieldStatistics.get("percentiles_count");
		existingAverage = (Double)existingSingleFieldStatistics.get("percentiles_average");
		existingPercentilesCount = (Integer)existingSingleFieldStatistics.get("percentiles_count");
		int mergedCount = existingPercentilesCount + shardPercentilesCount;
		double mergedAverage = ((shardPercentilesCount / mergedCount) * shardAverage) + ((existingPercentilesCount / mergedCount) * existingAverage);
		mergedStatistics.add("percentiles_average", mergedAverage);
		mergedStatistics.add("percentiles_count", mergedCount);
    mergedStatistics.add("percentiles_sum", shardTotal);
	}
	
	
	
	return mergedStatistics;
}

private void refineFacets(ResponseBuilder rb, ShardRequest sreq) {
    FacetInfo fi = rb._facetInfo;
    
    for (ShardResponse srsp : sreq.responses) {
      // int shardNum = rb.getShardNum(srsp.shard);
      NamedList facet_counts = (NamedList)srsp.getSolrResponse().getResponse().get("facet_counts");
      NamedList facet_fields = (NamedList) facet_counts.get("facet_fields");
      
      if (facet_fields == null) continue; // this can happen when there's an exception      
      
      for (int i = 0; i < facet_fields.size(); i++) {
        String key = facet_fields.getName(i);
        DistribFieldFacet dff = fi.facets.get(key);
        if (dff == null) continue;
        
        NamedList shardCounts = (NamedList) facet_fields.getVal(i);
        
        for (int j = 0; j < shardCounts.size(); j++) {
          String name = shardCounts.getName(j);
          long count = ((Number) shardCounts.getVal(j)).longValue();
          ShardFacetCount sfc = dff.counts.get(name);
          if (sfc == null) {
            // we got back a term we didn't ask for?
            log.error("Unexpected term returned for facet refining. key=" + key + " term='" + name + "'"
              + "\n\trequest params=" + sreq.params
              + "\n\ttoRefine=" + dff._toRefine
              + "\n\tresponse=" + shardCounts
            );
            continue;
          }
          sfc.count += count;
        }
      }
    }
  }
  
  @Override
  public void finishStage(ResponseBuilder rb) {
    if (!rb.doFacets || rb.stage != ResponseBuilder.STAGE_GET_FIELDS) return;
    // wait until STAGE_GET_FIELDS
    // so that "result" is already stored in the response (for aesthetics)
    

    FacetInfo fi = rb._facetInfo;
    
    NamedList<Object> facet_counts = new SimpleOrderedMap<Object>();
    
    NamedList<Number> facet_queries = new SimpleOrderedMap<Number>();
    facet_counts.add("facet_queries", facet_queries);
    for (QueryFacet qf : fi.queryFacets.values()) {
      facet_queries.add(qf.getKey(), num(qf.count));
    }
    
    NamedList<Object> facet_fields = new SimpleOrderedMap<Object>();
    facet_counts.add("facet_fields", facet_fields);
    
    for (DistribFieldFacet dff : fi.facets.values()) {
      NamedList<Object> fieldCounts = new NamedList<Object>(); // order is more important for facets
      facet_fields.add(dff.getKey(), fieldCounts);
      
      ShardFacetCount[] counts;
      boolean countSorted = dff.sort.equals(FacetParams.FACET_SORT_COUNT);
      if (countSorted) {
        counts = dff.countSorted;
        if (counts == null || dff.needRefinements) {
          counts = dff.getCountSorted();
        }
      } else if (dff.sort.equals(FacetParams.FACET_SORT_INDEX)) {
        counts = dff.getLexSorted();
      } else { // TODO: log error or throw exception?
        counts = dff.getLexSorted();
      }
      
      if (countSorted) {
        int end = dff.limit < 0 ? counts.length : Math.min(dff.offset + dff.limit, counts.length);
        for (int i = dff.offset; i < end; i++) {
          if (counts[i].count < dff.minCount) {
            break;
          }
          fieldCounts.add(counts[i].name, num(counts[i].count));
        }
      } else {
        int off = dff.offset;
        int lim = dff.limit >= 0 ? dff.limit : Integer.MAX_VALUE;
        
        // index order...
        for (int i = 0; i < counts.length; i++) {
          long count = counts[i].count;
          if (count < dff.minCount) continue;
          if (off > 0) {
            off--;
            continue;
          }
          if (lim <= 0) {
            break;
          }
          lim--;
          fieldCounts.add(counts[i].name, num(count));
        }
      }

      if (dff.missing) {
        fieldCounts.add(null, num(dff.missingCount));
      }
    }

    facet_counts.add("facet_dates", fi.dateFacets);
    facet_counts.add("facet_ranges", fi.rangeFacets);
    if(fi.pivotFacets.size() > 0) {    	
    	if(rb.doPercentiles) {
    		SolrParams solrParams = rb.req.getParams();    		
    		fi.pivotFacets = convertPivotStatisticsBucketsToPercentiles(fi.pivotFacets, solrParams);
    	}
    	facet_counts.add("facet_pivot", fi.pivotFacets);
    }

    rb.rsp.add("facet_counts", facet_counts);

    rb._facetInfo = null;  // could be big, so release asap
  }


  private SimpleOrderedMap<List<NamedList<Object>>> convertPivotStatisticsBucketsToPercentiles(
		SimpleOrderedMap<List<NamedList<Object>>> pivotFacets, SolrParams required) {

	  for(int i =0; i < pivotFacets.size(); i++) {
		  pivotFacets.setVal(i, convertPivotList(pivotFacets.getVal(i), required));
	  }
	  
	  return pivotFacets;
}

private List<NamedList<Object>> convertPivotList(List<NamedList<Object>> val, SolrParams required) {

	for(int i =0; i < val.size(); i++) {
		val.set(i, convertPivotStatistics(val.get(i),required));
	}
	
	return val;
}

private NamedList<Object> convertPivotStatistics(NamedList<Object> thisPivot, SolrParams required) {

	
	int pivotIndex = thisPivot.indexOf(PivotListEntry.PIVOT.getName(), 0);
	if(pivotIndex > -1) {
		ArrayList<Object> furtherPivots = (ArrayList<Object>)thisPivot.getVal(pivotIndex);
		ArrayList<Object> convertedFurtherPivots = new ArrayList<Object>();
		for(int i = 0; i < furtherPivots.size(); i++) {
		 convertedFurtherPivots.add(convertPivotStatistics((NamedList<Object>)furtherPivots.get(i),required));
		}
		thisPivot.setVal(pivotIndex, convertedFurtherPivots);
	}
	int statsIndex = thisPivot.indexOf(PivotListEntry.STATISTICS.getName(), 0);
	if(statsIndex > -1) {
		
		thisPivot.setVal(statsIndex, convertPivotStatisticsFields((NamedList<Object>)thisPivot.getVal(statsIndex), required));
	}
	
	
	return thisPivot;

}

private Object convertPivotStatisticsFields(NamedList<Object> listOfFields, SolrParams required) {
	
	for(int i =0 ; i < listOfFields.size(); i++ ) {
		String fieldName = listOfFields.getName(i);
		listOfFields.setVal(i, convertOnePivotStatisticsField((NamedList<Object>)listOfFields.getVal(i), required, fieldName));
	}
	
	return listOfFields;
}

private NamedList<Object> convertOnePivotStatisticsField(NamedList<Object> statsData, SolrParams solrParams, String fieldName) {
	
	Integer totalCount = (Integer)statsData.get(FacetParams.PERCENTILE_SHARD_TOTAL_COUNT);
	if(totalCount != null) {
		RequiredSolrParams required = new RequiredSolrParams(solrParams);
		String[] requestedPercentiles = required.getFieldParams(fieldName, FacetParams.PERCENTILE_REQUESTED_PERCENTILES);
		boolean calculateAverages = solrParams.getBool(FacetParams.PERCENTILE_AVERAGES, false);
		
		Integer bucketsIndex = statsData.indexOf(FacetParams.PERCENTILE_BUCKETS, 0);
		FacetPercentiles fp = new FacetPercentiles(requestedPercentiles, totalCount);
		if(bucketsIndex > -1) {		
			NamedList<Integer> buckets = (NamedList<Integer>)statsData.getVal(bucketsIndex);
			for(int i =0; i < buckets.size() && (fp.stillLookingForPercentiles() || calculateAverages); i++) {
				fp.processFacetCount(buckets.getName(i), buckets.getVal(i));
				if(calculateAverages) {
					fp.accumulateAverage(buckets.getName(i), buckets.getVal(i));
				}
			}
		}
		statsData = new NamedList<Object>();
		statsData.add(FacetParams.PERCENTILE, fp.getPercentiles());
		if(calculateAverages) {
			statsData.add("percentiles_average", fp.getAverage());
			statsData.add("percentiles_count", fp.getTotalCount());
      statsData.add("percentiles_sum", fp.getTotal());
		}
	}
	else {
		statsData = new NamedList<Object>();
	}
	return statsData;
}

// use <int> tags for smaller facet counts (better back compatibility)
  private Number num(long val) {
   if (val < Integer.MAX_VALUE) return (int)val;
   else return val;
  }
  private Number num(Long val) {
    if (val.longValue() < Integer.MAX_VALUE) return val.intValue();
    else return val;
  }


  /////////////////////////////////////////////
  ///  SolrInfoMBean
  ////////////////////////////////////////////

  @Override
  public String getDescription() {
    return "Handle Faceting";
  }

  @Override
  public String getSource() {
    return "$URL: http://svn.apache.org/repos/asf/lucene/dev/branches/branch_4x/solr/core/src/java/org/apache/solr/handler/component/FacetComponent.java $";
  }

  @Override
  public URL[] getDocs() {
    return null;
  }

  /**
   * <b>This API is experimental and subject to change</b>
   */
  public static class FacetInfo {

    public LinkedHashMap<String,QueryFacet> queryFacets;
    public LinkedHashMap<String,DistribFieldFacet> facets;
    public SimpleOrderedMap<SimpleOrderedMap<Object>> dateFacets
      = new SimpleOrderedMap<SimpleOrderedMap<Object>>();
    public SimpleOrderedMap<SimpleOrderedMap<Object>> rangeFacets
      = new SimpleOrderedMap<SimpleOrderedMap<Object>>();
    public SimpleOrderedMap<List<NamedList<Object>>> pivotFacets = new SimpleOrderedMap<List<NamedList<Object>>>();

    void parse(SolrParams params, ResponseBuilder rb) {
      queryFacets = new LinkedHashMap<String,QueryFacet>();
      facets = new LinkedHashMap<String,DistribFieldFacet>();

      String[] facetQs = params.getParams(FacetParams.FACET_QUERY);
      if (facetQs != null) {
        for (String query : facetQs) {
          QueryFacet queryFacet = new QueryFacet(rb, query);
          queryFacets.put(queryFacet.getKey(), queryFacet);
        }
      }
      
      String[] facetFs = params.getParams(FacetParams.FACET_FIELD);
      if (facetFs != null) {
        
        for (String field : facetFs) {
          DistribFieldFacet ff = new DistribFieldFacet(rb, field);
          facets.put(ff.getKey(), ff);
        }
      }
    }
  }
  
  /**
   * <b>This API is experimental and subject to change</b>
   */
  public static class FacetBase {
    String facetType; // facet.field, facet.query, etc (make enum?)
    String facetStr; // original parameter value of facetStr
    String facetOn; // the field or query, absent localParams if appropriate
    private String key; // label in the response for the result... "foo" for {!key=foo}myfield
    SolrParams localParams; // any local params for the facet
    
    public FacetBase(ResponseBuilder rb, String facetType, String facetStr) {
      this.facetType = facetType;
      this.facetStr = facetStr;
      try {
        this.localParams = QueryParsing.getLocalParams(facetStr, rb.req.getParams());
      } catch (ParseException e) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
      }
      this.facetOn = facetStr;
      this.key = facetStr;
      
      if (localParams != null) {
        // remove local params unless it's a query
        if (!facetType.equals(FacetParams.FACET_QUERY)) {
          facetOn = localParams.get(CommonParams.VALUE);
          key = facetOn;
        }
        
        key = localParams.get(CommonParams.OUTPUT_KEY, key);
      }
    }
    
    /** returns the key in the response that this facet will be under */
    public String getKey() { return key; }
    public String getType() { return facetType; }
  }

  /**
   * <b>This API is experimental and subject to change</b>
   */
  public static class QueryFacet extends FacetBase {
    public long count;
    
    public QueryFacet(ResponseBuilder rb, String facetStr) {
      super(rb, FacetParams.FACET_QUERY, facetStr);
    }
  }
  
  /**
   * <b>This API is experimental and subject to change</b>
   */
  public static class FieldFacet extends FacetBase {
    public String field;     // the field to facet on... "myfield" for {!key=foo}myfield
    public FieldType ftype;
    public int offset;
    public int limit;
    public int minCount;
    public String sort;
    public boolean missing;
    public String prefix;
    public long missingCount;
    
    public FieldFacet(ResponseBuilder rb, String facetStr) {
      super(rb, FacetParams.FACET_FIELD, facetStr);
      fillParams(rb, rb.req.getParams(), facetOn);
    }
    
    private void fillParams(ResponseBuilder rb, SolrParams params, String field) {
      this.field = field;
      this.ftype = rb.req.getSchema().getFieldTypeNoEx(this.field);
      this.offset = params.getFieldInt(field, FacetParams.FACET_OFFSET, 0);
      this.limit = params.getFieldInt(field, FacetParams.FACET_LIMIT, 100);
      Integer mincount = params.getFieldInt(field, FacetParams.FACET_MINCOUNT);
      if (mincount == null) {
        Boolean zeros = params.getFieldBool(field, FacetParams.FACET_ZEROS);
        // mincount = (zeros!=null && zeros) ? 0 : 1;
        mincount = (zeros != null && !zeros) ? 1 : 0;
        // current default is to include zeros.
      }
      this.minCount = mincount;
      this.missing = params.getFieldBool(field, FacetParams.FACET_MISSING, false);
      // default to sorting by count if there is a limit.
      this.sort = params.getFieldParam(field, FacetParams.FACET_SORT, limit>0 ? FacetParams.FACET_SORT_COUNT : FacetParams.FACET_SORT_INDEX);
      if (this.sort.equals(FacetParams.FACET_SORT_COUNT_LEGACY)) {
        this.sort = FacetParams.FACET_SORT_COUNT;
      } else if (this.sort.equals(FacetParams.FACET_SORT_INDEX_LEGACY)) {
        this.sort = FacetParams.FACET_SORT_INDEX;
      }
      this.prefix = params.getFieldParam(field, FacetParams.FACET_PREFIX);
    }
  }
  
  /**
   * <b>This API is experimental and subject to change</b>
   */
  public static class DistribFieldFacet extends FieldFacet {
    public List<String>[] _toRefine; // a List<String> of refinements needed, one for each shard.
    
    // SchemaField sf; // currently unneeded
    
    // the max possible count for a term appearing on no list
    public long missingMaxPossible;
    // the max possible count for a missing term for each shard (indexed by shardNum)
    public long[] missingMax;
    public OpenBitSet[] counted; // a bitset for each shard, keeping track of which terms seen
    public HashMap<String,ShardFacetCount> counts = new HashMap<String,ShardFacetCount>(128);
    public int termNum;
    
    public int initialLimit; // how many terms requested in first phase
    public int initialMincount; // mincount param sent to each shard
    public boolean needRefinements;
    public ShardFacetCount[] countSorted;
    
    DistribFieldFacet(ResponseBuilder rb, String facetStr) {
      super(rb, facetStr);
      // sf = rb.req.getSchema().getField(field);
      missingMax = new long[rb.shards.length];
      counted = new OpenBitSet[rb.shards.length];
    }
    
    void add(int shardNum, NamedList shardCounts, int numRequested) {
      // shardCounts could be null if there was an exception
      int sz = shardCounts == null ? 0 : shardCounts.size();
      int numReceived = sz;
      
      OpenBitSet terms = new OpenBitSet(termNum + sz);
      
      long last = 0;
      for (int i = 0; i < sz; i++) {
        String name = shardCounts.getName(i);
        long count = ((Number) shardCounts.getVal(i)).longValue();
        if (name == null) {
          missingCount += count;
          numReceived--;
        } else {
          ShardFacetCount sfc = counts.get(name);
          if (sfc == null) {
            sfc = new ShardFacetCount();
            sfc.name = name;
            sfc.indexed = ftype == null ? sfc.name : ftype.toInternal(sfc.name);
            sfc.termNum = termNum++;
            counts.put(name, sfc);
          }
          sfc.count += count;
          terms.fastSet(sfc.termNum);
          last = count;
        }
      }
      
      // the largest possible missing term is initialMincount if we received less
      // than the number requested.
      if (numRequested < 0 || numRequested != 0 && numReceived < numRequested) {
        last = initialMincount;
      }
      
      missingMaxPossible += last;
      missingMax[shardNum] = last;
      counted[shardNum] = terms;
    }
    
    public ShardFacetCount[] getLexSorted() {
      ShardFacetCount[] arr = counts.values().toArray(new ShardFacetCount[counts.size()]);
      Arrays.sort(arr, new Comparator<ShardFacetCount>() {
        public int compare(ShardFacetCount o1, ShardFacetCount o2) {
          return o1.indexed.compareTo(o2.indexed);
        }
      });
      countSorted = arr;
      return arr;
    }
    
    public ShardFacetCount[] getCountSorted() {
      ShardFacetCount[] arr = counts.values().toArray(new ShardFacetCount[counts.size()]);
      Arrays.sort(arr, new Comparator<ShardFacetCount>() {
        public int compare(ShardFacetCount o1, ShardFacetCount o2) {
          if (o2.count < o1.count) return -1;
          else if (o1.count < o2.count) return 1;
          return o1.indexed.compareTo(o2.indexed);
        }
      });
      countSorted = arr;
      return arr;
    }
    
    // returns the max possible value this ShardFacetCount could have for this shard
    // (assumes the shard did not report a count for this value)
    long maxPossible(ShardFacetCount sfc, int shardNum) {
      return missingMax[shardNum];
      // TODO: could store the last term in the shard to tell if this term
      // comes before or after it. If it comes before, we could subtract 1
    }
  }
  
  /**
   * <b>This API is experimental and subject to change</b>
   */
  public static class ShardFacetCount {
    public String name;
    public String indexed;  // the indexed form of the name... used for comparisons.
    public long count;
    public int termNum; // term number starting at 0 (used in bit arrays)
    
    @Override
    public String toString() {
      return "{term=" + name + ",termNum=" + termNum + ",count=" + count + "}";
    }
  }
}
