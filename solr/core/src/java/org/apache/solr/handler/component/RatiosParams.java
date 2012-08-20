package org.apache.solr.handler.component;

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

public class RatiosParams {
  public static final String RATIOS = "ratios";  // turn on ratios component
  
  public static final String RATIOS_Q1 = RATIOS + ".q1"; // the numerator set
  public static final String RATIOS_Q2 = RATIOS + ".q2"; // the denominator set
  public static final String RATIOS_MEASURE = RATIOS + ".measure";  // the field to ratio on
  public static final String RATIOS_DIMENSION = RATIOS + ".dimension";  // the field to ratio on
  public static final String RATIOS_MIN = RATIOS + ".min";  // min values for successful match
  public static final String RATIOS_MAX = RATIOS + ".max";  // max values for successful match
  public static final String RATIOS_DEBUG = RATIOS + ".debug";  // debug output
  public static final String RATIOS_ROWS = RATIOS + ".rows";  // should output rows
}
