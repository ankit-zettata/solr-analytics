package org.apache.solr.handler.component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.time.StopWatch;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.util.OpenBitSet;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.params.StatsParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.search.DocSet;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.QueryParsing;
import org.apache.solr.search.SolrIndexSearcher;

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

/**
 * Provides a means to query solr and return count sets based upon
 * a ratio query.
 * 
 * 1) given two domains [queries]
 * 2) calculate the stats for both around a specific field value (domain specific) [facet]
 * 3) based upon a statistical field (amt, qty, etc) [stats field]
 * 4) then return a count of the field values that are within the min/max range specified
 * 
 * 
 */
public class RatiosComponent extends SearchComponent {
  
  public static final String COMPONENT_NAME = "ratios";

  @Override
  public void prepare(ResponseBuilder rb) throws IOException {
    if (rb.req.getParams().getBool(RatiosParams.RATIOS,false)) {
      rb.setNeedDocSet(false);
      rb.setNeedDocList(false);
      rb.doRatios = true;
    }
  }

  @Override
  public void process(ResponseBuilder rb) throws IOException {
    try {
      HashMap<String,Long> timers = new HashMap<String,Long>();
      
      if (rb.doRatios) {
        SolrParams params = rb.req.getParams();
        
        // in ratios the facet field is always the dimension field
        String dimension = params.get(RatiosParams.RATIOS_DIMENSION);
        String measure = params.get(RatiosParams.RATIOS_MEASURE);
        Double min = params.getDouble(RatiosParams.RATIOS_MIN, 0);
        Double max = params.getDouble(RatiosParams.RATIOS_MAX, 1);
        boolean debug = params.getBool(RatiosParams.RATIOS_DEBUG, false);
        boolean rows = params.getBool(RatiosParams.RATIOS_ROWS, false);
        
        HashMap<String,String[]> fieldFacets = new HashMap<String,String[]>();
        fieldFacets.put(measure, new String[] { dimension });
        
        SolrIndexSearcher searcher = rb.req.getSearcher();
        
        String defType = params.get(QueryParsing.DEFTYPE, QParserPlugin.DEFAULT_QTYPE);
        QParser q1 = QParser.getParser(params.get("q") + " AND (" + params.get(RatiosParams.RATIOS_Q1) + ")", defType, rb.req);
        QParser q2 = QParser.getParser(params.get("q") + " AND (" + params.get(RatiosParams.RATIOS_Q2) + ")", defType, rb.req);
        
        StopWatch stopwatch = new StopWatch();
        stopwatch.start();        
        
        DocSet set1 = searcher.getDocSet(q1.getQuery());
        stopwatch.stop();
        timers.put("q1.ms", stopwatch.getTime());
        stopwatch.reset();
        
        stopwatch.start();
        DocSet set2 = searcher.getDocSet(q2.getQuery());
        stopwatch.stop();
        timers.put("q2.ms", stopwatch.getTime());
        stopwatch.reset();
        
        // ====== stats for 1st
        stopwatch.start();
        ModifiableSolrParams xp = new ModifiableSolrParams();
        xp.add(StatsParams.STATS_FIELD, measure);
        xp.add(StatsParams.STATS_FACET, dimension);
        xp.add(ShardParams.IS_SHARD, String.valueOf(params.getBool(ShardParams.IS_SHARD, false)));
        SimpleStats stats1 = new SimpleStats(rb.req, set1, xp);
        
        // TODO implement according to SOLR standard
        NamedList<?> map1 = stats1.getFieldCacheStats(measure, new String[] { dimension });
        if (map1 == null || map1.size() <= 0) {
          // empty do nothing
          return;
        }
        Map<String,Double> matrix1 = new HashMap<String,Double>(); // TODO map1.get(dimension);
        stopwatch.stop();
        timers.put("q1.stats.ms", stopwatch.getTime());
        stopwatch.reset();
        
        // ====== stats for 2nd
        stopwatch.start();
        SimpleStats stats2 = new SimpleStats(rb.req, set2, xp);
        NamedList<?> map2 = stats2.getFieldCacheStats(measure, new String[] { dimension });
        if (map2 == null || map2.size() <= 0) {
          // empty do nothing
          return;
        }
        Map<String,Double> matrix2 = new HashMap<String,Double>(); // TODO map2.get(dimension);
        stopwatch.stop();
        timers.put("q2.stats.ms", stopwatch.getTime());
        stopwatch.reset();
        
        // ====== ratios
        stopwatch.start();
        OpenBitSet ratios = new OpenBitSet();// TODO filter(matrix1, matrix2, min, max);
        stopwatch.stop();
        timers.put("ratio.ms", stopwatch.getTime());
        stopwatch.reset();
        
        // ====== done do payload extraction
        NamedList<Object> payload = new NamedList<Object>();
        if (debug) {
          // timer information
          NamedList<Object> performance = new NamedList<Object>();
          for (String key : timers.keySet()) {
            performance.add(key, timers.get(key));
          }
          payload.add("debug", performance);
        }
        
        payload.add("count", ratios.cardinality());
        payload.add("union", set1.unionSize(set2));
        payload.add("intersection", set1.intersectionSize(set2));
        
        NamedList<Object> query1 = new NamedList<Object>();
        query1.add("rows", set1.size());
        query1.add("dimensions", matrix1.size());
        if (rows) {
          query1.add( "results", toNamedList(matrix1));
        }
        
        NamedList<Object> query2 = new NamedList<Object>();
        query2.add("rows", set2.size());
        query2.add("dimensions", matrix2.size());
        if (rows) {
          query2.add( "results", toNamedList(matrix2));
        }
        
        NamedList<Object> breakdown = new NamedList<Object>();
        breakdown.add("query1", query1);
        breakdown.add("query2", query2);
        
        payload.add("breakdown", breakdown);

        // TODO - output ratio bitset to hex for UX to do client side join
        // byte[] bytes = HexUtil.convertToGzipCompressedByte(ratios.getBits());
        // String x = javax.xml.bind.DatatypeConverter.printBase64Binary(bytes);
        // payload.add("base64", x);
        
        rb.rsp.add( RatiosParams.RATIOS, payload );
      }
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }
  
  private NamedList<Double> toNamedList(Map<String, Double> x) {
    NamedList<Double> r = new NamedList<Double>();
    Set<Entry<String,Double>> set = x.entrySet();
    for (Entry<String,Double> entry : set) {
      r.add(entry.getKey(), entry.getValue());
    }
    return r;
  }
  
  private OpenBitSet filter(Map<String,Double> dimensions1, Map<String,Double> dimensions2, double min, double max) {
    
    OpenBitSet ratios = new OpenBitSet();
    
    for (String id : dimensions1.keySet()) {
      Double a = dimensions1.get(id);
      Double b = dimensions2.get(id);
      if (a == null || a.isInfinite() || a.isNaN()) {
        continue;
      }
      if (b == null || b.isInfinite() || b.isNaN() || b == 0) {
        continue;
      }
      Double r = a / b;
      if (r < min || r > max) {
        continue;
      }
      
      ratios.set(Long.parseLong(id));
    }
    
    return ratios;
  }
  
  @Override
  public int distributedProcess(ResponseBuilder rb) throws IOException {
    return ResponseBuilder.STAGE_DONE;
  }
  
  @Override
  public void modifyRequest(ResponseBuilder rb, SearchComponent who, ShardRequest sreq) {
    if (!rb.doRatios) return;

    if ((sreq.purpose & ShardRequest.PURPOSE_GET_TOP_IDS) != 0) {
        sreq.purpose |= ShardRequest.PURPOSE_GET_RATIOS;

        StatsInfo si = rb._statsInfo;
        if (si == null) {
          rb._statsInfo = si = new StatsInfo();
          si.parse(rb.req.getParams(), rb);
        }
    } else {
      sreq.params.set(RatiosParams.RATIOS, "false");
    }
  }
  
  @Override
  public void handleResponses(ResponseBuilder rb, ShardRequest sreq) {
    if (!rb.doRatios || (sreq.purpose & ShardRequest.PURPOSE_GET_RATIOS) == 0) return;

    StatsInfo si = rb._statsInfo;

    for (ShardResponse srsp : sreq.responses) {
      NamedList<?> stats = (NamedList<?>) srsp.getSolrResponse().getResponse().get(RatiosParams.RATIOS);

      NamedList<?> stats_fields = (NamedList<?>) stats.get("stats_fields");
      if (stats_fields != null) {
        for (int i = 0; i < stats_fields.size(); i++) {
          String field = stats_fields.getName(i);
          StatsValues stv = si.statsFields.get(field);
          NamedList<?> shardStv = (NamedList<?>) stats_fields.get(field);
          stv.accumulate(shardStv);
        }
      }
    }
  }
  
  @Override
  public void finishStage(ResponseBuilder rb) {
    if (!rb.doRatios || rb.stage != ResponseBuilder.STAGE_GET_FIELDS) return;
    // wait until STAGE_GET_FIELDS
    // so that "result" is already stored in the response (for aesthetics)

    StatsInfo si = rb._statsInfo;

    NamedList<NamedList<Object>> stats = new SimpleOrderedMap<NamedList<Object>>();
    NamedList<Object> stats_fields = new SimpleOrderedMap<Object>();
    stats.add("stats_fields", stats_fields);
    for (String field : si.statsFields.keySet()) {
      NamedList<?> stv = si.statsFields.get(field).getStatsValues();
      if ((Long) stv.get("count") != 0) {
        stats_fields.add(field, stv);
      } else {
        stats_fields.add(field, null);
      }
    }

    rb.rsp.add(RatiosParams.RATIOS, stats);

    rb._statsInfo = null;
  }

  @Override
  public String getDescription() {
    return "Ratios Component";
  }

  @Override
  public String getSource() {
    return "$URL: http://svn.apache.org/repos/asf/lucene/dev/branches/branch_4x/solr/core/src/java/org/apache/solr/handler/component/RatiosComponent.java $";
  }
  
}