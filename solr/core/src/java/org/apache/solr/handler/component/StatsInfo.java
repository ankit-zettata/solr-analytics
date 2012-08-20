package org.apache.solr.handler.component;

import java.util.HashMap;
import java.util.Map;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.params.StatsParams;
import org.apache.solr.schema.SchemaField;

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

public class StatsInfo {
  Map<String, StatsValues> statsFields;

  void parse(SolrParams params, ResponseBuilder rb) {
    statsFields = new HashMap<String, StatsValues>();

    String[] statsFs = params.getParams(StatsParams.STATS_FIELD);
    boolean minimal = params.getBool("stats.minimal", false);
    if (statsFs != null) {
      for (String field : statsFs) {
        SchemaField sf = rb.req.getSchema().getField(field);
        statsFields.put(field, StatsValuesFactory.createStatsValues(sf, minimal));
      }
    }
  }
}
