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

package org.apache.solr.core;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

/**
 * Factory to instantiate {@link org.apache.lucene.store.RAMDirectory}
 */
public class RAMDirectoryFactory extends StandardDirectoryFactory {

  @Override
  protected Directory create(String path) throws IOException {
    return new RAMDirectory();
  }
  
  @Override
  public boolean exists(String path) {
    String fullPath = new File(path).getAbsolutePath();
    synchronized (this) {
      CacheValue cacheValue = byPathCache.get(fullPath);
      Directory directory = null;
      if (cacheValue != null) {
        directory = cacheValue.directory;
      }
      if (directory == null) {
        return false;
      } else {
        return true;
      }
    }
  }

}
