package org.apache.solr.handler.component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.BitSet;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class HexUtil {
  
  public static Logger log = LoggerFactory.getLogger(HexUtil.class);
  
  private HexUtil() {   
  } 
  
  public static byte[] convertToGzipCompressedByte(long[] longArray) throws IOException{
    GZIPOutputStream zip = null;
    ByteArrayOutputStream out = null;
    DataOutputStream data = null;
    try {
      out = new ByteArrayOutputStream();
      zip = new GZIPOutputStream(out);
      data = new DataOutputStream(zip);

      for(int i = 0; i < longArray.length; i++){
        data.writeLong(longArray[i]);
      }

      return out.toByteArray();
    } finally {
      IOUtils.closeQuietly(zip);
      IOUtils.closeQuietly(data);
      IOUtils.closeQuietly(out);
    }
  }
  
  public static byte[] convertToByte(long[] longArray) throws IOException{
    ByteArrayOutputStream out = null;
    DataOutputStream data = null;
    try {
      out = new ByteArrayOutputStream();
      data = new DataOutputStream(out);

      for(int i = 0; i < longArray.length; i++){
        data.writeLong(longArray[i]);
      }

      return out.toByteArray();
    } finally {
      IOUtils.closeQuietly(data);
      IOUtils.closeQuietly(out);
    }
  }

  public static long[] convertToLong(byte[] byteArray) throws IOException {
    ByteArrayInputStream in = null;
    DataInputStream data = null;
    try {
      in = new ByteArrayInputStream(byteArray);
      data = new DataInputStream(in);
  
      int length = byteArray.length / 8;
      long[] result = new long[length];
      for(int i = 0; i < length; i++){
        result[i] = data.readLong();
      }
  
      return result;
    } finally {
      IOUtils.closeQuietly(data);
      IOUtils.closeQuietly(in);
    }
  }
}
