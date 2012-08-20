package org.apache.solr.util;

import org.apache.solr.common.util.NamedList;

public class NamedListHelper {
  
  public static NamedListHelper INSTANCE = new NamedListHelper();
  
  private NamedListHelper() {
    // Use the singleton
  }
  
  public Object getFromPivotList(PivotListEntry entryToGet, NamedList<?> pivotList) {
    Object entry = pivotList.get(entryToGet.getName(), entryToGet.getIndex());
    if (entry == null) {
      entry = pivotList.get(entryToGet.getName());
    }
    return entry;
  }

}